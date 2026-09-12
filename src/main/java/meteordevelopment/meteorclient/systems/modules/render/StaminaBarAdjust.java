/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Drives Stamina Bar HUD's background/fill colors, same split-by-concern relationship XPBarAdjust
 * has with Vanilla XP Bar - this module owns "how to color it" (Fixed, or one of the animated
 * modes below), while the HUD element owns "where/how big to draw it". Stamina Bar HUD falls back
 * to its own plain Background/Fill Color settings when this module is inactive, so it still works
 * standalone without requiring this module at all.
 */
public class StaminaBarAdjust extends Module {
    public enum ColorMode {
        Fixed,
        Rainbow,
        Gradient,
        Flashing,
        HueShift,
        ByValue
    }

    public enum RainbowTransition {
        Soft,
        Hard
    }

    public enum LowHealthMode {
        Off,
        Permanent,
        FlashWhenReached
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSpecialColors = settings.createGroup("Max/Low Hunger Color");

    private final Setting<Integer> alpha = sgGeneral.add(new IntSetting.Builder()
        .name("alpha")
        .description("Transparency of the real food icons - 255 is fully opaque, 0 is invisible. Applies whether or not Vanilla Hunger is relocated (that element's own Alpha setting multiplies with this one if both are set).")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    private final SettingGroup sgBackgroundRainbow = settings.createGroup("Background Color (Rainbow)");
    private final SettingGroup sgBackgroundGradient = settings.createGroup("Background Color (Gradient / Flashing)");
    private final SettingGroup sgBackgroundHueShift = settings.createGroup("Background Color (Hue Shift)");
    private final SettingGroup sgBackgroundByValue = settings.createGroup("Background Color (By Value)");
    private final SettingGroup sgFillRainbow = settings.createGroup("Fill Color (Rainbow)");
    private final SettingGroup sgFillGradient = settings.createGroup("Fill Color (Gradient / Flashing)");
    private final SettingGroup sgFillHueShift = settings.createGroup("Fill Color (Hue Shift)");
    private final SettingGroup sgFillByValue = settings.createGroup("Fill Color (By Value)");

    // Background Color

    private final Setting<ColorMode> backgroundMode = sgGeneral.add(new EnumSetting.Builder<ColorMode>()
        .name("background-mode")
        .description("How the bar's background picks its color.")
        .defaultValue(ColorMode.Fixed)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color for the background, when Background Mode above is Fixed.")
        .defaultValue(new SettingColor(25, 25, 25, 150))
        .visible(() -> backgroundMode.get() == ColorMode.Fixed)
        .build()
    );

    // Fill Color

    private final Setting<ColorMode> fillMode = sgGeneral.add(new EnumSetting.Builder<ColorMode>()
        .name("fill-mode")
        .description("How the bar's fill picks its color.")
        .defaultValue(ColorMode.Fixed)
        .build()
    );

    private final Setting<SettingColor> fillColor = sgGeneral.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("Color for the fill, when Fill Mode above is Fixed.")
        .defaultValue(new SettingColor(220, 30, 30))
        .visible(() -> fillMode.get() == ColorMode.Fixed)
        .build()
    );

    // Max/Low Hunger Color - both override whatever Fill Mode above would otherwise produce,
    // applied to hearts (Vanilla) and/or the bar's fill (Bar) per Apply To above.

    private final Setting<Boolean> maxHealthColorEnabled = sgSpecialColors.add(new BoolSetting.Builder()
        .name("max-hunger-color")
        .description("Uses a distinct color whenever hunger is at/above the threshold below, overriding Fill Mode above at that point.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxHealthThreshold = sgSpecialColors.add(new IntSetting.Builder()
        .name("max-hunger-threshold")
        .description("Hunger percent at/above which the max-hunger color applies. 100 = only exactly full, same as before this was adjustable - lower it to trigger a bit before completely full.")
        .defaultValue(100)
        .range(1, 100)
        .sliderRange(1, 100)
        .visible(maxHealthColorEnabled::get)
        .build()
    );

    private final Setting<SettingColor> maxHealthColor = sgSpecialColors.add(new ColorSetting.Builder()
        .name("max-hunger-color-value")
        .description("Color used while hunger is at/above the threshold, when Max Hunger Color above is on.")
        .defaultValue(new SettingColor(255, 215, 0))
        .visible(maxHealthColorEnabled::get)
        .build()
    );

    private final Setting<LowHealthMode> lowHealthMode = sgSpecialColors.add(new EnumSetting.Builder<LowHealthMode>()
        .name("low-hunger-mode")
        .description("Off: no low-hunger color. Permanent: always uses the low-hunger color, no threshold - a plain fixed override, always on. Flash When Reached: briefly flashes the low-hunger color only at the moment hunger first drops to/below the threshold below, then goes back to normal even if it stays low - like vanilla's own hunger shake, but a one-shot flash instead of a repeating one.")
        .defaultValue(LowHealthMode.Off)
        .build()
    );

    private final Setting<Integer> lowHealthThreshold = sgSpecialColors.add(new IntSetting.Builder()
        .name("low-hunger-threshold")
        .description("Hunger percent at/below which the low-hunger color flashes (vanilla's own hunger shake kicks in at 3 legs of meat = 6/20 = 30% - this defaults to match that exactly). Only used by Flash When Reached - Permanent has no threshold, it's just always on.")
        .defaultValue(30)
        .range(0, 100)
        .sliderRange(0, 100)
        .visible(() -> lowHealthMode.get() == LowHealthMode.FlashWhenReached)
        .build()
    );

    private final Setting<SettingColor> lowHealthColor = sgSpecialColors.add(new ColorSetting.Builder()
        .name("low-hunger-color-value")
        .description("Color used for the low-hunger warning, when Low Health Mode above isn't Off.")
        .defaultValue(new SettingColor(255, 0, 0))
        .visible(() -> lowHealthMode.get() != LowHealthMode.Off)
        .build()
    );

    private final Setting<Integer> lowHealthFlashDuration = sgSpecialColors.add(new IntSetting.Builder()
        .name("low-hunger-flash-duration")
        .description("How many ticks the flash lasts, when Low Hunger Mode above is Flash When Reached.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 100)
        .visible(() -> lowHealthMode.get() == LowHealthMode.FlashWhenReached)
        .build()
    );

    // Background Color (Rainbow)

    private final Setting<RainbowTransition> backgroundRainbowTransition = sgBackgroundRainbow.add(new EnumSetting.Builder<RainbowTransition>()
        .name("background-rainbow-transition")
        .description("Soft smoothly cycles hue. Hard jumps between a fixed number of steps.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> backgroundMode.get() == ColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> backgroundRainbowSteps = sgBackgroundRainbow.add(new IntSetting.Builder()
        .name("background-rainbow-steps")
        .description("Number of distinct colors, when Background Rainbow Transition above is Hard.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 24)
        .visible(() -> backgroundMode.get() == ColorMode.Rainbow && backgroundRainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> backgroundRainbowSpeed = sgBackgroundRainbow.add(new DoubleSetting.Builder()
        .name("background-rainbow-speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> backgroundMode.get() == ColorMode.Rainbow)
        .build()
    );

    // Background Color (Gradient / Flashing)

    private final Setting<List<SettingColor>> backgroundGradientColors = sgBackgroundGradient.add(new ColorListSetting.Builder()
        .name("background-gradient-colors")
        .description("Colors the background cycles through, in order.")
        .defaultValue(List.of(new SettingColor(25, 25, 25), new SettingColor(60, 60, 60)))
        .visible(() -> backgroundMode.get() == ColorMode.Gradient)
        .build()
    );

    private final Setting<Double> backgroundGradientSpeed = sgBackgroundGradient.add(new DoubleSetting.Builder()
        .name("background-gradient-speed")
        .description("How fast the gradient cycles.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> backgroundMode.get() == ColorMode.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> backgroundFlashColors = sgBackgroundGradient.add(new TimedColorListSetting.Builder()
        .name("background-flash-colors")
        .description("Colors the background flashes between, each held for its own duration (in ticks).")
        .defaultValue(List.of())
        .visible(() -> backgroundMode.get() == ColorMode.Flashing)
        .build()
    );

    // Background Color (Hue Shift)

    private final Setting<SettingColor> backgroundHueShiftBaseColor = sgBackgroundHueShift.add(new ColorSetting.Builder()
        .name("background-hue-shift-base-color")
        .description("Starting color - its hue continuously swings, keeping its saturation/brightness/alpha.")
        .defaultValue(new SettingColor(25, 25, 25))
        .visible(() -> backgroundMode.get() == ColorMode.HueShift)
        .build()
    );

    private final Setting<Double> backgroundHueShiftSpeed = sgBackgroundHueShift.add(new DoubleSetting.Builder()
        .name("background-hue-shift-speed")
        .description("How fast the hue swings.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> backgroundMode.get() == ColorMode.HueShift)
        .build()
    );

    private final Setting<Double> backgroundHueShiftRange = sgBackgroundHueShift.add(new DoubleSetting.Builder()
        .name("background-hue-shift-range")
        .description("How far the hue swings from the base color, in degrees each direction. 180 = swings the full way around.")
        .defaultValue(30)
        .min(0)
        .sliderRange(0, 180)
        .visible(() -> backgroundMode.get() == ColorMode.HueShift)
        .build()
    );

    // Background Color (By Value) - smoothly blends between two colors based on the current
    // hunger percent itself, unlike Max/Low Hunger Color above (hard cutoffs at specific points/
    // thresholds) - "one color at high hunger, another at low hunger, and everything in between".

    private final Setting<SettingColor> backgroundHighColor = sgBackgroundByValue.add(new ColorSetting.Builder()
        .name("background-high-value-color")
        .description("Color at full hunger - blends toward Low Value Color below as hunger drops.")
        .defaultValue(new SettingColor(140, 100, 20))
        .visible(() -> backgroundMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> backgroundLowColor = sgBackgroundByValue.add(new ColorSetting.Builder()
        .name("background-low-value-color")
        .description("Color at zero hunger - Background High Value Color above blends toward this as hunger drops.")
        .defaultValue(new SettingColor(100, 25, 25))
        .visible(() -> backgroundMode.get() == ColorMode.ByValue)
        .build()
    );

    // Fill Color (Rainbow)

    private final Setting<RainbowTransition> fillRainbowTransition = sgFillRainbow.add(new EnumSetting.Builder<RainbowTransition>()
        .name("fill-rainbow-transition")
        .description("Soft smoothly cycles hue. Hard jumps between a fixed number of steps.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> fillMode.get() == ColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> fillRainbowSteps = sgFillRainbow.add(new IntSetting.Builder()
        .name("fill-rainbow-steps")
        .description("Number of distinct colors, when Fill Rainbow Transition above is Hard.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 24)
        .visible(() -> fillMode.get() == ColorMode.Rainbow && fillRainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> fillRainbowSpeed = sgFillRainbow.add(new DoubleSetting.Builder()
        .name("fill-rainbow-speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> fillMode.get() == ColorMode.Rainbow)
        .build()
    );

    // Fill Color (Gradient / Flashing)

    private final Setting<List<SettingColor>> fillGradientColors = sgFillGradient.add(new ColorListSetting.Builder()
        .name("fill-gradient-colors")
        .description("Colors the fill cycles through, in order.")
        .defaultValue(List.of(new SettingColor(220, 30, 30), new SettingColor(255, 140, 0)))
        .visible(() -> fillMode.get() == ColorMode.Gradient)
        .build()
    );

    private final Setting<Double> fillGradientSpeed = sgFillGradient.add(new DoubleSetting.Builder()
        .name("fill-gradient-speed")
        .description("How fast the gradient cycles.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> fillMode.get() == ColorMode.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> fillFlashColors = sgFillGradient.add(new TimedColorListSetting.Builder()
        .name("fill-flash-colors")
        .description("Colors the fill flashes between, each held for its own duration (in ticks).")
        .defaultValue(List.of())
        .visible(() -> fillMode.get() == ColorMode.Flashing)
        .build()
    );

    // Fill Color (Hue Shift)

    private final Setting<SettingColor> fillHueShiftBaseColor = sgFillHueShift.add(new ColorSetting.Builder()
        .name("fill-hue-shift-base-color")
        .description("Starting color - its hue continuously swings, keeping its saturation/brightness/alpha.")
        .defaultValue(new SettingColor(220, 30, 30))
        .visible(() -> fillMode.get() == ColorMode.HueShift)
        .build()
    );

    private final Setting<Double> fillHueShiftSpeed = sgFillHueShift.add(new DoubleSetting.Builder()
        .name("fill-hue-shift-speed")
        .description("How fast the hue swings.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> fillMode.get() == ColorMode.HueShift)
        .build()
    );

    private final Setting<Double> fillHueShiftRange = sgFillHueShift.add(new DoubleSetting.Builder()
        .name("fill-hue-shift-range")
        .description("How far the hue swings from the base color, in degrees each direction. 180 = swings the full way around.")
        .defaultValue(30)
        .min(0)
        .sliderRange(0, 180)
        .visible(() -> fillMode.get() == ColorMode.HueShift)
        .build()
    );

    // Fill Color (By Value) - see Background Color (By Value) above for the same idea.

    private final Setting<SettingColor> fillHighColor = sgFillByValue.add(new ColorSetting.Builder()
        .name("fill-high-value-color")
        .description("Color at full hunger - blends toward Low Value Color below as hunger drops.")
        .defaultValue(new SettingColor(255, 200, 40))
        .visible(() -> fillMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> fillLowColor = sgFillByValue.add(new ColorSetting.Builder()
        .name("fill-low-value-color")
        .description("Color at zero hunger - Fill High Value Color above blends toward this as hunger drops.")
        .defaultValue(new SettingColor(220, 30, 30))
        .visible(() -> fillMode.get() == ColorMode.ByValue)
        .build()
    );

    private boolean wasAboveLowThreshold = true;
    private int lowFlashTicksRemaining = 0;

    public StaminaBarAdjust() {
        super(Categories.Render, "stamina-bar-adjust", "Colors the real hunger icons and/or Stamina Bar HUD - Fixed, or an animated mode independent of hunger, plus distinct Max/Low hunger colors.");
    }

    @Override
    public void onActivate() {
        wasAboveLowThreshold = true;
        lowFlashTicksRemaining = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || lowHealthMode.get() != LowHealthMode.FlashWhenReached) return;

        float progress = mc.player.getHungerManager().getFoodLevel() / 20f;
        boolean above = progress > lowHealthThreshold.get() / 100.0;

        if (!above && wasAboveLowThreshold) lowFlashTicksRemaining = lowHealthFlashDuration.get();
        wasAboveLowThreshold = above;

        if (lowFlashTicksRemaining > 0) lowFlashTicksRemaining--;
    }

    public double getAlpha() {
        return alpha.get() / 255.0;
    }

    /** progress: current/max hunger, 0-1 - used by By Value mode below (a smooth blend, unlike the hard-cutoff Max/Low Hunger Color, which only applies to Fill). */
    public Color getBackgroundColor(Color fallback, float progress) {
        return switch (backgroundMode.get()) {
            case Fixed -> backgroundColor.get();
            case Rainbow -> rainbowColor(backgroundRainbowTransition.get(), backgroundRainbowSteps.get(), backgroundRainbowSpeed.get());
            case Gradient -> gradientColor(backgroundGradientColors.get(), backgroundGradientSpeed.get());
            case Flashing -> flashColor(backgroundFlashColors.get(), fallback);
            case HueShift -> hueShiftColor(backgroundHueShiftBaseColor.get(), backgroundHueShiftSpeed.get(), backgroundHueShiftRange.get());
            case ByValue -> byValueColor(backgroundHighColor.get(), backgroundLowColor.get(), progress);
        };
    }

    /** progress: current/max hunger, 0-1 - used for the Max/Low hunger color overrides below (hard cutoffs), which take priority over Fill Mode, and for By Value mode (a smooth blend across the whole range instead). */
    public Color getFillColor(Color fallback, float progress) {
        if (maxHealthColorEnabled.get() && progress >= maxHealthThreshold.get() / 100.0) return maxHealthColor.get();

        if (lowHealthMode.get() == LowHealthMode.Permanent) return lowHealthColor.get();
        if (lowHealthMode.get() == LowHealthMode.FlashWhenReached && lowFlashTicksRemaining > 0) return lowHealthColor.get();

        return switch (fillMode.get()) {
            case Fixed -> fillColor.get();
            case Rainbow -> rainbowColor(fillRainbowTransition.get(), fillRainbowSteps.get(), fillRainbowSpeed.get());
            case Gradient -> gradientColor(fillGradientColors.get(), fillGradientSpeed.get());
            case Flashing -> flashColor(fillFlashColors.get(), fallback);
            case HueShift -> hueShiftColor(fillHueShiftBaseColor.get(), fillHueShiftSpeed.get(), fillHueShiftRange.get());
            case ByValue -> byValueColor(fillHighColor.get(), fillLowColor.get(), progress);
        };
    }

    /** Linear blend from lowColor (progress 0) to highColor (progress 1) - "one color at high hunger, another at low hunger". */
    private Color byValueColor(SettingColor highColor, SettingColor lowColor, float progress) {
        float t = MathHelper.clamp(progress, 0f, 1f);

        return new Color(
            (int) (lowColor.r + (highColor.r - lowColor.r) * t),
            (int) (lowColor.g + (highColor.g - lowColor.g) * t),
            (int) (lowColor.b + (highColor.b - lowColor.b) * t),
            (int) (lowColor.a + (highColor.a - lowColor.a) * t)
        );
    }

    // Shared math only - Background Color and Fill Color each bring their own fully independent
    // settings values, so picking the same mode for both does NOT make them animate together
    // unless their speeds/steps/colors happen to match.

    private Color rainbowColor(RainbowTransition transition, int steps, double speed) {
        double time = System.currentTimeMillis() / 1000.0 * speed * 60.0;

        float hue;
        if (transition == RainbowTransition.Soft) {
            hue = (float) (time % 360.0);
        } else {
            int step = (int) (time / (360.0 / steps)) % steps;
            hue = step * (360f / steps);
        }

        return Color.fromHsv(hue, 1, 1);
    }

    private Color gradientColor(List<SettingColor> colors, double speed) {
        if (colors.isEmpty()) return Color.WHITE;
        if (colors.size() == 1) return colors.get(0);

        double time = (System.currentTimeMillis() / 1000.0 * speed) % colors.size();
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

    private Color flashColor(List<TimedColorEntry> entries, Color fallback) {
        if (entries.isEmpty()) return fallback;
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

    private Color hueShiftColor(SettingColor base, double speed, double range) {
        float[] hsb = java.awt.Color.RGBtoHSB(base.r, base.g, base.b, null);

        double time = System.currentTimeMillis() / 1000.0 * speed;
        double offset = Math.sin(time) * range;
        float hue = (float) (((hsb[0] * 360.0 + offset) % 360.0 + 360.0) % 360.0);

        Color color = Color.fromHsv(hue, hsb[1], hsb[2]);
        color.a = base.a;
        return color;
    }
}
