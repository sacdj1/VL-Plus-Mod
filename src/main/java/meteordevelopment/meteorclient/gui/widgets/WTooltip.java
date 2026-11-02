/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;

public abstract class WTooltip extends WContainer implements WRoot {
    private boolean valid;

    protected String text;

    public WTooltip(String text) {
        this.text = text;
    }

    // Plain theme.label(text) (no maxWidth) never wraps - it just measures the WHOLE string as
    // one line, however long. That was fine for a handful of short descriptions, but this
    // codebase has long since grown many multi-sentence ones (250+ characters), which rendered as
    // a single line many screens wide - mostly off-screen past the mouse position this tooltip is
    // anchored to, reading as text that's missing or in the wrong place depending on where exactly
    // the cursor was. Capping width here wraps it across multiple lines instead, like any normal
    // tooltip.
    private static final double MAX_WIDTH = 250;

    @Override
    public void init() {
        add(theme.label(text, false, MAX_WIDTH)).pad(4);
    }

    @Override
    public void invalidate() {
        valid = false;
    }

    @Override
    public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (!valid) {
            calculateSize();
            calculateWidgetPositions();

            valid = true;
        }

        return super.render(renderer, mouseX, mouseY, delta);
    }
}
