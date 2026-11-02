/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.widgets.containers;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.util.math.MathHelper;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public abstract class WView extends WVerticalList {
    public double maxHeight = Double.MAX_VALUE;
    public boolean scrollOnlyWhenMouseOver = true;
    public boolean hasScrollBar = true;

    protected boolean canScroll;
    private double actualHeight;

    private double scroll;
    private double targetScroll;
    private boolean moveAfterPositionWidgets;

    protected boolean handleMouseOver;
    protected boolean handlePressed;

    @Override
    public void init() {
        maxHeight = Utils.getWindowHeight() - theme.scale(128);
    }

    @Override
    protected void onCalculateSize() {
        boolean couldScroll = canScroll;
        canScroll = false;
        widthRemove = 0;

        super.onCalculateSize();

        // Only clamp/scroll at all when hasScrollBar is actually true - a view with no scrollbar
        // (e.g. ModulesScreen's own category dropdowns) is meant to show its FULL content, not get
        // silently height-capped with no way to interact with the resulting scroll state. Without
        // this gate, height still got clamped and canScroll still went true purely from content
        // exceeding maxHeight, with only the handle's own width/draw skipped - leaving a real
        // (if stuck/inaccessible) scroll offset that moveAfterPositionWidgets below could still
        // reapply via moveCells(), visibly shifting the whole body away from its own header.
        if (hasScrollBar && height > maxHeight) {
            actualHeight = height;
            height = maxHeight;
            canScroll = true;

            widthRemove = handleWidth() * 2;
            width += widthRemove;

            if (couldScroll) moveAfterPositionWidgets = true;
        }
        else {
            actualHeight = height;
            scroll = 0;
            targetScroll = 0;
        }
    }

    @Override
    protected void onCalculateWidgetPositions() {
        super.onCalculateWidgetPositions();

        if (moveAfterPositionWidgets) {
            scroll = MathHelper.clamp(scroll, 0, actualHeight - height);
            targetScroll = scroll;

            moveCells(0, -scroll);

            moveAfterPositionWidgets = false;
        }
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
        if (handleMouseOver && button == GLFW_MOUSE_BUTTON_LEFT && !used) {
            handlePressed = true;
            return true;
        }

        return false;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (handlePressed) handlePressed = false;

        return false;
    }

    @Override
    public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
        handleMouseOver = false;

        if (canScroll && hasScrollBar) {
            double x = handleX();
            double y = handleY();

            if (mouseX >= x && mouseX <= x + handleWidth() && mouseY >= y && mouseY <= y + handleHeight()) {
                handleMouseOver = true;
            }
        }

        if (handlePressed) {
            double preScroll = scroll;
            double mouseDelta = mouseY - lastMouseY;

            //scroll += Math.round(theme.scale(mouseDelta + mouseDelta * ((height / actualHeight) * 0.7627725)));
            //scroll += Math.round(theme.scale(mouseDelta * (1 / (height / actualHeight))));
            scroll += Math.round(mouseDelta * ((actualHeight - handleHeight() / 2) / height)); // TODO: Someone improve this
            scroll = MathHelper.clamp(scroll, 0, actualHeight - height);

            targetScroll = scroll;

            double delta = scroll - preScroll;
            if (delta != 0) moveCells(0, -delta);
        }
    }

    @Override
    public boolean onMouseScrolled(double amount) {
        if (!scrollOnlyWhenMouseOver || mouseOver) {
            targetScroll -= Math.round(theme.scale(amount * 40));
            targetScroll = MathHelper.clamp(targetScroll, 0, actualHeight - height);
            return true;
        }

        return false;
    }

    @Override
    public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        updateScroll(delta);

        // Always scissored to this view's own viewport now, not just while it can scroll - same bug,
        // same fix as WWindow/WSection: a view whose content happens to fit (canScroll false) used to
        // skip scissorStart/scissorEnd entirely, which also skips GuiRenderer's flush pair, letting
        // its queued text pile into whatever LATER view/window happens to force a flush instead of
        // its own. Harmless to always scissor to (x, y, width, height) - that's exactly this view's
        // own bounds either way.
        // try/finally so scissorEnd() always runs even if a child widget's render() throws - see
        // WWindow.render()'s own note; the same unguarded pairing here caused the identical failure.
        renderer.scissorStart(x, y, width, height);
        try {
            return super.render(renderer, mouseX, mouseY, delta);
        } finally {
            renderer.scissorEnd();
        }
    }

    private void updateScroll(double delta) {
        double preScroll = scroll;
        double max = actualHeight - height;

        if (Math.abs(targetScroll - scroll) < 1) scroll = targetScroll;
        else if (targetScroll > scroll) {
            scroll += Math.round(theme.scale(delta * 300 + delta * 100 * (Math.abs(targetScroll - scroll) / 10)));
            if (scroll > targetScroll) scroll = targetScroll;
        }
        else if (targetScroll < scroll) {
            scroll -= Math.round(theme.scale(delta * 300 + delta * 100 * (Math.abs(targetScroll - scroll) / 10)));
            if (scroll < targetScroll) scroll = targetScroll;
        }

        scroll = MathHelper.clamp(scroll, 0, max);

        double change = scroll - preScroll;
        if (change != 0) moveCells(0, -change);
    }

    @Override
    protected boolean propagateEvents(WWidget widget) {
        return ((widget.y >= y && widget.y <= y + height) || (widget.y + widget.height >= y && widget.y + widget.height <= y + height)) || ((y >= widget.y && y <= widget.y + widget.height) || (y + height >= widget.y && y + height <= widget.y + widget.height));
    }

    protected double handleWidth() {
        return theme.scale(6);
    }

    protected double handleHeight() {
        return height / actualHeight * height;
    }

    protected double handleX() {
        return x + width - handleWidth();
    }

    protected double handleY() {
        return y + (height - handleHeight()) * (scroll / (actualHeight - height));
    }
}
