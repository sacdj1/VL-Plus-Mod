/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Unlike the other Vanilla HUD elements, this one draws itself - vanilla draws the air bubbles
 * inline inside renderStatusBars with no separate callable method to redirect (unlike
 * armor/health/food, each their own method), so there's no clean way to transform vanilla's own
 * draw call. Reimplemented instead, following vanilla's exact bubble-count/full-vs-bursting
 * formula (InGameHudMixin hides vanilla's own copy by translating it off-screen while this
 * element is present, rather than drawing both).
 */
public class VanillaAirHud extends HudElement {
    public static final HudElementInfo<VanillaAirHud> INFO = new HudElementInfo<>(Hud.VANILLA_GROUP, "vanilla-air", "Moves and scales the real air/oxygen bubble row - reported not working correctly, under investigation.", VanillaAirHud::new);

    private static final Identifier AIR_TEXTURE = Identifier.of("minecraft", "textures/gui/sprites/hud/air.png");
    private static final Identifier AIR_BURSTING_TEXTURE = Identifier.of("minecraft", "textures/gui/sprites/hud/air_bursting.png");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Size multiplier over vanilla's normal size.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 20)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Integer> spacing = sgGeneral.add(new IntSetting.Builder()
        .name("spacing")
        .description("Distance between each bubble's own center, before Scale is applied (8 is vanilla's own spacing, which slightly overlaps each 9px bubble sprite with the next).")
        .defaultValue(8)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Integer> rotation = sgGeneral.add(new IntSetting.Builder()
        .name("rotation")
        .description("Rotates the bubble row's layout around its own center, in degrees clockwise. Unlike the other Vanilla elements (which redirect vanilla's own matrix-transformed draw calls), this element draws itself - so only each bubble's position rotates around the center, not the individual bubble icons themselves.")
        .defaultValue(0)
        .range(0, 359)
        .sliderRange(0, 359)
        .build()
    );

    public VanillaAirHud() {
        super(INFO);
        calculateSize();
    }

    private void calculateSize() {
        setSize((9 * spacing.get() + 9) * scale.get(), 9 * scale.get());
    }

    public double getScale() {
        return scale.get();
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null) {
            if (isInEditor()) renderer.quad(x, y, getWidth(), getHeight(), new Color(255, 255, 255, 40));
            return;
        }

        int maxAir = mc.player.getMaxAir();
        int air = Math.min(mc.player.getAir(), maxAir);
        boolean visible = mc.player.isSubmergedIn(FluidTags.WATER) || air < maxAir;

        if (!visible) {
            if (isInEditor()) renderer.quad(x, y, getWidth(), getHeight(), new Color(255, 255, 255, 40));
            return;
        }

        double s = scale.get();
        int full = MathHelper.ceil((air - 2) * 10.0 / maxAir);
        int total = MathHelper.ceil(air * 10.0 / maxAir);

        double angleRad = Math.toRadians(rotation.get());
        double cos = Math.cos(angleRad), sin = Math.sin(angleRad);
        double pivotX = x + getWidth() / 2.0;
        double pivotY = y + getHeight() / 2.0;
        double size = 9 * s;

        // Vanilla builds this row right-to-left from the screen's right edge (full bubbles
        // closest to the edge, bursting ones further away) - x/y here is this element's own
        // top-left instead, so the loop is mirrored to land the same bubbles in the same
        // left-to-right reading order, just anchored the other way.
        for (int i = 0; i < total; i++) {
            boolean bursting = i >= full;
            Identifier texture = bursting ? AIR_BURSTING_TEXTURE : AIR_TEXTURE;
            double bx = x + (total - 1 - i) * spacing.get() * s;

            double centerX = bx + size / 2.0;
            double centerY = y + size / 2.0;
            double dx = centerX - pivotX;
            double dy = centerY - pivotY;
            double rx = pivotX + dx * cos - dy * sin;
            double ry = pivotY + dx * sin + dy * cos;

            renderer.texture(texture, rx - size / 2.0, ry - size / 2.0, size, size, Color.WHITE);
        }
    }
}
