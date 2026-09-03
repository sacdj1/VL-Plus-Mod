/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.List;

/**
 * Recolors the vanilla XP bar itself - useful on servers that repurpose it to show an ability
 * cooldown instead of real XP. Follows the same convention as the Ability Cooldown HUD element:
 * experienceProgress 0 = empty/ready, 1 = full/cooldown just applied.
 *
 * The background/track is the always-full-width part of the bar - it never changes size, so it's
 * given a fixed color rather than one driven by progress. The fill/progress part is the piece that
 * actually grows and shrinks with the cooldown, so that's where the ready/not-ready color logic
 * (below) applies - it's the part that visually communicates the cooldown state.
 */
public class XPBarAdjust extends Module {
    public enum ReadyColorMode {
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
    private final SettingGroup sgReady = settings.createGroup("Ready Color");
    private final SettingGroup sgRainbow = settings.createGroup("Ready Color (Rainbow)");
    private final SettingGroup sgGradient = settings.createGroup("Ready Color (Gradient)");
    private final SettingGroup sgHueShift = settings.createGroup("Ready Color (Hue Shift)");

    // General

    private final Setting<Boolean> recolorBackground = sgGeneral.add(new BoolSetting.Builder()
        .name("recolor-background")
        .description("Recolors the empty/background part of the bar (the always-full-width track behind the fill) using Background Color below - it stays fixed, it doesn't change with progress like the fill does.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Fixed tint for the background/track. Its alpha controls how strongly it blends with the bar's normal look - separate from Overall Alpha below, which fades the whole recolor.")
        .defaultValue(new SettingColor(255, 40, 40, 120))
        .visible(recolorBackground::get)
        .build()
    );

    private final Setting<Boolean> recolorFill = sgGeneral.add(new BoolSetting.Builder()
        .name("recolor-fill")
        .description("Recolors the filled/progress part of the bar - the part that actually grows/shrinks with the cooldown.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> emptinessGradient = sgGeneral.add(new BoolSetting.Builder()
        .name("emptiness-gradient")
        .description("Smoothly blends from the not-ready color toward the ready color as the bar drains. When off, the bar just shows the not-ready color flat until it's fully empty, then switches straight to the ready color - no in-between.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> notReadyColor = sgGeneral.add(new ColorSetting.Builder()
        .name("not-ready-color")
        .description("Color while the cooldown is active (bar not empty). Its alpha controls how strongly it blends with the bar's normal look - separate from Overall Alpha below, which fades the whole recolor.")
        .defaultValue(new SettingColor(255, 40, 40))
        .build()
    );

    private final Setting<Integer> overallAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("overall-alpha")
        .description("Final opacity of the whole recolor overlay (background and fill both), on top of each color's own alpha above - use that to control how strongly a given color blends in, and this to fade the whole effect uniformly.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    // Ready Color

    private final Setting<ReadyColorMode> readyColorMode = sgReady.add(new EnumSetting.Builder<ReadyColorMode>()
        .name("mode")
        .description("How the ready color is picked, once the bar reaches empty.")
        .defaultValue(ReadyColorMode.Static)
        .build()
    );

    private final Setting<SettingColor> readyStaticColor = sgReady.add(new ColorSetting.Builder()
        .name("color")
        .description("Color when ready.")
        .defaultValue(new SettingColor(40, 255, 40))
        .visible(() -> readyColorMode.get() == ReadyColorMode.Static)
        .build()
    );

    // Ready Color (Rainbow)

    private final Setting<RainbowTransition> rainbowTransition = sgRainbow.add(new EnumSetting.Builder<RainbowTransition>()
        .name("transition")
        .description("Soft smoothly cycles through every hue. Hard jumps between a fixed set of hues with no blending.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> readyColorMode.get() == ReadyColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> rainbowSteps = sgRainbow.add(new IntSetting.Builder()
        .name("hard-steps")
        .description("How many distinct hues to jump between, in Hard mode.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 16)
        .visible(() -> readyColorMode.get() == ReadyColorMode.Rainbow && rainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> rainbowSpeed = sgRainbow.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> readyColorMode.get() == ReadyColorMode.Rainbow)
        .build()
    );

    // Ready Color (Gradient / Flashing)

    private final Setting<List<SettingColor>> gradientColors = sgGradient.add(new ColorListSetting.Builder()
        .name("colors")
        .description("The colors to cycle between. Needs at least 2.")
        .defaultValue(List.of(new SettingColor(40, 255, 40), new SettingColor(40, 200, 255)))
        .visible(() -> readyColorMode.get() == ReadyColorMode.Gradient)
        .build()
    );

    private final Setting<Double> gradientSpeed = sgGradient.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the gradient cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> readyColorMode.get() == ReadyColorMode.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> flashColors = sgGradient.add(new TimedColorListSetting.Builder()
        .name("flash-colors")
        .description("The colors to hard-switch between, each with its own hold duration in game ticks. Needs at least 2.")
        .defaultValue(List.of(new TimedColorEntry(new SettingColor(40, 255, 40), 10), new TimedColorEntry(new SettingColor(40, 200, 255), 10)))
        .visible(() -> readyColorMode.get() == ReadyColorMode.Flashing)
        .build()
    );

    // Ready Color (Hue Shift)

    private final Setting<SettingColor> hueShiftBaseColor = sgHueShift.add(new ColorSetting.Builder()
        .name("base-color")
        .description("Starting color - its hue continuously rotates, keeping its saturation/brightness/alpha, unlike Rainbow which always uses full saturation/brightness regardless of the base color.")
        .defaultValue(new SettingColor(40, 255, 40))
        .visible(() -> readyColorMode.get() == ReadyColorMode.HueShift)
        .build()
    );

    private final Setting<Double> hueShiftSpeed = sgHueShift.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the hue rotates.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> readyColorMode.get() == ReadyColorMode.HueShift)
        .build()
    );

    public XPBarAdjust() {
        super(Categories.Render, "xp-bar-adjust", "Recolors the vanilla XP bar - useful on servers that repurpose it for an ability cooldown.");
    }

    public boolean shouldRecolorFill() {
        return recolorFill.get();
    }

    public boolean shouldRecolorBackground() {
        return recolorBackground.get();
    }

    /** Packed ARGB for the fixed background/track overlay, or 0 (fully transparent - draw nothing) if faded out entirely. */
    public int getBackgroundOverlayArgb() {
        return toOverlayArgb(backgroundColor.get());
    }

    /** Packed ARGB for the fill overlay at the given progress, or 0 (fully transparent - draw nothing) if faded out entirely. progress: 0 = empty/ready, 1 = full/just applied. */
    public int getFillOverlayArgb(float progress) {
        return toOverlayArgb(getColor(progress));
    }

    private int toOverlayArgb(Color color) {
        int a = Math.round((color.a / 255f) * (overallAlpha.get() / 255f) * 255f);
        if (a <= 0) return 0;

        return Color.fromRGBA(color.r, color.g, color.b, a);
    }

    /** progress: 0 = empty/ready, 1 = full/just applied - matches PlayerEntity.experienceProgress directly. */
    private Color getColor(float progress) {
        Color ready = getReadyColor();

        if (!emptinessGradient.get()) return progress <= 0.0001f ? ready : notReadyColor.get();

        float t = 1f - progress;
        return lerp(notReadyColor.get(), ready, t);
    }

    private Color getReadyColor() {
        return switch (readyColorMode.get()) {
            case Static -> readyStaticColor.get();
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

    private Color getFlashColor() {
        List<TimedColorEntry> entries = flashColors.get();
        if (entries.isEmpty()) return readyStaticColor.get();
        if (entries.size() == 1) return entries.get(0).color;

        long totalTicks = 0;
        for (TimedColorEntry entry : entries) totalTicks += Math.max(1, entry.ticks);

        long tick = System.currentTimeMillis() / 50; // ~1 game tick, assuming a stable 20 TPS
        long pos = tick % totalTicks;

        long accumulated = 0;
        for (TimedColorEntry entry : entries) {
            accumulated += Math.max(1, entry.ticks);
            if (pos < accumulated) return entry.color;
        }

        return entries.get(entries.size() - 1).color;
    }

    private Color lerp(Color from, Color to, float t) {
        return new Color(
            (int) (from.r + (to.r - from.r) * t),
            (int) (from.g + (to.g - from.g) * t),
            (int) (from.b + (to.b - from.b) * t),
            (int) (from.a + (to.a - from.a) * t)
        );
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
        List<SettingColor> colors = gradientColors.get();
        if (colors.isEmpty()) return Color.WHITE;
        if (colors.size() == 1) return colors.get(0);

        double time = (System.currentTimeMillis() / 1000.0 * gradientSpeed.get()) % colors.size();
        int index = (int) time;
        float progress = (float) (time - index);

        SettingColor from = colors.get(index);
        SettingColor to = colors.get((index + 1) % colors.size());

        return new Color(
            (int) (from.r + (to.r - from.r) * progress),
            (int) (from.g + (to.g - from.g) * progress),
            (int) (from.b + (to.b - from.b) * progress),
            (int) (from.a + (to.a - from.a) * progress)
        );
    }
}
