/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * Doesn't draw anything itself - see VanillaHotbarHud. InGameHudMixin redirects vanilla's own
 * title/subtitle text to this element's position/scale. Unlike the other Vanilla elements, this
 * one is CENTER-anchored (the element's own box center, not its top-left) rather than top-left -
 * vanilla itself always draws titles centered on a point (title/subtitle width varies per
 * message, so there's no fixed top-left corner to anchor on the way there is for e.g. the hotbar),
 * so the box here just marks roughly where that center point will be.
 */
public class VanillaTitleHud extends HudElement {
    public static final HudElementInfo<VanillaTitleHud> INFO = new HudElementInfo<>(Hud.VANILLA_GROUP, "vanilla-title", "Moves and scales the real title/subtitle text (centered on this box, since title text width varies).", VanillaTitleHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> hide = sgGeneral.add(new BoolSetting.Builder()
        .name("hide")
        .description("Hides the real vanilla element entirely instead of repositioning it.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Size multiplier over vanilla's normal size.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 20)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Integer> rotation = sgGeneral.add(new IntSetting.Builder()
        .name("rotation")
        .description("Rotates the whole element around its own center, in degrees clockwise.")
        .defaultValue(0)
        .range(0, 359)
        .sliderRange(0, 359)
        .build()
    );

    public VanillaTitleHud() {
        super(INFO);
        calculateSize();
    }

    private void calculateSize() {
        setSize(200 * scale.get(), 60 * scale.get());
    }

    public double getScale() {
        return scale.get();
    }

    public int getRotation() {
        return rotation.get();
    }

    @Override
    public int getEditorRotation() {
        return rotation.get();
    }

    public boolean isHidden() {
        return hide.get();
    }

    @Override
    public void render(HudRenderer renderer) {
        if (!isInEditor()) return;

        renderer.quad(x, y, getWidth(), getHeight(), new Color(255, 255, 255, 40));

        // 4x is vanilla's own title text scale - Scale above is a multiplier over that, same as
        // everywhere else in this element, so the preview lines up with how big the real title
        // will actually be.
        double textScale = scale.get() * 4;
        String example = "Example Title";
        double centerX = x + getWidth() / 2.0;
        double centerY = y + getHeight() / 2.0;

        renderer.text(example, centerX - renderer.textWidth(example, true, textScale) / 2.0, centerY - renderer.textHeight(true, textScale) / 2.0, Color.WHITE, true, textScale);
    }
}
