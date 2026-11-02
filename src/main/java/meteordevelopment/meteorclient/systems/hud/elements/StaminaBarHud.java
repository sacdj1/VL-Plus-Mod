/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.screens.settings.ColorRangeListScreen;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import meteordevelopment.meteorclient.utils.misc.StatusColorOverrides;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * A plain bar (not vanilla's food/hunger icon row) showing hunger ("stamina") as a proportional
 * fill, independently sized/scaled from vanilla's fixed layout. See HealthBarHud for the same
 * pattern with a Nametag health source - not offered here since vanilla hunger has no equivalent
 * server-displayed nametag convention to read from.
 *
 * Owns its own full color-mode system (Fixed/Rainbow/Gradient/Flashing/Hue Shift/By Value, plus
 * Max/Low Hunger Color overrides) rather than deferring to Stamina Bar Adjust - the two used to
 * share color logic, but that meant changing the module's colors (which also drive the real
 * vanilla food icons) always changed this bar too, with no way to keep them independent.
 */
public class StaminaBarHud extends HudElement {

    @Override
    public boolean hasOwnBackground() {
        return true;
    }

    public static final HudElementInfo<StaminaBarHud> INFO = new HudElementInfo<>(Hud.GROUP, "stamina-bar", "A plain scalable stamina/hunger bar, independent of vanilla's food icon row.", StaminaBarHud::new);

    public enum DisplayMode {
        Bar,
        Numbers,
        Both
    }

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

    public enum LowHungerMode {
        Off,
        Permanent,
        FlashWhenReached
    }

    public enum LowIndicatorStyle {
        Zone,
        Line
    }

    public enum ThresholdMode {
        Percent,
        Value
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgBorder = settings.createGroup("Border");
    private final SettingGroup sgSpecialColors = settings.createGroup("Max/Low Hunger Color");
    private final SettingGroup sgBackgroundRainbow = settings.createGroup("Background Color (Rainbow)");
    private final SettingGroup sgBackgroundGradient = settings.createGroup("Background Color (Gradient / Flashing)");
    private final SettingGroup sgBackgroundHueShift = settings.createGroup("Background Color (Hue Shift)");
    private final SettingGroup sgBackgroundByValue = settings.createGroup("Background Color (By Value)");
    private final SettingGroup sgFillRainbow = settings.createGroup("Fill Color (Rainbow)");
    private final SettingGroup sgFillGradient = settings.createGroup("Fill Color (Gradient / Flashing)");
    private final SettingGroup sgFillHueShift = settings.createGroup("Fill Color (Hue Shift)");
    private final SettingGroup sgFillByValue = settings.createGroup("Fill Color (By Value)");
    private final StatusColorOverrides statusColors = new StatusColorOverrides(settings.createGroup("Status Effect Colors"));
    private final SettingGroup sgTextRainbow = settings.createGroup("Text Color (Rainbow)");
    private final SettingGroup sgTextGradient = settings.createGroup("Text Color (Gradient / Flashing)");
    private final SettingGroup sgTextHueShift = settings.createGroup("Text Color (Hue Shift)");
    private final SettingGroup sgTextByValue = settings.createGroup("Text Color (By Value)");
    private final SettingGroup sgBorderRainbow = settings.createGroup("Border Color (Rainbow)");
    private final SettingGroup sgBorderGradient = settings.createGroup("Border Color (Gradient / Flashing)");
    private final SettingGroup sgBorderHueShift = settings.createGroup("Border Color (Hue Shift)");
    private final SettingGroup sgBorderByValue = settings.createGroup("Border Color (By Value)");

    private final Setting<Double> width = sgGeneral.add(new DoubleSetting.Builder()
        .name("width")
        .description("Base width, before Scale X/Scale below are applied.")
        .defaultValue(100)
        .min(1)
        .sliderRange(1, 400)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("height")
        .description("Base height, before Scale Y/Scale below are applied.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 100)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Double> scaleX = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale-x")
        .description("Horizontal scale, independent of Scale Y - stretches/squashes the bar without affecting its height.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Double> scaleY = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale-y")
        .description("Vertical scale, independent of Scale X - stretches/squashes the bar without affecting its width.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Overall scale, multiplied on top of Scale X/Scale Y - scales both dimensions together.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Integer> rotation = sgGeneral.add(new IntSetting.Builder()
        .name("rotation")
        .description("Rotates the whole bar around its own center, in degrees clockwise. The current/max text (Numbers/Both) stays upright regardless, same as Item Info's badges.")
        .defaultValue(0)
        .range(-359, 359)
        .sliderRange(-359, 359)
        .build()
    );

    private final Setting<DisplayMode> displayMode = sgGeneral.add(new EnumSetting.Builder<DisplayMode>()
        .name("display-mode")
        .description("Bar: fill only. Numbers: text only (current/max). Both: bar with the numbers drawn over it.")
        .defaultValue(DisplayMode.Bar)
        .build()
    );

    private final Setting<Boolean> flip = sgGeneral.add(new BoolSetting.Builder()
        .name("flip")
        .description("Fills from the left edge toward the right instead of right toward left (the default).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pillShape = sgGeneral.add(new BoolSetting.Builder()
        .name("pill-shape")
        .description("Fully rounds the bar's ends into a pill/stadium shape, regardless of Corner Radius below.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> cornerRadius = sgGeneral.add(new IntSetting.Builder()
        .name("corner-radius")
        .description("How rounded the bar's corners are, in pixels (before Scale). Automatically capped to half the bar's height/width, so it can never overshoot into a pill shape on its own - use Pill Shape above for that.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 20)
        .visible(() -> !pillShape.get())
        .build()
    );

    private final Setting<Boolean> showBorder = sgBorder.add(new BoolSetting.Builder()
        .name("show-border")
        .description("Draws an outline around the whole bar, matching its own rounded/pill shape - purely additive padding, grows the element's total size rather than shrinking the bar itself to make room.")
        .defaultValue(false)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<ColorMode> borderMode = sgBorder.add(new EnumSetting.Builder<ColorMode>()
        .name("border-mode")
        .description("How the outline picks its color.")
        .defaultValue(ColorMode.Fixed)
        .visible(showBorder::get)
        .build()
    );

    private final Setting<SettingColor> borderColor = sgBorder.add(new ColorSetting.Builder()
        .name("border-color")
        .description("Color of the outline, when Border Mode above is Fixed.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.Fixed)
        .build()
    );

    private final Setting<Double> borderThickness = sgBorder.add(new DoubleSetting.Builder()
        .name("border-thickness")
        .description("Thickness of the outline, in pixels (before Scale).")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .decimalPlaces(1)
        .visible(showBorder::get)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Integer> maxValue = sgGeneral.add(new IntSetting.Builder()
        .name("max-value")
        .description("The value that counts as full - the bar reaches 100% (and shows this as the second number in Numbers/Both) at this amount. Change this on servers where 'stamina' is a different, custom stat reusing this same hunger display.")
        .defaultValue(19)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> sprintRange = sgGeneral.add(new BoolSetting.Builder()
        .name("sprint-range")
        .description("Only shows stamina you can actually spend: the bar's empty point moves up to 3 full hunger bars (6 food) - vanilla stops letting you sprint at or below that - so the bar reads empty exactly when you can't sprint any more, instead of still looking a third full. Max Value stays the top; everything (fill, numbers, Low/Max thresholds) uses the shortened range.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> smoothFill = sgGeneral.add(new BoolSetting.Builder()
        .name("smooth-fill")
        .description("Interpolates the fill smoothly toward its real value over time, instead of snapping instantly.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> smoothSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("smooth-speed")
        .description("How fast the displayed fill catches up to the real value. Higher catches up faster/snappier, lower lags more/smoother.")
        .defaultValue(15)
        .min(0.1)
        .sliderRange(1, 60)
        .visible(smoothFill::get)
        .build()
    );

    // Background Color

    private final Setting<ColorMode> backgroundMode = sgColors.add(new EnumSetting.Builder<ColorMode>()
        .name("background-mode")
        .description("How the bar's background picks its color.")
        .defaultValue(ColorMode.Fixed)
        .visible(() -> displayMode.get() != DisplayMode.Numbers)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgColors.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color for the background, when Background Mode above is Fixed.")
        .defaultValue(new SettingColor(25, 25, 25, 150))
        .visible(() -> displayMode.get() != DisplayMode.Numbers && backgroundMode.get() == ColorMode.Fixed)
        .build()
    );

    // Fill Color

    private final Setting<ColorMode> fillMode = sgColors.add(new EnumSetting.Builder<ColorMode>()
        .name("fill-mode")
        .description("How the bar's fill picks its color.")
        .defaultValue(ColorMode.Fixed)
        .visible(() -> displayMode.get() != DisplayMode.Numbers)
        .build()
    );

    private final Setting<SettingColor> fillColor = sgColors.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("Color for the fill, when Fill Mode above is Fixed.")
        .defaultValue(new SettingColor(210, 160, 50))
        .visible(() -> displayMode.get() != DisplayMode.Numbers && fillMode.get() == ColorMode.Fixed)
        .build()
    );

    private final Setting<Boolean> fillShading = sgColors.add(new BoolSetting.Builder()
        .name("fill-shading")
        .description("Darkens the fill smoothly from top to bottom, for a simple glossy/embossed look - applies on top of whatever Fill Mode above already produces.")
        .defaultValue(false)
        .visible(() -> displayMode.get() != DisplayMode.Numbers)
        .build()
    );

    private final Setting<Double> fillShadingStrength = sgColors.add(new DoubleSetting.Builder()
        .name("fill-shading-strength")
        .description("How much darker the bottom of the fill gets, relative to the top. 0 = no darkening, 1 = fades to black.")
        .defaultValue(0.4)
        .range(0, 1)
        .sliderRange(0, 1)
        .visible(() -> displayMode.get() != DisplayMode.Numbers && fillShading.get())
        .build()
    );

    private final Setting<ColorMode> textMode = sgColors.add(new EnumSetting.Builder<ColorMode>()
        .name("text-mode")
        .description("How the current/max text picks its color.")
        .defaultValue(ColorMode.Fixed)
        .visible(() -> displayMode.get() != DisplayMode.Bar)
        .build()
    );

    private final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Color of the current/max text, when Text Mode above is Fixed.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> displayMode.get() != DisplayMode.Bar && textMode.get() == ColorMode.Fixed)
        .build()
    );

    // Max/Low Hunger Color - both override whatever Fill Mode above would otherwise produce.

    private final Setting<Boolean> maxColorEnabled = sgSpecialColors.add(new BoolSetting.Builder()
        .name("max-hunger-color")
        .description("Uses a distinct color whenever hunger is at/above the threshold below, overriding Fill Mode above at that point.")
        .defaultValue(false)
        .build()
    );

    private final Setting<ThresholdMode> maxThresholdMode = sgSpecialColors.add(new EnumSetting.Builder<ThresholdMode>()
        .name("max-hunger-threshold-mode")
        .description("Whether the threshold below is a percent of Max Value, or a specific absolute value.")
        .defaultValue(ThresholdMode.Percent)
        .visible(maxColorEnabled::get)
        .build()
    );

    private final Setting<Integer> maxColorThreshold = sgSpecialColors.add(new IntSetting.Builder()
        .name("max-hunger-threshold")
        .description("Hunger percent at/above which the max-hunger color applies. 100 = only exactly full - lower it to trigger a bit before completely full.")
        .defaultValue(100)
        .range(1, 100)
        .sliderRange(1, 100)
        .visible(() -> maxColorEnabled.get() && maxThresholdMode.get() == ThresholdMode.Percent)
        .build()
    );

    private final Setting<Integer> maxThresholdValue = sgSpecialColors.add(new IntSetting.Builder()
        .name("max-hunger-threshold-value")
        .description("Hunger value at/above which the max-hunger color applies, when the mode above is Value instead of Percent.")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 100)
        .visible(() -> maxColorEnabled.get() && maxThresholdMode.get() == ThresholdMode.Value)
        .build()
    );

    private final Setting<SettingColor> maxColor = sgSpecialColors.add(new ColorSetting.Builder()
        .name("max-hunger-color-value")
        .description("Color used while hunger is at/above the threshold, when Max Hunger Color above is on.")
        .defaultValue(new SettingColor(255, 215, 0))
        .visible(maxColorEnabled::get)
        .build()
    );

    private final Setting<LowHungerMode> lowColorMode = sgSpecialColors.add(new EnumSetting.Builder<LowHungerMode>()
        .name("low-hunger-mode")
        .description("Off: no low-hunger indicator. Permanent: a low-hunger indicator (see Low Hunger Style below for Zone vs Line) that's always present in some form, not tied to a one-shot trigger. Flash When Reached: instead briefly flashes the FILL itself to the low-hunger color, only at the moment hunger first drops to/below the threshold, then goes back to normal even if it stays low.")
        .defaultValue(LowHungerMode.Off)
        .build()
    );

    private final Setting<LowIndicatorStyle> lowIndicatorStyle = sgSpecialColors.add(new EnumSetting.Builder<LowIndicatorStyle>()
        .name("low-hunger-style")
        .description("Zone: a colored box drawn ON TOP of the fill, spanning from empty up to the threshold below - always visible at that fixed position (use its own color's alpha if you want it to blend with the fill underneath instead of fully covering it). Line: instead a thin marker line at the threshold position - visible as a warning while hunger is still above it, and disappears the instant hunger actually drops to/below it.")
        .defaultValue(LowIndicatorStyle.Zone)
        .visible(() -> lowColorMode.get() == LowHungerMode.Permanent)
        .build()
    );

    private final Setting<ThresholdMode> lowThresholdMode = sgSpecialColors.add(new EnumSetting.Builder<ThresholdMode>()
        .name("low-hunger-threshold-mode")
        .description("Whether the threshold below is a percent of Max Value, or a specific absolute value.")
        .defaultValue(ThresholdMode.Value)
        .visible(() -> lowColorMode.get() != LowHungerMode.Off)
        .build()
    );

    private final Setting<Integer> lowColorThreshold = sgSpecialColors.add(new IntSetting.Builder()
        .name("low-hunger-threshold")
        .description("Hunger percent the low-hunger indicator uses - Permanent: how much of the bar (from empty) the marked zone covers. Flash When Reached: the percent at/below which the flash triggers.")
        .defaultValue(30)
        .range(0, 100)
        .sliderRange(0, 100)
        .visible(() -> lowColorMode.get() != LowHungerMode.Off && lowThresholdMode.get() == ThresholdMode.Percent)
        .build()
    );

    private final Setting<Integer> lowThresholdValue = sgSpecialColors.add(new IntSetting.Builder()
        .name("low-hunger-threshold-value")
        .description("Hunger value the low-hunger indicator uses, when the mode above is Value instead of Percent.")
        .defaultValue(6)
        .min(0)
        .sliderRange(0, 100)
        .visible(() -> lowColorMode.get() != LowHungerMode.Off && lowThresholdMode.get() == ThresholdMode.Value)
        .build()
    );

    private final Setting<SettingColor> lowColor = sgSpecialColors.add(new ColorSetting.Builder()
        .name("low-hunger-color-value")
        .description("Permanent: color of the marked zone. Flash When Reached: color the fill flashes to. Used whenever Low Hunger Mode above isn't Off.")
        .defaultValue(new SettingColor(255, 0, 0))
        .visible(() -> lowColorMode.get() != LowHungerMode.Off)
        .build()
    );

    private final Setting<Integer> lowColorFlashDuration = sgSpecialColors.add(new IntSetting.Builder()
        .name("low-hunger-flash-duration")
        .description("How many ticks the flash lasts, when Low Hunger Mode above is Flash When Reached.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 100)
        .visible(() -> lowColorMode.get() == LowHungerMode.FlashWhenReached)
        .build()
    );

    private final Setting<Integer> lowZoneMarkerThickness = sgSpecialColors.add(new IntSetting.Builder()
        .name("low-hunger-marker-thickness")
        .description("Thickness of the low-hunger marker line, in pixels (before Scale), when Low Hunger Style above is Line.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 10)
        .visible(() -> lowColorMode.get() == LowHungerMode.Permanent && lowIndicatorStyle.get() == LowIndicatorStyle.Line)
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
        .description("Color at full hunger - blends toward Mid Value Color below as hunger drops.")
        .defaultValue(new SettingColor(140, 100, 20))
        .visible(() -> backgroundMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> backgroundMidColor = sgBackgroundByValue.add(new ColorSetting.Builder()
        .name("background-mid-value-color")
        .description("Color at half hunger - blends between Background High Value Color above and Background Low Value Color below.")
        .defaultValue(new SettingColor(120, 62, 22))
        .visible(() -> backgroundMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> backgroundLowColor = sgBackgroundByValue.add(new ColorSetting.Builder()
        .name("background-low-value-color")
        .description("Color at zero hunger - Background Mid Value Color above blends toward this as hunger drops.")
        .defaultValue(new SettingColor(100, 25, 25))
        .visible(() -> backgroundMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<List<ColorRangeEntry>> backgroundRanges = sgBackgroundByValue.add(new ColorRangeListSetting.Builder()
        .name("background-value-ranges")
        .description("Each band's own hunger-point cutoff and color - see Edit Ranges below.")
        .visible(() -> backgroundMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Void> backgroundRangesEdit = sgBackgroundByValue.add(new ButtonSetting.Builder()
        .name("background-value-ranges-edit")
        .description("Add/edit/remove the hunger-point bands above.")
        .buttonText("Edit Ranges")
        .screen(theme -> new ColorRangeListScreen(theme, "Background", backgroundRanges.get(), 20))
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
        .defaultValue(new SettingColor(210, 160, 50))
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
        .description("Color at full hunger - blends toward Mid Value Color below as hunger drops.")
        .defaultValue(new SettingColor(255, 200, 40))
        .visible(() -> fillMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> fillMidColor = sgFillByValue.add(new ColorSetting.Builder()
        .name("fill-mid-value-color")
        .description("Color at half hunger - blends between Fill High Value Color above and Fill Low Value Color below.")
        .defaultValue(new SettingColor(237, 115, 35))
        .visible(() -> fillMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> fillLowColor = sgFillByValue.add(new ColorSetting.Builder()
        .name("fill-low-value-color")
        .description("Color at zero hunger - Fill Mid Value Color above blends toward this as hunger drops.")
        .defaultValue(new SettingColor(220, 30, 30))
        .visible(() -> fillMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<List<ColorRangeEntry>> fillRanges = sgFillByValue.add(new ColorRangeListSetting.Builder()
        .name("fill-value-ranges")
        .description("Each band's own hunger-point cutoff and color - see Edit Ranges below.")
        .visible(() -> fillMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Void> fillRangesEdit = sgFillByValue.add(new ButtonSetting.Builder()
        .name("fill-value-ranges-edit")
        .description("Add/edit/remove the hunger-point bands above.")
        .buttonText("Edit Ranges")
        .screen(theme -> new ColorRangeListScreen(theme, "Fill", fillRanges.get(), 20))
        .visible(() -> fillMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    // Text Color (Rainbow)

    private final Setting<RainbowTransition> textRainbowTransition = sgTextRainbow.add(new EnumSetting.Builder<RainbowTransition>()
        .name("text-rainbow-transition")
        .description("Soft smoothly cycles hue. Hard jumps between a fixed number of steps.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> textMode.get() == ColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> textRainbowSteps = sgTextRainbow.add(new IntSetting.Builder()
        .name("text-rainbow-steps")
        .description("Number of distinct colors, when Text Rainbow Transition above is Hard.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 24)
        .visible(() -> textMode.get() == ColorMode.Rainbow && textRainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> textRainbowSpeed = sgTextRainbow.add(new DoubleSetting.Builder()
        .name("text-rainbow-speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> textMode.get() == ColorMode.Rainbow)
        .build()
    );

    // Text Color (Gradient / Flashing)

    private final Setting<List<SettingColor>> textGradientColors = sgTextGradient.add(new ColorListSetting.Builder()
        .name("text-gradient-colors")
        .description("Colors the text cycles through, in order.")
        .defaultValue(List.of(new SettingColor(255, 255, 255), new SettingColor(150, 150, 150)))
        .visible(() -> textMode.get() == ColorMode.Gradient)
        .build()
    );

    private final Setting<Double> textGradientSpeed = sgTextGradient.add(new DoubleSetting.Builder()
        .name("text-gradient-speed")
        .description("How fast the gradient cycles.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> textMode.get() == ColorMode.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> textFlashColors = sgTextGradient.add(new TimedColorListSetting.Builder()
        .name("text-flash-colors")
        .description("Colors the text flashes between, each held for its own duration (in ticks).")
        .defaultValue(List.of())
        .visible(() -> textMode.get() == ColorMode.Flashing)
        .build()
    );

    // Text Color (Hue Shift)

    private final Setting<SettingColor> textHueShiftBaseColor = sgTextHueShift.add(new ColorSetting.Builder()
        .name("text-hue-shift-base-color")
        .description("Starting color - its hue continuously swings, keeping its saturation/brightness/alpha.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> textMode.get() == ColorMode.HueShift)
        .build()
    );

    private final Setting<Double> textHueShiftSpeed = sgTextHueShift.add(new DoubleSetting.Builder()
        .name("text-hue-shift-speed")
        .description("How fast the hue swings.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> textMode.get() == ColorMode.HueShift)
        .build()
    );

    private final Setting<Double> textHueShiftRange = sgTextHueShift.add(new DoubleSetting.Builder()
        .name("text-hue-shift-range")
        .description("How far the hue swings from the base color, in degrees each direction. 180 = swings the full way around.")
        .defaultValue(30)
        .min(0)
        .sliderRange(0, 180)
        .visible(() -> textMode.get() == ColorMode.HueShift)
        .build()
    );

    // Text Color (By Value)

    private final Setting<SettingColor> textHighColor = sgTextByValue.add(new ColorSetting.Builder()
        .name("text-high-value-color")
        .description("Color at full hunger - blends toward Mid Value Color below as hunger drops.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> textMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> textMidColor = sgTextByValue.add(new ColorSetting.Builder()
        .name("text-mid-value-color")
        .description("Color at half hunger - blends between Text High Value Color above and Text Low Value Color below.")
        .defaultValue(new SettingColor(237, 142, 142))
        .visible(() -> textMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> textLowColor = sgTextByValue.add(new ColorSetting.Builder()
        .name("text-low-value-color")
        .description("Color at zero hunger - Text Mid Value Color above blends toward this as hunger drops.")
        .defaultValue(new SettingColor(220, 30, 30))
        .visible(() -> textMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<List<ColorRangeEntry>> textRanges = sgTextByValue.add(new ColorRangeListSetting.Builder()
        .name("text-value-ranges")
        .description("Each band's own hunger-point cutoff and color - see Edit Ranges below.")
        .visible(() -> textMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Void> textRangesEdit = sgTextByValue.add(new ButtonSetting.Builder()
        .name("text-value-ranges-edit")
        .description("Add/edit/remove the hunger-point bands above.")
        .buttonText("Edit Ranges")
        .screen(theme -> new ColorRangeListScreen(theme, "Text", textRanges.get(), 20))
        .visible(() -> textMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    // Border Color (Rainbow)

    private final Setting<RainbowTransition> borderRainbowTransition = sgBorderRainbow.add(new EnumSetting.Builder<RainbowTransition>()
        .name("border-rainbow-transition")
        .description("Soft smoothly cycles hue. Hard jumps between a fixed number of steps.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> borderRainbowSteps = sgBorderRainbow.add(new IntSetting.Builder()
        .name("border-rainbow-steps")
        .description("Number of distinct colors, when Border Rainbow Transition above is Hard.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 24)
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.Rainbow && borderRainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> borderRainbowSpeed = sgBorderRainbow.add(new DoubleSetting.Builder()
        .name("border-rainbow-speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.Rainbow)
        .build()
    );

    // Border Color (Gradient / Flashing)

    private final Setting<List<SettingColor>> borderGradientColors = sgBorderGradient.add(new ColorListSetting.Builder()
        .name("border-gradient-colors")
        .description("Colors the outline cycles through, in order.")
        .defaultValue(List.of(new SettingColor(255, 255, 255), new SettingColor(150, 150, 150)))
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.Gradient)
        .build()
    );

    private final Setting<Double> borderGradientSpeed = sgBorderGradient.add(new DoubleSetting.Builder()
        .name("border-gradient-speed")
        .description("How fast the gradient cycles.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> borderFlashColors = sgBorderGradient.add(new TimedColorListSetting.Builder()
        .name("border-flash-colors")
        .description("Colors the outline flashes between, each held for its own duration (in ticks).")
        .defaultValue(List.of())
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.Flashing)
        .build()
    );

    // Border Color (Hue Shift)

    private final Setting<SettingColor> borderHueShiftBaseColor = sgBorderHueShift.add(new ColorSetting.Builder()
        .name("border-hue-shift-base-color")
        .description("Starting color - its hue continuously swings, keeping its saturation/brightness/alpha.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.HueShift)
        .build()
    );

    private final Setting<Double> borderHueShiftSpeed = sgBorderHueShift.add(new DoubleSetting.Builder()
        .name("border-hue-shift-speed")
        .description("How fast the hue swings.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.HueShift)
        .build()
    );

    private final Setting<Double> borderHueShiftRange = sgBorderHueShift.add(new DoubleSetting.Builder()
        .name("border-hue-shift-range")
        .description("How far the hue swings from the base color, in degrees each direction. 180 = swings the full way around.")
        .defaultValue(30)
        .min(0)
        .sliderRange(0, 180)
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.HueShift)
        .build()
    );

    // Border Color (By Value)

    private final Setting<SettingColor> borderHighColor = sgBorderByValue.add(new ColorSetting.Builder()
        .name("border-high-value-color")
        .description("Color at full hunger - blends toward Mid Value Color below as hunger drops.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> borderMidColor = sgBorderByValue.add(new ColorSetting.Builder()
        .name("border-mid-value-color")
        .description("Color at half hunger - blends between Border High Value Color above and Border Low Value Color below.")
        .defaultValue(new SettingColor(237, 142, 142))
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> borderLowColor = sgBorderByValue.add(new ColorSetting.Builder()
        .name("border-low-value-color")
        .description("Color at zero hunger - Border Mid Value Color above blends toward this as hunger drops.")
        .defaultValue(new SettingColor(220, 30, 30))
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<List<ColorRangeEntry>> borderRanges = sgBorderByValue.add(new ColorRangeListSetting.Builder()
        .name("border-value-ranges")
        .description("Each band's own hunger-point cutoff and color - see Edit Ranges below.")
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Void> borderRangesEdit = sgBorderByValue.add(new ButtonSetting.Builder()
        .name("border-value-ranges-edit")
        .description("Add/edit/remove the hunger-point bands above.")
        .buttonText("Edit Ranges")
        .screen(theme -> new ColorRangeListScreen(theme, "Border", borderRanges.get(), 20))
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Boolean> rotateText = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate-text")
        .description("Rotates the current/max text along with the bar (per Rotation above) instead of always staying upright.")
        .defaultValue(false)
        .build()
    );

    private float smoothedProgress = -1;
    private long lastSmoothNanos = -1;

    private boolean wasAboveLowThreshold = true;
    private int lowFlashTicksRemaining = 0;

    public StaminaBarHud() {
        super(INFO);
        calculateSize();

        MeteorClient.EVENT_BUS.subscribe(this);
    }

    // Border is drawn OUTSIDE the bar's own configured Width/Height (see drawBorderRing) rather than
    // the box growing to make room for it - growing the box broke anchoring for Left/Top-anchored
    // bars (the common case): growing size only extends a box AWAY from its anchored corner, so the
    // bar's actual fill/background visibly shifted toward that corner every time the border toggled
    // on. Keeping the box size fixed at exactly Width/Height regardless of border settings means
    // toggling/resizing the border never moves or resizes the bar's own content at all.
    private void calculateSize() {
        setSize(width.get() * scaleX.get() * scale.get(), height.get() * scaleY.get() * scale.get());
    }

    @Override
    public int getEditorRotation() {
        return rotation.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.player == null || lowColorMode.get() != LowHungerMode.FlashWhenReached) return;

        float progress = rangeValue(mc.player.getHungerManager().getFoodLevel()) / (float) rangeMax();
        boolean above = progress > lowThresholdFraction();

        if (!above && wasAboveLowThreshold) lowFlashTicksRemaining = lowColorFlashDuration.get();
        wasAboveLowThreshold = above;

        if (lowFlashTicksRemaining > 0) lowFlashTicksRemaining--;
    }

    /** Vanilla stops sprinting at or below 6 food (3 full hunger bars) - Sprint Range treats that as the bar's empty point. */
    private static final int SPRINT_FOOD_LIMIT = 6;

    private int rangeFloor() {
        return sprintRange.get() ? Math.min(SPRINT_FOOD_LIMIT, maxValue.get() - 1) : 0;
    }

    private int rangeMax() {
        return maxValue.get() - rangeFloor();
    }

    private int rangeValue(int foodLevel) {
        return Math.max(0, foodLevel - rangeFloor());
    }

    /** Low/Max Hunger Color's threshold, as a 0-1 fraction of Max Value - either a direct percent, or an absolute value divided by Max Value. */
    private double lowThresholdFraction() {
        return lowThresholdMode.get() == ThresholdMode.Percent ? lowColorThreshold.get() / 100.0 : rangeValue(lowThresholdValue.get()) / (double) rangeMax();
    }

    private double maxThresholdFraction() {
        return maxThresholdMode.get() == ThresholdMode.Percent ? maxColorThreshold.get() / 100.0 : rangeValue(maxThresholdValue.get()) / (double) rangeMax();
    }

    // Rotates a rectangle (given in local coords relative to the box's own top-left) around the
    // box's own center by this element's Rotation setting, then draws it as two triangles - same
    // technique as HudEditorScreen's rotated selection box. Used for both the background and fill
    // rectangles so they rotate together as one rigid bar.
    private void drawRotatedQuad(HudRenderer renderer, double localX, double localY, double localW, double localH, Color color) {
        drawRotatedQuad(renderer, localX, localY, localW, localH, color, 0);
    }

    // shading (0-1): darkens the shape from top (unchanged) to bottom (darkest), independent of
    // rotation - see shadeColor below.
    private void drawRotatedQuad(HudRenderer renderer, double localX, double localY, double localW, double localH, Color color, double shading) {
        int deg = rotation.get();
        Color topColor = shading > 0 ? shadeColor(color, 0, shading) : color;
        Color bottomColor = shading > 0 ? shadeColor(color, 1, shading) : color;

        if (deg == 0) {
            if (shading > 0) renderer.quad(x + localX, y + localY, localW, localH, topColor, topColor, bottomColor, bottomColor);
            else renderer.quad(x + localX, y + localY, localW, localH, color);
            return;
        }

        double centerX = x + getWidth() / 2.0;
        double centerY = y + getHeight() / 2.0;
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad), sin = Math.sin(rad);

        double[] lx = {x + localX - centerX, x + localX - centerX, x + localX + localW - centerX, x + localX + localW - centerX};
        double[] ly = {y + localY - centerY, y + localY + localH - centerY, y + localY + localH - centerY, y + localY - centerY};
        double[] cx = new double[4];
        double[] cy = new double[4];

        for (int i = 0; i < 4; i++) {
            cx[i] = centerX + lx[i] * cos - ly[i] * sin;
            cy[i] = centerY + lx[i] * sin + ly[i] * cos;
        }

        // Point order from lx/ly above: 0=top-left, 1=bottom-left, 2=bottom-right, 3=top-right.
        Color c0 = topColor, c1 = bottomColor, c2 = bottomColor, c3 = topColor;

        Renderer2D.COLOR.triangle(cx[0], cy[0], c0, cx[1], cy[1], c1, cx[2], cy[2], c2);
        Renderer2D.COLOR.triangle(cx[0], cy[0], c0, cx[2], cy[2], c2, cx[3], cy[3], c3);
    }

    // Multiplies color's RGB by (1 - strength*t), t=0 at the shape's own top edge and t=1 at its
    // own bottom edge - a simple top-darker-at-bottom shade, independent of rotation (t is always
    // measured in the shape's own LOCAL, pre-rotation space).
    private Color shadeColor(Color color, double t, double strength) {
        double factor = 1.0 - strength * MathHelper.clamp(t, 0, 1);
        return new Color(
            (int) MathHelper.clamp(color.r * factor, 0, 255),
            (int) MathHelper.clamp(color.g * factor, 0, 255),
            (int) MathHelper.clamp(color.b * factor, 0, 255),
            color.a
        );
    }

    private static final int CORNER_SEGMENTS = 6;

    private double effectiveRadius() {
        return pillShape.get() ? getHeight() / 2.0 : cornerRadius.get() * scale.get();
    }

    private void drawRoundedRect(HudRenderer renderer, double localX, double localY, double localW, double localH, double radius, boolean roundTL, boolean roundTR, boolean roundBR, boolean roundBL, Color color) {
        drawRoundedRect(renderer, localX, localY, localW, localH, radius, roundTL, roundTR, roundBR, roundBL, color, 0);
    }

    // A rectangle with up to 4 independently roundable corners, rotated around the box's own center
    // like drawRotatedQuad above, filled via a triangle fan from that same center (always valid
    // since the shape is convex). Used for both the full pill/rounded background (all 4 corners
    // rounded) and the fill (only the corner(s) on the bar's real end rounded, per Flip - the cut
    // edge where the fill stops stays a plain straight line, so no stencil/scissor clip is needed to
    // reveal it correctly as the fill grows). shading (0-1): see shadeColor above - applied to each
    // boundary point by its own LOCAL Y (before rotation), so the shading always reads as "top of
    // the shape" to "bottom of the shape" regardless of Rotation.
    private void drawRoundedRect(HudRenderer renderer, double localX, double localY, double localW, double localH, double radius, boolean roundTL, boolean roundTR, boolean roundBR, boolean roundBL, Color color, double shading) {
        radius = Math.min(radius, Math.min(localW, localH) / 2.0);

        if (radius < 0.5) {
            drawRotatedQuad(renderer, localX, localY, localW, localH, color, shading);
            return;
        }

        List<double[]> pts = new ArrayList<>();
        addArcOrCorner(pts, 0, 0, radius, radius, radius, 180, 270, roundTL);
        addArcOrCorner(pts, localW, 0, localW - radius, radius, radius, 270, 360, roundTR);
        addArcOrCorner(pts, localW, localH, localW - radius, localH - radius, radius, 0, 90, roundBR);
        addArcOrCorner(pts, 0, localH, radius, localH - radius, radius, 90, 180, roundBL);

        int deg = rotation.get();
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double centerX = x + getWidth() / 2.0;
        double centerY = y + getHeight() / 2.0;

        double[][] p = new double[pts.size()][2];
        for (int i = 0; i < pts.size(); i++) {
            double lx = x + localX + pts.get(i)[0] - centerX;
            double ly = y + localY + pts.get(i)[1] - centerY;
            p[i][0] = centerX + lx * cos - ly * sin;
            p[i][1] = centerY + lx * sin + ly * cos;
        }

        // Fan origin: this SHAPE's own center (not the whole bar's center) - a fan triangulation
        // only tiles a convex polygon correctly when its origin actually lies inside that polygon,
        // and the bar's overall center can fall well outside a narrow/off-center fill rectangle
        // (e.g. at low hunger, or the low-hunger zone piece), garbling the rounded corners there
        // even though the full-width background (whose own center always coincides with the bar's)
        // never showed the bug. Rotated the same way as the boundary points above.
        double flx = x + localX + localW / 2.0 - centerX;
        double fly = y + localY + localH / 2.0 - centerY;
        double fanX = centerX + flx * cos - fly * sin;
        double fanY = centerY + flx * sin + fly * cos;

        double safeH = Math.max(localH, 0.0001);
        Color fanColor = shading > 0 ? shadeColor(color, 0.5, shading) : color;

        for (int i = 0; i < p.length; i++) {
            int next = (i + 1) % p.length;

            if (shading > 0) {
                Color c1 = shadeColor(color, pts.get(i)[1] / safeH, shading);
                Color c2 = shadeColor(color, pts.get(next)[1] / safeH, shading);
                Renderer2D.COLOR.triangle(fanX, fanY, fanColor, p[i][0], p[i][1], c1, p[next][0], p[next][1], c2);
            }
            else renderer.triangle(fanX, fanY, p[i][0], p[i][1], p[next][0], p[next][1], color);
        }
    }

    // rounded: appends an arc of points from fromDeg to toDeg around (arcCx, arcCy). Not rounded:
    // appends just the plain box corner point (cornerX, cornerY) instead of an arc.
    private void addArcOrCorner(List<double[]> pts, double cornerX, double cornerY, double arcCx, double arcCy, double radius, double fromDeg, double toDeg, boolean rounded) {
        if (!rounded) {
            pts.add(new double[]{cornerX, cornerY});
            return;
        }

        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            double t = fromDeg + (toDeg - fromDeg) * i / (double) CORNER_SEGMENTS;
            double rad = Math.toRadians(t);
            pts.add(new double[]{arcCx + Math.cos(rad) * radius, arcCy + Math.sin(rad) * radius});
        }
    }

    // Draws the border as an actual ring between an outer and inner rounded-rect boundary, drawn
    // OUTSIDE the bar's own bounds (negative offset / beyond width,height) rather than inset within
    // them - purely decorative overscan that doesn't affect the box's own anchored size/position, so
    // toggling/resizing the border never moves or resizes the bar's actual background/fill (see
    // calculateSize()). Also not a filled rect sitting underneath the background/fill (which is what
    // this used to do before that) - a full solid rect there bleeds the border color through any
    // translucency in whatever's drawn on top of it, visibly tinting the whole bar instead of
    // staying confined to a thin outline. Outer and inner boundaries are sampled with identical
    // angular segments (always via the arc path, even at radius 0, which just collapses to repeated
    // corner points) so they always pair up 1:1 into a clean quad strip regardless of how small the
    // radius/thickness actually are.
    private void drawBorderRing(HudRenderer renderer, double innerRadius, double thickness, Color color) {
        double w = getWidth(), h = getHeight();
        double outerRadius = innerRadius + thickness;

        List<double[]> outerPts = new ArrayList<>();
        addArcOrCorner(outerPts, -thickness, -thickness, -thickness + outerRadius, -thickness + outerRadius, outerRadius, 180, 270, true);
        addArcOrCorner(outerPts, w + thickness, -thickness, w + thickness - outerRadius, -thickness + outerRadius, outerRadius, 270, 360, true);
        addArcOrCorner(outerPts, w + thickness, h + thickness, w + thickness - outerRadius, h + thickness - outerRadius, outerRadius, 0, 90, true);
        addArcOrCorner(outerPts, -thickness, h + thickness, -thickness + outerRadius, h + thickness - outerRadius, outerRadius, 90, 180, true);

        List<double[]> innerPts = new ArrayList<>();
        addArcOrCorner(innerPts, 0, 0, innerRadius, innerRadius, innerRadius, 180, 270, true);
        addArcOrCorner(innerPts, w, 0, w - innerRadius, innerRadius, innerRadius, 270, 360, true);
        addArcOrCorner(innerPts, w, h, w - innerRadius, h - innerRadius, innerRadius, 0, 90, true);
        addArcOrCorner(innerPts, 0, h, innerRadius, h - innerRadius, innerRadius, 90, 180, true);

        int n = outerPts.size();

        int deg = rotation.get();
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double centerX = x + w / 2.0;
        double centerY = y + h / 2.0;

        double[][] op = new double[n][2];
        double[][] ip = new double[n][2];

        for (int i = 0; i < n; i++) {
            double olx = x + outerPts.get(i)[0] - centerX;
            double oly = y + outerPts.get(i)[1] - centerY;
            op[i][0] = centerX + olx * cos - oly * sin;
            op[i][1] = centerY + olx * sin + oly * cos;

            double ilx = x + innerPts.get(i)[0] - centerX;
            double ily = y + innerPts.get(i)[1] - centerY;
            ip[i][0] = centerX + ilx * cos - ily * sin;
            ip[i][1] = centerY + ilx * sin + ily * cos;
        }

        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            renderer.triangle(op[i][0], op[i][1], op[next][0], op[next][1], ip[next][0], ip[next][1], color);
            renderer.triangle(op[i][0], op[i][1], ip[next][0], ip[next][1], ip[i][0], ip[i][1], color);
        }
    }

    // Sutherland-Hodgman clip of a convex polygon (local, pre-rotation coordinates) against the
    // vertical half-plane x <= xClip (keepLeft) or x >= xClip (!keepLeft). Clipping a convex
    // polygon by a half-plane always yields another convex polygon.
    private List<double[]> clipPolygonVertical(List<double[]> pts, double xClip, boolean keepLeft) {
        List<double[]> result = new ArrayList<>();
        int n = pts.size();

        for (int i = 0; i < n; i++) {
            double[] curr = pts.get(i);
            double[] prev = pts.get((i - 1 + n) % n);

            boolean currIn = keepLeft ? curr[0] <= xClip : curr[0] >= xClip;
            boolean prevIn = keepLeft ? prev[0] <= xClip : prev[0] >= xClip;

            if (currIn != prevIn) {
                double t = (xClip - prev[0]) / (curr[0] - prev[0]);
                result.add(new double[]{xClip, prev[1] + t * (curr[1] - prev[1])});
            }

            if (currIn) result.add(curr);
        }

        return result;
    }

    // Draws the fill as an exact slice of the bar's own FULL rounded-rect boundary (localW/localH -
    // the bar's real content size, not the fill's own narrower width), cut at fillW along its own
    // local X axis, in whichever direction Flip grows from. Slicing the REAL boundary this way (via
    // clipPolygonVertical above) instead of picking between a flat-cut shape and a fully-rounded
    // one at some "is it basically full" threshold means the cut edge always exactly follows
    // whatever the underlying rounded shape actually looks like there - flat where it's flat, a
    // partial arc the instant the cut enters the corner's curve, gradually completing into the
    // corner's full original arc as the fill keeps growing past it - so there's no sudden jump
    // between "square" and "round" anywhere, including right around 100%.
    private void drawFillSlice(HudRenderer renderer, double localX, double localY, double localW, double localH, double radius, double fillW, boolean flipped, Color color, double shading) {
        if (fillW <= 0) return;

        radius = Math.min(radius, Math.min(localW, localH) / 2.0);

        List<double[]> pts;
        if (radius < 0.5) {
            pts = new ArrayList<>(List.of(
                new double[]{0, 0}, new double[]{localW, 0},
                new double[]{localW, localH}, new double[]{0, localH}
            ));
        }
        else {
            pts = new ArrayList<>();
            addArcOrCorner(pts, 0, 0, radius, radius, radius, 180, 270, true);
            addArcOrCorner(pts, localW, 0, localW - radius, radius, radius, 270, 360, true);
            addArcOrCorner(pts, localW, localH, localW - radius, localH - radius, radius, 0, 90, true);
            addArcOrCorner(pts, 0, localH, radius, localH - radius, radius, 90, 180, true);
        }

        double xClip = flipped ? localW - fillW : fillW;
        List<double[]> clipped = clipPolygonVertical(pts, xClip, !flipped);
        if (clipped.size() < 3) return;

        int deg = rotation.get();
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double centerX = x + getWidth() / 2.0;
        double centerY = y + getHeight() / 2.0;

        int n = clipped.size();
        double[][] p = new double[n][2];
        double sumX = 0, sumY = 0;

        for (int i = 0; i < n; i++) {
            double localPX = clipped.get(i)[0];
            double localPY = clipped.get(i)[1];
            sumX += localPX;
            sumY += localPY;

            double lx = x + localX + localPX - centerX;
            double ly = y + localY + localPY - centerY;
            p[i][0] = centerX + lx * cos - ly * sin;
            p[i][1] = centerY + lx * sin + ly * cos;
        }

        // Fan origin: average of the clipped polygon's own vertices - a convex combination of a
        // convex polygon's vertices always lies inside it, so this is always a valid fan origin
        // regardless of how the clip reshaped the boundary.
        double fanLocalX = sumX / n;
        double fanLocalY = sumY / n;
        double flx = x + localX + fanLocalX - centerX;
        double fly = y + localY + fanLocalY - centerY;
        double fanX = centerX + flx * cos - fly * sin;
        double fanY = centerY + flx * sin + fly * cos;

        double safeH = Math.max(localH, 0.0001);
        Color fanColor = shading > 0 ? shadeColor(color, MathHelper.clamp(fanLocalY / safeH, 0, 1), shading) : color;

        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;

            if (shading > 0) {
                Color c1 = shadeColor(color, clipped.get(i)[1] / safeH, shading);
                Color c2 = shadeColor(color, clipped.get(next)[1] / safeH, shading);
                Renderer2D.COLOR.triangle(fanX, fanY, fanColor, p[i][0], p[i][1], c1, p[next][0], p[next][1], c2);
            }
            else renderer.triangle(fanX, fanY, p[i][0], p[i][1], p[next][0], p[next][1], color);
        }
    }

    private float getRenderProgress(float target) {
        if (!smoothFill.get()) {
            smoothedProgress = target;
            lastSmoothNanos = -1;
            return target;
        }

        long now = System.nanoTime();
        if (smoothedProgress < 0 || lastSmoothNanos < 0) {
            smoothedProgress = target;
        } else {
            float dt = (now - lastSmoothNanos) / 1_000_000_000f;
            float t = 1f - (float) Math.exp(-smoothSpeed.get() * dt);
            smoothedProgress += (target - smoothedProgress) * t;
        }
        lastSmoothNanos = now;

        return smoothedProgress;
    }

    /** progress: current/max hunger, 0-1 - used by By Value mode below (a smooth blend, unlike the hard-cutoff Max/Low Hunger Color, which only applies to Fill). */
    private Color getBackgroundColor(float progress) {
        return switch (backgroundMode.get()) {
            case Fixed -> backgroundColor.get();
            case Rainbow -> rainbowColor(backgroundRainbowTransition.get(), backgroundRainbowSteps.get(), backgroundRainbowSpeed.get());
            case Gradient -> gradientColor(backgroundGradientColors.get(), backgroundGradientSpeed.get());
            case Flashing -> flashColor(backgroundFlashColors.get(), backgroundColor.get());
            case HueShift -> hueShiftColor(backgroundHueShiftBaseColor.get(), backgroundHueShiftSpeed.get(), backgroundHueShiftRange.get());
            case ByValue -> byValueColor(backgroundHighColor.get(), backgroundMidColor.get(), backgroundLowColor.get(), progress);
            case ByValueRainbow -> byValueRainbowColor(progress);
            case ByValueRanges -> rangesColor(backgroundRanges.get());
        };
    }

    /** progress: current/max hunger, 0-1 - used for the Max/Low hunger color overrides below (hard cutoffs), which take priority over Fill Mode, and for By Value mode (a smooth blend across the whole range instead). Permanent's own low-hunger indicator is a marked zone on the background instead of a fill override - see render(). */
    private Color getFillColor(float progress) {
        Color statusColor = statusColors.get();
        if (statusColor != null) return statusColor;

        if (maxColorEnabled.get() && progress >= maxThresholdFraction()) return maxColor.get();

        if (lowColorMode.get() == LowHungerMode.FlashWhenReached && lowFlashTicksRemaining > 0) return lowColor.get();

        return switch (fillMode.get()) {
            case Fixed -> fillColor.get();
            case Rainbow -> rainbowColor(fillRainbowTransition.get(), fillRainbowSteps.get(), fillRainbowSpeed.get());
            case Gradient -> gradientColor(fillGradientColors.get(), fillGradientSpeed.get());
            case Flashing -> flashColor(fillFlashColors.get(), fillColor.get());
            case HueShift -> hueShiftColor(fillHueShiftBaseColor.get(), fillHueShiftSpeed.get(), fillHueShiftRange.get());
            case ByValue -> byValueColor(fillHighColor.get(), fillMidColor.get(), fillLowColor.get(), progress);
            case ByValueRainbow -> byValueRainbowColor(progress);
            case ByValueRanges -> rangesColor(fillRanges.get());
        };
    }

    /** progress: current/max hunger, 0-1. */
    private Color getTextColor(float progress) {
        return switch (textMode.get()) {
            case Fixed -> textColor.get();
            case Rainbow -> rainbowColor(textRainbowTransition.get(), textRainbowSteps.get(), textRainbowSpeed.get());
            case Gradient -> gradientColor(textGradientColors.get(), textGradientSpeed.get());
            case Flashing -> flashColor(textFlashColors.get(), textColor.get());
            case HueShift -> hueShiftColor(textHueShiftBaseColor.get(), textHueShiftSpeed.get(), textHueShiftRange.get());
            case ByValue -> byValueColor(textHighColor.get(), textMidColor.get(), textLowColor.get(), progress);
            case ByValueRainbow -> byValueRainbowColor(progress);
            case ByValueRanges -> rangesColor(textRanges.get());
        };
    }

    /** progress: current/max hunger, 0-1. */
    private Color getBorderColor(float progress) {
        return switch (borderMode.get()) {
            case Fixed -> borderColor.get();
            case Rainbow -> rainbowColor(borderRainbowTransition.get(), borderRainbowSteps.get(), borderRainbowSpeed.get());
            case Gradient -> gradientColor(borderGradientColors.get(), borderGradientSpeed.get());
            case Flashing -> flashColor(borderFlashColors.get(), borderColor.get());
            case HueShift -> hueShiftColor(borderHueShiftBaseColor.get(), borderHueShiftSpeed.get(), borderHueShiftRange.get());
            case ByValue -> byValueColor(borderHighColor.get(), borderMidColor.get(), borderLowColor.get(), progress);
            case ByValueRainbow -> byValueRainbowColor(progress);
            case ByValueRanges -> rangesColor(borderRanges.get());
        };
    }

    /** By Value Ranges - a hard, un-blended color per user-defined band of real hunger points. */
    private Color rangesColor(List<ColorRangeEntry> ranges) {
        double food = mc.player != null ? mc.player.getHungerManager().getFoodLevel() : 0;
        return ColorRangeEntry.resolve(ranges, food, Color.WHITE);
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

    @Override
    public void render(HudRenderer renderer) {
        int current, max = maxValue.get();

        if (isInEditor()) current = 15;
        else if (mc.player == null) current = 0;
        else current = mc.player.getHungerManager().getFoodLevel();

        current = rangeValue(current);
        max = rangeMax();

        float target = MathHelper.clamp(current / (float) max, 0, 1);
        float progress = getRenderProgress(target);

        double w = getWidth();
        double h = getHeight();

        if (displayMode.get() != DisplayMode.Numbers) {
            double radius = effectiveRadius();
            boolean f = !flip.get();

            double borderT = showBorder.get() ? borderThickness.get() * scale.get() : 0;
            if (borderT > 0) drawBorderRing(renderer, radius, borderT, getBorderColor(target));

            double bx = 0, by = 0;
            double bw = w, bh = h;
            double insetRadius = radius;
            // Clamped once here, against the FULL bar's own dimensions, so the background and fill
            // always share the exact same radius (drawRoundedRect's own internal clamp, against
            // whatever localW/localH IT was individually called with, would otherwise silently give
            // the fill a SMALLER radius than the background any time the fill itself is narrower
            // than the bar - i.e. most of the time below ~100% - making it look progressively less
            // rounded than the background instead of matching it, even though both were configured
            // with the same Corner Radius). Only actually still shrinks further, inside that per-
            // call clamp, in the genuinely unavoidable case where the fill's own width is smaller
            // than this radius.
            insetRadius = Math.min(insetRadius, Math.min(bw, bh) / 2.0);

            drawRoundedRect(renderer, bx, by, bw, bh, insetRadius, true, true, true, true, getBackgroundColor(target));

            if (progress > 0) {
                double fillW = bw * progress;
                drawFillSlice(renderer, bx, by, bw, bh, insetRadius, fillW, f, getFillColor(target), fillShading.get() ? fillShadingStrength.get() : 0);
            }

            // Permanent indicator - both styles draw ON TOP of the fill (not hidden behind it),
            // fixed at the low-threshold position: Zone a filled box, Line a thin marker. Zone stays
            // visible at all times (use its own color's alpha if you want it to blend with the fill
            // underneath rather than fully cover it); Line only shows while hunger is still above
            // the threshold and disappears once it actually drops to/below it, since by then you're
            // already in the low zone and don't need the warning.
            if (lowColorMode.get() == LowHungerMode.Permanent) {
                double thresholdFraction = lowThresholdFraction();
                double zoneW = bw * thresholdFraction;

                if (lowIndicatorStyle.get() == LowIndicatorStyle.Zone) {
                    if (!f) drawRoundedRect(renderer, bx, by, zoneW, bh, insetRadius, true, false, false, true, lowColor.get());
                    else drawRoundedRect(renderer, bx + bw - zoneW, by, zoneW, bh, insetRadius, false, true, true, false, lowColor.get());
                }
                else if (progress > thresholdFraction) {
                    double markerThickness = Math.max(1, lowZoneMarkerThickness.get() * scale.get());
                    double markerLocalX = f ? bx + bw - zoneW - markerThickness / 2.0 : bx + zoneW - markerThickness / 2.0;
                    markerLocalX = MathHelper.clamp(markerLocalX, bx, bx + bw - markerThickness);

                    drawRotatedQuad(renderer, markerLocalX, by, markerThickness, bh, lowColor.get());
                }
            }
        }

        if (displayMode.get() != DisplayMode.Bar) {
            String text = current + "/" + max;
            double tx = x + w / 2.0 - renderer.textWidth(text) / 2.0;
            double ty = y + h / 2.0 - renderer.textHeight() / 2.0;
            Color color = getTextColor(target);

            if (rotateText.get() && rotation.get() != 0) {
                VanillaTextRenderer.INSTANCE.rotation = rotation.get();
                VanillaTextRenderer.INSTANCE.rotationPivotX = x + w / 2.0;
                VanillaTextRenderer.INSTANCE.rotationPivotY = y + h / 2.0;

                renderer.text(text, tx, ty, color, true);

                VanillaTextRenderer.INSTANCE.rotation = 0;
            }
            else renderer.text(text, tx, ty, color, true);
        }
    }

    /** "By Value Rainbow": the whole rainbow mapped onto the value - red at 0 sweeping through to violet at full. */
    private Color byValueRainbowColor(float progress) {
        return Color.fromHsv(MathHelper.clamp(progress, 0f, 1f) * 300.0, 1, 1);
    }
}
