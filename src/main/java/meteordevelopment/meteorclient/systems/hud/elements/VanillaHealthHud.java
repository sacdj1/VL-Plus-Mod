/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.DoubleSetting;
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

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Size multiplier over vanilla's normal size.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 5)
        .onChanged(v -> calculateSize())
        .build()
    );

    public VanillaHealthHud() {
        super(INFO);
        calculateSize();
    }

    private void calculateSize() {
        setSize(80 * scale.get(), 9 * scale.get());
    }

    public double getScale() {
        return scale.get();
    }

    @Override
    public void render(HudRenderer renderer) {
        if (isInEditor()) renderer.quad(x, y, getWidth(), getHeight(), new Color(255, 255, 255, 40));
    }
}
