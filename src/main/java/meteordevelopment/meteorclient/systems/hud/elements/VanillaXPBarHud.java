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
 * Doesn't draw anything itself - see VanillaHotbarHud. InGameHudMixin redirects vanilla's own XP
 * bar (background/track and fill/progress both, plus XPBarAdjust's recolor overlays on top of it,
 * if that module is also active) to this element's position/scale.
 */
public class VanillaXPBarHud extends HudElement {
    public static final HudElementInfo<VanillaXPBarHud> INFO = new HudElementInfo<>(Hud.VANILLA_GROUP, "vanilla-xp-bar", "Moves and scales the real XP bar.", VanillaXPBarHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> hide = sgGeneral.add(new BoolSetting.Builder()
        .name("hide")
        .description("Hides the real vanilla element entirely instead of repositioning it.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Overall size multiplier, multiplied on top of Scale X/Scale Y - scales both dimensions together.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 20)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Double> scaleX = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale-x")
        .description("Horizontal scale, independent of Scale Y - stretches/squashes the bar without affecting its height.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 20)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Double> scaleY = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale-y")
        .description("Vertical scale, independent of Scale X - stretches/squashes the bar without affecting its width.")
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

    public VanillaXPBarHud() {
        super(INFO);
        calculateSize();
    }

    private void calculateSize() {
        setSize(183 * scaleX.get() * scale.get(), 5 * scaleY.get() * scale.get());
    }

    public double getScale() {
        return scale.get();
    }

    public double getScaleX() {
        return scaleX.get() * scale.get();
    }

    public double getScaleY() {
        return scaleY.get() * scale.get();
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
        if (isInEditor()) renderer.quad(x, y, getWidth(), getHeight(), new Color(255, 255, 255, 40));
    }
}
