/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.widgets.containers;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.utils.WindowConfig;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WTriangle;
import net.minecraft.util.math.MathHelper;

import java.util.function.Consumer;

import static meteordevelopment.meteorclient.utils.Utils.getWindowHeight;
import static meteordevelopment.meteorclient.utils.Utils.getWindowWidth;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

public abstract class WWindow extends WVerticalList {
    public double padding = 8;
    public Consumer<WContainer> beforeHeaderInit;
    public String id;

    public final WWidget icon;
    protected final String title;

    protected WHeader header;
    public WView view;

    protected boolean dragging;
    protected boolean expanded = true;
    protected boolean dragged;

    protected double animProgress = 1;

    protected boolean moved = false;
    protected double movedX, movedY;

    private boolean propagateEventsExpanded;

    public WWindow(WWidget icon, String title) {
        this.icon = icon;
        this.title = title;
    }

    @Override
    public void init() {
        header = header(icon);
        header.theme = theme;
        super.add(header).expandWidgetX().widget();

        view = super.add(theme.view()).expandX().pad(padding).widget();

        if (id != null) {
            expanded = theme.getWindowConfig(id).expanded;
            animProgress = expanded ? 1 : 0;
        }
    }

    protected abstract WHeader header(WWidget icon);

    @Override
    public <T extends WWidget> Cell<T> add(T widget) {
        return view.add(widget);
    }

    @Override
    public void clear() {
        view.clear();
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;

        if (id != null) {
            WindowConfig config = theme.getWindowConfig(id);
            config.expanded = expanded;
        }
    }

    @Override
    protected void onCalculateWidgetPositions() {
        if (id != null) {
            WindowConfig config = theme.getWindowConfig(id);

            if (config.x != -1) {
                x = config.x;

                if (x + width > getWindowWidth()) {
                    x = getWindowWidth() - width;
                }
            }

            if (config.y != -1) {
                y = config.y;

                if (y + height > getWindowHeight()) {
                    y = getWindowHeight() - height;
                }
            }
        }

        super.onCalculateWidgetPositions();

        if (moved) {
            move(movedX - x, movedY - y);
        }
    }

    @Override
    public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (!visible) return true;

        // Always scissored to this window's own bounds now, not just while animating - a fully
        // expanded, non-animating window used to skip scissorStart/scissorEnd entirely, which also
        // skips GuiRenderer's endRender()/beginRender() flush pair. With several such windows in a
        // row (the normal case: most category windows sit still, not mid-animation, most of the
        // time), none of them ever got their own flush boundary, so their queued text operations
        // all piled into ONE shared, unflushed batch spanning every one of those windows, finally
        // drawn together whenever something ELSE (the next window that WAS mid-animation, or the
        // screen's own outer scissor) finally forced a flush - under THAT flush's transform/scissor
        // state, not each window's own. Scissoring every window to its own exact bounds every frame
        // (harmless when content already fits, which it always does here - height is computed FROM
        // the content) forces a flush right at the end of each window's own render, so its text
        // never has a chance to bleed into a later window's batch.
        // try/finally so scissorEnd() always runs even if a child widget's render() throws - without
        // it, one exception anywhere in this window's whole subtree left GuiRenderer's scissor stack
        // (and the shared Renderer2D meshes it begins/ends) permanently unbalanced, corrupting every
        // other window/section's rendering for the rest of the session (see Mesh.begin()'s own note).
        renderer.scissorStart(x, y, width, (height - header.height) * animProgress + header.height);
        try {
            return super.render(renderer, mouseX, mouseY, delta);
        } finally {
            renderer.scissorEnd();
        }
    }

    @Override
    protected void renderWidget(WWidget widget, GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (expanded || animProgress > 0 || widget instanceof WHeader) {
            widget.render(renderer, mouseX, mouseY, delta);
        }

        propagateEventsExpanded = expanded;
    }

    @Override
    protected boolean propagateEvents(WWidget widget) {
        return widget instanceof WHeader || propagateEventsExpanded;
    }

    protected abstract class WHeader extends WContainer {
        private final WWidget icon;
        private WTriangle triangle;
        private WHorizontalList list;

        public WHeader(WWidget icon) {
            this.icon = icon;
        }

        @Override
        public void init() {
            if (icon != null) {
                createList();
                add(icon).centerY();
            }

            if (beforeHeaderInit != null) {
                createList();
                beforeHeaderInit.accept(this);
            }

            add(theme.label(title, true)).expandCellX().center().pad(4);

            triangle = add(theme.triangle()).pad(4).right().centerY().widget();
            triangle.action = () -> setExpanded(!expanded);
        }

        private void createList() {
            list = add(theme.horizontalList()).expandX().widget();
            list.spacing = 0;
        }

        @Override
        public <T extends WWidget> Cell<T> add(T widget) {
            if (list != null) return list.add(widget);
            return super.add(widget);
        }

        @Override
        protected void onCalculateSize() {
            width = 0;
            height = 0;

            for (Cell<?> cell : cells) {
                double w = cell.padLeft() + cell.widget().width + cell.padRight();
                if (cell.widget() instanceof WTriangle) w *= 2;

                width += w;
                height = Math.max(height, cell.padTop() + cell.widget().height + cell.padBottom());
            }
        }

        @Override
        public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
            if (mouseOver && !used) {
                if (button == GLFW_MOUSE_BUTTON_RIGHT) setExpanded(!expanded);
                else {
                    dragging = true;
                    dragged = false;
                }

                return true;
            }

            return false;
        }

        @Override
        public boolean onMouseReleased(double mouseX, double mouseY, int button) {
            if (dragging) {
                dragging = false;

                if (!dragged) setExpanded(!expanded);
            }

            return false;
        }

        @Override
        public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
            if (dragging) {
                WWindow.this.move(mouseX - lastMouseX, mouseY - lastMouseY);

                moved = true;
                movedX = x;
                movedY = y;

                if (id != null) {
                    WindowConfig config = theme.getWindowConfig(id);

                    config.x = x;
                    config.y = y;
                }

                dragged = true;
            }
        }

        @Override
        public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            animProgress += (expanded ? 1 : -1) * delta * 14;
            animProgress = MathHelper.clamp(animProgress, 0, 1);

            triangle.rotation = (1 - animProgress) * -90;

            return super.render(renderer, mouseX, mouseY, delta);
        }
    }
}
