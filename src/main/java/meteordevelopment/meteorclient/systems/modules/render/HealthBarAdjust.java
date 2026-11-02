/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.screens.settings.ColorRangeListScreen;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.StatusColorOverrides;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Drives Health Bar HUD's background/fill colors, same split-by-concern relationship XPBarAdjust
 * has with Vanilla XP Bar - this module owns "how to color it" (Fixed, or one of the animated
 * modes below), while the HUD element owns "where/how big to draw it". Health Bar HUD falls back
 * to its own plain Background/Fill Color settings when this module is inactive, so it still works
 * standalone without requiring this module at all.
 */
public class HealthBarAdjust extends Module {
    public enum ColorMode {
        Fixed,
        Rainbow,
        Gradient,
        Flashing,
        HueShift,
        ByValue,
        ByValueRainbow,
        ByValueRanges
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

    public enum ThresholdMode {
        Percent,
        Value
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSpecialColors = settings.createGroup("Max/Low Health Color");

    private final Setting<Integer> alpha = sgGeneral.add(new IntSetting.Builder()
        .name("alpha")
        .description("Transparency of the real hearts - 255 is fully opaque, 0 is invisible. Applies whether or not Vanilla Health is relocated (that element's own Alpha setting multiplies with this one if both are set).")
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
    private final StatusColorOverrides statusColors = new StatusColorOverrides(settings.createGroup("Status Effect Colors"));

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

    // Max/Low Health Color - both override whatever Fill Mode above would otherwise produce,
    // applied to hearts (Vanilla) and/or the bar's fill (Bar) per Apply To above.

    private final Setting<Boolean> maxHealthColorEnabled = sgSpecialColors.add(new BoolSetting.Builder()
        .name("max-health-color")
        .description("Uses a distinct color whenever health is at/above the threshold below, overriding Fill Mode above at that point.")
        .defaultValue(false)
        .build()
    );

    private final Setting<ThresholdMode> maxThresholdMode = sgSpecialColors.add(new EnumSetting.Builder<ThresholdMode>()
        .name("max-health-threshold-mode")
        .description("Whether the threshold below is a percent of max health, or a specific absolute health value - useful on servers where max health goes well above the vanilla 20 (10 hearts).")
        .defaultValue(ThresholdMode.Percent)
        .visible(maxHealthColorEnabled::get)
        .build()
    );

    private final Setting<Integer> maxHealthThreshold = sgSpecialColors.add(new IntSetting.Builder()
        .name("max-health-threshold")
        .description("Health percent at/above which the max-health color applies. 100 = only exactly full, same as before this was adjustable - lower it to trigger a bit before completely full.")
        .defaultValue(100)
        .range(1, 100)
        .sliderRange(1, 100)
        .visible(() -> maxHealthColorEnabled.get() && maxThresholdMode.get() == ThresholdMode.Percent)
        .build()
    );

    private final Setting<Integer> maxThresholdValue = sgSpecialColors.add(new IntSetting.Builder()
        .name("max-health-threshold-value")
        .description("Health value at/above which the max-health color applies, when the mode above is Value instead of Percent.")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 200)
        .visible(() -> maxHealthColorEnabled.get() && maxThresholdMode.get() == ThresholdMode.Value)
        .build()
    );

    private final Setting<SettingColor> maxHealthColor = sgSpecialColors.add(new ColorSetting.Builder()
        .name("max-health-color-value")
        .description("Color used while health is at/above the threshold, when Max Health Color above is on.")
        .defaultValue(new SettingColor(255, 215, 0))
        .visible(maxHealthColorEnabled::get)
        .build()
    );

    private final Setting<LowHealthMode> lowHealthMode = sgSpecialColors.add(new EnumSetting.Builder<LowHealthMode>()
        .name("low-health-mode")
        .description("Off: no low-health color. Permanent: always uses the low-health color, no threshold - a plain fixed override, always on. Flash When Reached: briefly flashes the low-health color only at the moment health first drops to/below the threshold below, then goes back to normal even if it stays low - like vanilla's own hunger shake, but a one-shot flash instead of a repeating one.")
        .defaultValue(LowHealthMode.Off)
        .build()
    );

    private final Setting<ThresholdMode> lowThresholdMode = sgSpecialColors.add(new EnumSetting.Builder<ThresholdMode>()
        .name("low-health-threshold-mode")
        .description("Whether the threshold below is a percent of max health, or a specific absolute health value.")
        .defaultValue(ThresholdMode.Percent)
        .visible(() -> lowHealthMode.get() == LowHealthMode.FlashWhenReached)
        .build()
    );

    private final Setting<Integer> lowHealthThreshold = sgSpecialColors.add(new IntSetting.Builder()
        .name("low-health-threshold")
        .description("Health percent at/below which the low-health color flashes (vanilla's own hunger shake kicks in at 3 legs of meat = 6/20 = 30%, if you want an equivalent feel here). Only used by Flash When Reached - Permanent has no threshold, it's just always on.")
        .defaultValue(30)
        .range(0, 100)
        .sliderRange(0, 100)
        .visible(() -> lowHealthMode.get() == LowHealthMode.FlashWhenReached && lowThresholdMode.get() == ThresholdMode.Percent)
        .build()
    );

    private final Setting<Integer> lowThresholdValue = sgSpecialColors.add(new IntSetting.Builder()
        .name("low-health-threshold-value")
        .description("Health value at/below which the low-health color flashes, when the mode above is Value instead of Percent.")
        .defaultValue(6)
        .min(0)
        .sliderRange(0, 200)
        .visible(() -> lowHealthMode.get() == LowHealthMode.FlashWhenReached && lowThresholdMode.get() == ThresholdMode.Value)
        .build()
    );

    private final Setting<SettingColor> lowHealthColor = sgSpecialColors.add(new ColorSetting.Builder()
        .name("low-health-color-value")
        .description("Color used for the low-health warning, when Low Health Mode above isn't Off.")
        .defaultValue(new SettingColor(255, 0, 0))
        .visible(() -> lowHealthMode.get() != LowHealthMode.Off)
        .build()
    );

    private final Setting<Integer> lowHealthFlashDuration = sgSpecialColors.add(new IntSetting.Builder()
        .name("low-health-flash-duration")
        .description("How many ticks the flash lasts, when Low Health Mode above is Flash When Reached.")
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

    // Background Color (By Value) - smoothly blends between two colors based on the current health
    // percent itself, unlike Max/Low Health Color above (hard cutoffs at specific points/
    // thresholds) - "one color at high health, another at low health, and everything in between".

    private final Setting<SettingColor> backgroundHighColor = sgBackgroundByValue.add(new ColorSetting.Builder()
        .name("background-high-value-color")
        .description("Color at full health - blends toward Mid Value Color below as health drops.")
        .defaultValue(new SettingColor(25, 100, 25))
        .visible(() -> backgroundMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> backgroundMidColor = sgBackgroundByValue.add(new ColorSetting.Builder()
        .name("background-mid-value-color")
        .description("Color at half health - blends between Background High Value Color above and Background Low Value Color below.")
        .defaultValue(new SettingColor(100, 55, 10))
        .visible(() -> backgroundMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> backgroundLowColor = sgBackgroundByValue.add(new ColorSetting.Builder()
        .name("background-low-value-color")
        .description("Color at zero health - Background Mid Value Color above blends toward this as health drops.")
        .defaultValue(new SettingColor(100, 25, 25))
        .visible(() -> backgroundMode.get() == ColorMode.ByValue)
        .build()
    );

    // Background Color (By Value Ranges) - unlike By Value above (a smooth blend across the whole
    // range), this picks a hard, un-blended color per band of real health points you define
    // yourself, e.g. "red below 10, yellow 10-20, green above that".

    private final Setting<List<ColorRangeEntry>> backgroundRanges = sgBackgroundByValue.add(new ColorRangeListSetting.Builder()
        .name("background-value-ranges")
        .description("Each band's own health-point cutoff and color - see Edit Ranges below.")
        .visible(() -> backgroundMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Void> backgroundRangesEdit = sgBackgroundByValue.add(new ButtonSetting.Builder()
        .name("background-value-ranges-edit")
        .description("Add/edit/remove the health-point bands above.")
        .buttonText("Edit Ranges")
        .screen(theme -> new ColorRangeListScreen(theme, "Background", backgroundRanges.get(), mc.player != null ? mc.player.getMaxHealth() : 20))
        .visible(() -> backgroundMode.get() == ColorMode.ByValueRanges)
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
        .description("Color at full health - blends toward Mid Value Color below as health drops.")
        .defaultValue(new SettingColor(60, 220, 60))
        .visible(() -> fillMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> fillMidColor = sgFillByValue.add(new ColorSetting.Builder()
        .name("fill-mid-value-color")
        .description("Color at half health - blends between Fill High Value Color above and Fill Low Value Color below.")
        .defaultValue(new SettingColor(255, 140, 0))
        .visible(() -> fillMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> fillLowColor = sgFillByValue.add(new ColorSetting.Builder()
        .name("fill-low-value-color")
        .description("Color at zero health - Fill Mid Value Color above blends toward this as health drops.")
        .defaultValue(new SettingColor(220, 30, 30))
        .visible(() -> fillMode.get() == ColorMode.ByValue)
        .build()
    );

    // Fill Color (By Value Ranges) - see Background Color (By Value Ranges) above for the same idea.

    private final Setting<List<ColorRangeEntry>> fillRanges = sgFillByValue.add(new ColorRangeListSetting.Builder()
        .name("fill-value-ranges")
        .description("Each band's own health-point cutoff and color - see Edit Ranges below.")
        .visible(() -> fillMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Void> fillRangesEdit = sgFillByValue.add(new ButtonSetting.Builder()
        .name("fill-value-ranges-edit")
        .description("Add/edit/remove the health-point bands above.")
        .buttonText("Edit Ranges")
        .screen(theme -> new ColorRangeListScreen(theme, "Fill", fillRanges.get(), mc.player != null ? mc.player.getMaxHealth() : 20))
        .visible(() -> fillMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private boolean wasAboveLowThreshold = true;
    private int lowFlashTicksRemaining = 0;

    public HealthBarAdjust() {
        super(Categories.Hud, "health-bar-adjust", "Colors the real hearts and/or Health Bar HUD - Fixed, or an animated mode independent of health, plus distinct Max/Low health colors.");
    }

    @Override
    public void onActivate() {
        wasAboveLowThreshold = true;
        lowFlashTicksRemaining = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || lowHealthMode.get() != LowHealthMode.FlashWhenReached) return;

        float progress = mc.player.getMaxHealth() > 0 ? mc.player.getHealth() / mc.player.getMaxHealth() : 0;
        boolean above = progress > lowThresholdFraction();

        if (!above && wasAboveLowThreshold) lowFlashTicksRemaining = lowHealthFlashDuration.get();
        wasAboveLowThreshold = above;

        if (lowFlashTicksRemaining > 0) lowFlashTicksRemaining--;
    }

    public double getAlpha() {
        return alpha.get() / 255.0;
    }

    /** Low/Max Health Color's threshold, as a 0-1 fraction of real max health - either a direct percent, or an absolute health value divided by the player's actual current max health (so it still tracks correctly on servers where max health isn't the vanilla 20). */
    private double lowThresholdFraction() {
        if (lowThresholdMode.get() == ThresholdMode.Percent) return lowHealthThreshold.get() / 100.0;

        float maxHealth = mc.player != null ? mc.player.getMaxHealth() : 20f;
        return maxHealth > 0 ? lowThresholdValue.get() / maxHealth : 0;
    }

    private double maxThresholdFraction() {
        if (maxThresholdMode.get() == ThresholdMode.Percent) return maxHealthThreshold.get() / 100.0;

        float maxHealth = mc.player != null ? mc.player.getMaxHealth() : 20f;
        return maxHealth > 0 ? maxThresholdValue.get() / maxHealth : 1;
    }

    /** progress: current/max health, 0-1 - used by By Value mode below (a smooth blend, unlike the hard-cutoff Max/Low Health Color, which only applies to Fill). */
    public Color getBackgroundColor(Color fallback, float progress) {
        return switch (backgroundMode.get()) {
            case Fixed -> backgroundColor.get();
            case Rainbow -> rainbowColor(backgroundRainbowTransition.get(), backgroundRainbowSteps.get(), backgroundRainbowSpeed.get());
            case Gradient -> gradientColor(backgroundGradientColors.get(), backgroundGradientSpeed.get());
            case Flashing -> flashColor(backgroundFlashColors.get(), fallback);
            case HueShift -> hueShiftColor(backgroundHueShiftBaseColor.get(), backgroundHueShiftSpeed.get(), backgroundHueShiftRange.get());
            case ByValue -> byValueColor(backgroundHighColor.get(), backgroundMidColor.get(), backgroundLowColor.get(), progress);
            case ByValueRainbow -> byValueRainbowColor(progress);
            case ByValueRanges -> rangesColor(backgroundRanges.get());
        };
    }

    /** progress: current/max health, 0-1 - used for the Max/Low health color overrides below (hard cutoffs), which take priority over Fill Mode, and for By Value mode (a smooth blend across the whole range instead). */
    public Color getFillColor(Color fallback, float progress) {
        Color statusColor = statusColors.get();
        if (statusColor != null) return statusColor;

        if (maxHealthColorEnabled.get() && progress >= maxThresholdFraction()) return maxHealthColor.get();

        if (lowHealthMode.get() == LowHealthMode.Permanent) return lowHealthColor.get();
        if (lowHealthMode.get() == LowHealthMode.FlashWhenReached && lowFlashTicksRemaining > 0) return lowHealthColor.get();

        return switch (fillMode.get()) {
            case Fixed -> fillColor.get();
            case Rainbow -> rainbowColor(fillRainbowTransition.get(), fillRainbowSteps.get(), fillRainbowSpeed.get());
            case Gradient -> gradientColor(fillGradientColors.get(), fillGradientSpeed.get());
            case Flashing -> flashColor(fillFlashColors.get(), fallback);
            case HueShift -> hueShiftColor(fillHueShiftBaseColor.get(), fillHueShiftSpeed.get(), fillHueShiftRange.get());
            case ByValue -> byValueColor(fillHighColor.get(), fillMidColor.get(), fillLowColor.get(), progress);
            case ByValueRainbow -> byValueRainbowColor(progress);
            case ByValueRanges -> rangesColor(fillRanges.get());
        };
    }

    /** By Value Ranges - a hard, un-blended color per user-defined band of real health points. */
    private Color rangesColor(List<ColorRangeEntry> ranges) {
        double health = mc.player != null ? mc.player.getHealth() : 0;
        return ColorRangeEntry.resolve(ranges, health, Color.WHITE);
    }

    /** Piecewise-linear blend through three colors - lowColor at progress 0, midColor at 0.5, highColor at 1. */
    private Color byValueColor(SettingColor highColor, SettingColor midColor, SettingColor lowColor, float progress) {
        float t = MathHelper.clamp(progress, 0f, 1f);
        boolean upper = t >= 0.5f;
        SettingColor from = upper ? midColor : lowColor;
        SettingColor to = upper ? highColor : midColor;
        float k = upper ? (t - 0.5f) * 2f : t * 2f;

        return new Color(
            (int) (from.r + (to.r - from.r) * k),
            (int) (from.g + (to.g - from.g) * k),
            (int) (from.b + (to.b - from.b) * k),
            (int) (from.a + (to.a - from.a) * k)
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

    /** "By Value Rainbow": the whole rainbow mapped onto the value - red at 0 sweeping through to violet at full. */
    private Color byValueRainbowColor(float progress) {
        return Color.fromHsv(MathHelper.clamp(progress, 0f, 1f) * 300.0, 1, 1);
    }
}
