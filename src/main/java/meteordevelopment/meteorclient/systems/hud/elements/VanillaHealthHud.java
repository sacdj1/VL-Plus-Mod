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
 * health/hearts row (including absorption, regen pop animation, hardcore/poison/wither icons -
 * all of vanilla's own logic, untouched) to this element's position/scale. Extra rows for high
 * max health stack upward from here, same as vanilla.
 */
public class VanillaHealthHud extends HudElement {
    public static final HudElementInfo<VanillaHealthHud> INFO = new HudElementInfo<>(Hud.VANILLA_GROUP, "vanilla-health", "Moves and scales the real health/hearts row - reported not working correctly, under investigation.", VanillaHealthHud::new);

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

    private final Setting<Integer> spacing = sgGeneral.add(new IntSetting.Builder()
        .name("spacing")
        .description("Distance between each heart's own center, before Scale is applied (8 is vanilla's own spacing, which slightly overlaps each 9px heart sprite with the next).")
        .defaultValue(8)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Boolean> keepSpritesUpright = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-sprites-upright")
        .description("Counter-rotates each individual icon so they stay upright even while this element itself is rotated - only the row layout rotates, not the icons themselves.")
        .defaultValue(true)
        .visible(() -> rotation.get() != 0)
        .build()
    );

    public VanillaHealthHud() {
        super(INFO);
        calculateSize();
    }

    private void calculateSize() {
        setSize((9 * spacing.get() + 9) * scale.get(), 9 * scale.get());
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

    public boolean keepSpritesUpright() {
        return keepSpritesUpright.get();
    }

    public int getSpacing() {
        return spacing.get();
    }

    @Override
    public void render(HudRenderer renderer) {
        if (isInEditor()) renderer.quad(x, y, getWidth(), getHeight(), new Color(255, 255, 255, 40));
    }
}
