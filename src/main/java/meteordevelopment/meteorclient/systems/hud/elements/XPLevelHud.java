/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class XPLevelHud extends HudElement {
    public static final HudElementInfo<XPLevelHud> INFO = new HudElementInfo<>(Hud.GROUP, "xp-level", "Displays your XP level, with customizable coloring.", XPLevelHud::new);

    public enum ColorMode {
        Static,
        Rainbow,
        Gradient,
        Flashing,
        HueShift
    }

    public enum RainbowTransition {
        Soft,
        Hard
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRainbow = settings.createGroup("Rainbow");
    private final SettingGroup sgGradient = settings.createGroup("Gradient / Flashing");
    private final SettingGroup sgHueShift = settings.createGroup("Hue Shift");

    // General

    private final Setting<ColorMode> colorMode = sgGeneral.add(new EnumSetting.Builder<ColorMode>()
        .name("color-mode")
        .description("How the text is colored.")
        .defaultValue(ColorMode.Static)
        .build()
    );

    private final Setting<SettingColor> staticColor = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the text.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> colorMode.get() == ColorMode.Static)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text scale.")
        .defaultValue(2.0)
        .min(0.5)
        .sliderRange(0.5, 6)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Draws a drop shadow behind the text.")
        .defaultValue(true)
        .build()
    );

    // Rainbow

    private final Setting<RainbowTransition> rainbowTransition = sgRainbow.add(new EnumSetting.Builder<RainbowTransition>()
        .name("transition")
        .description("Soft smoothly cycles through every hue. Hard jumps between a fixed set of hues with no blending.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> colorMode.get() == ColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> rainbowSteps = sgRainbow.add(new IntSetting.Builder()
        .name("hard-steps")
        .description("How many distinct hues to jump between, in Hard mode.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 16)
        .visible(() -> colorMode.get() == ColorMode.Rainbow && rainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> rainbowSpeed = sgRainbow.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> colorMode.get() == ColorMode.Rainbow)
        .build()
    );

    // Gradient / Flashing

    private final Setting<List<SettingColor>> colors = sgGradient.add(new ColorListSetting.Builder()
        .name("colors")
        .description("The colors to cycle/flash between. Needs at least 2.")
        .defaultValue(List.of(new SettingColor(255, 0, 0), new SettingColor(0, 0, 255)))
        .visible(() -> colorMode.get() == ColorMode.Gradient || colorMode.get() == ColorMode.Flashing)
        .build()
    );

    private final Setting<Double> gradientSpeed = sgGradient.add(new DoubleSetting.Builder()
        .name("gradient-speed")
        .description("How fast the gradient cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> colorMode.get() == ColorMode.Gradient)
        .build()
    );

    private final Setting<Integer> flashTicks = sgGradient.add(new IntSetting.Builder()
        .name("flash-ticks")
        .description("How many game ticks to hold each color for, in Flashing mode.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .visible(() -> colorMode.get() == ColorMode.Flashing)
        .build()
    );

    // Hue Shift

    private final Setting<SettingColor> hueShiftBaseColor = sgHueShift.add(new ColorSetting.Builder()
        .name("base-color")
        .description("Starting color - its hue continuously rotates, keeping its saturation/brightness/alpha, unlike Rainbow which always uses full saturation/brightness regardless of the base color.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> colorMode.get() == ColorMode.HueShift)
        .build()
    );

    private final Setting<Double> hueShiftSpeed = sgHueShift.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the hue rotates.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> colorMode.get() == ColorMode.HueShift)
        .build()
    );

    public XPLevelHud() {
        super(INFO);

        calculateSize();
    }

    private void calculateSize() {
        setSize(30 * scale.get(), 9 * scale.get());
    }

    @Override
    public void render(HudRenderer renderer) {
        String text = String.valueOf(mc.player != null ? mc.player.experienceLevel : 0);
        Color color = getColor();

        renderer.text(text, x, y, color, shadow.get(), scale.get());
    }

    private Color getColor() {
        return switch (colorMode.get()) {
            case Static -> staticColor.get();
            case Rainbow -> getRainbowColor();
            case Gradient -> getGradientColor();
            case Flashing -> getFlashColor();
            case HueShift -> getHueShiftColor();
        };
    }

    private Color getHueShiftColor() {
        SettingColor base = hueShiftBaseColor.get();
        float[] hsb = java.awt.Color.RGBtoHSB(base.r, base.g, base.b, null);

        double time = System.currentTimeMillis() / 1000.0 * hueShiftSpeed.get() * 60.0;
        float hue = (float) ((hsb[0] * 360.0 + time) % 360.0);

        Color color = Color.fromHsv(hue, hsb[1], hsb[2]);
        color.a = base.a;
        return color;
    }

    private Color getRainbowColor() {
        double time = System.currentTimeMillis() / 1000.0 * rainbowSpeed.get() * 60.0;

        float hue;
        if (rainbowTransition.get() == RainbowTransition.Soft) {
            hue = (float) (time % 360.0);
        } else {
            int steps = rainbowSteps.get();
            int step = (int) (time / (360.0 / steps)) % steps;
            hue = step * (360f / steps);
        }

        return Color.fromHsv(hue, 1, 1);
    }

    private Color getGradientColor() {
        List<SettingColor> list = colors.get();
        if (list.isEmpty()) return Color.WHITE;
        if (list.size() == 1) return list.get(0);

        double time = (System.currentTimeMillis() / 1000.0 * gradientSpeed.get()) % list.size();
        int index = (int) time;
        float t = (float) (time - index);

        SettingColor from = list.get(index);
        SettingColor to = list.get((index + 1) % list.size());

        return new Color(
            (int) (from.r + (to.r - from.r) * t),
            (int) (from.g + (to.g - from.g) * t),
            (int) (from.b + (to.b - from.b) * t),
            (int) (from.a + (to.a - from.a) * t)
        );
    }

    private Color getFlashColor() {
        List<SettingColor> list = colors.get();
        if (list.isEmpty()) return Color.WHITE;
        if (list.size() == 1) return list.get(0);

        long tick = System.currentTimeMillis() / 50; // ~1 game tick, assuming a stable 20 TPS
        int index = (int) ((tick / flashTicks.get()) % list.size());

        return list.get(index);
    }
}
