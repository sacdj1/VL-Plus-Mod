/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.gui.screens.settings.ColorRangeListScreen;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.misc.VLPlusAdditions;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Some servers repurpose the vanilla XP bar to show an ability cooldown instead of real XP. On
 * this server it runs backwards from vanilla XP: empty (0) means the ability is ready, full (1)
 * means the cooldown was just applied, draining back down to empty as it recharges. The time
 * estimate is derived from the drain rate over a short recent window (not the whole cycle since
 * reset), so it stays accurate even if the drain rate isn't perfectly constant - there's no way to
 * know the server's actual cooldown duration directly, so this is the closest estimate without
 * hardcoding any specific ability's timing.
 */
public class AbilityCooldownHud extends HudElement {

    @Override
    public boolean hasOwnBackground() {
        return true;
    }
    public static final HudElementInfo<AbilityCooldownHud> INFO = new HudElementInfo<>(Hud.GROUP, "ability-cooldown", "Tracks an ability cooldown via the XP bar, with a color gradient and a percent or ETA readout.", AbilityCooldownHud::new);

    private static final long RATE_WINDOW_MS = 1500;
    private static final long SAMPLE_INTERVAL_MS = 50;

    public enum DisplayMode {
        Percent,
        Time
    }

    public enum SubSecondUnit {
        Milliseconds,
        Ticks
    }

    public enum TextColorMode {
        White,
        Black,
        MatchBar,
        InvertBar,
        ByValue,
        ByValueRanges
    }

    public enum OutlineColor {
        None,
        Black,
        White
    }

    public enum ReadyStyle {
        Static,
        Gradient,
        Flashing
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

    public enum SurfaceColorMode {
        Fixed,
        Cooldown,
        Rainbow,
        Gradient,
        Flashing,
        HueShift
    }

    private record Sample(long timestamp, float progress) {
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgBackground = settings.createGroup("Background");
    private final SettingGroup sgBackgroundRainbow = settings.createGroup("Background Color (Rainbow)");
    private final SettingGroup sgBackgroundGradient = settings.createGroup("Background Color (Gradient / Flashing)");
    private final SettingGroup sgBackgroundHueShift = settings.createGroup("Background Color (Hue Shift)");
    private final SettingGroup sgFill = settings.createGroup("Fill");
    private final SettingGroup sgFillRainbow = settings.createGroup("Fill Color (Rainbow)");
    private final SettingGroup sgFillGradient = settings.createGroup("Fill Color (Gradient / Flashing)");
    private final SettingGroup sgFillHueShift = settings.createGroup("Fill Color (Hue Shift)");
    private final SettingGroup sgBorder = settings.createGroup("Border");
    private final SettingGroup sgBorderRainbow = settings.createGroup("Border Color (Rainbow)");
    private final SettingGroup sgBorderGradient = settings.createGroup("Border Color (Gradient / Flashing)");
    private final SettingGroup sgBorderHueShift = settings.createGroup("Border Color (Hue Shift)");
    private final SettingGroup sgBorderByValue = settings.createGroup("Border Color (By Value)");
    private final SettingGroup sgTextByValue = settings.createGroup("Text Color (By Value)");

    // General

    private final Setting<DisplayMode> displayMode = sgGeneral.add(new EnumSetting.Builder<DisplayMode>()
        .name("display-mode")
        .description("Show a readiness percentage, or an estimated time until ready.")
        .defaultValue(DisplayMode.Percent)
        .build()
    );

    private final Setting<Boolean> invertPercentText = sgGeneral.add(new BoolSetting.Builder()
        .name("invert-percent-text")
        .description("Counts down from 100% to 0% as it becomes ready, instead of up from 0% to 100%.")
        .defaultValue(false)
        .visible(() -> displayMode.get() == DisplayMode.Percent)
        .build()
    );

    private final Setting<SubSecondUnit> subSecondUnit = sgGeneral.add(new EnumSetting.Builder<SubSecondUnit>()
        .name("sub-second-unit")
        .description("Once under a second remains, show that time in milliseconds or game ticks instead of rounding to whole seconds.")
        .defaultValue(SubSecondUnit.Milliseconds)
        .visible(() -> displayMode.get() == DisplayMode.Time)
        .build()
    );

    private final Setting<Boolean> padSubSecond = sgGeneral.add(new BoolSetting.Builder()
        .name("pad-sub-second")
        .description("Pads the sub-second time to a fixed width (e.g. \"042ms\" instead of \"42ms\"), so the text doesn't visibly shift left/right as the digit count changes.")
        .defaultValue(true)
        .visible(() -> displayMode.get() == DisplayMode.Time)
        .build()
    );

    private final Setting<Integer> roundSubSecondTo = sgGeneral.add(new IntSetting.Builder()
        .name("round-sub-second-to")
        .description("Rounds the sub-second time to the nearest this-many milliseconds, so it updates in visible steps instead of flickering every frame. 1 = no rounding.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 100)
        .visible(() -> displayMode.get() == DisplayMode.Time)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Overall size multiplier for the whole element - scales width and height together instead of adjusting them separately.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 5)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Double> barWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("width")
        .description("Base width of the bar, before scale is applied.")
        .defaultValue(100)
        .min(10)
        .sliderRange(10, 300)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Double> barHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("height")
        .description("Base height of the bar, before scale is applied.")
        .defaultValue(12)
        .min(4)
        .sliderRange(4, 40)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Boolean> showBar = sgGeneral.add(new BoolSetting.Builder()
        .name("show-bar")
        .description("Displays the bar itself. Turn off to only show the text.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fullBarWhenReady = sgGeneral.add(new BoolSetting.Builder()
        .name("full-bar-when-ready")
        .description("Shows the bar fully filled (using the ready style/color below) once ready, instead of empty/invisible - much clearer at a glance than an empty bar meaning \"ready\".")
        .defaultValue(true)
        .visible(showBar::get)
        .build()
    );

    private final Setting<Boolean> invertProgress = VLPlusAdditions.markExperimental(sgGeneral.add(new BoolSetting.Builder()
        .name("invert-progress")
        .description("By default (off) the bar starts at nothing right when the cooldown is applied and fills up to full as it becomes ready - matching the XP Bar module's own default, and the common \"charging up\" convention. On: it's full right when applied and drains down to nothing as it becomes ready instead. Affects color mapping too (see Invert Colors below), not just the fill amount - flip Invert Colors along with this if you invert it and the colors end up on the wrong ends.")
        .defaultValue(true)
        .visible(showBar::get)
        .build()
    ));

    private final Setting<Boolean> invertFillDirection = VLPlusAdditions.markExperimental(sgGeneral.add(new BoolSetting.Builder()
        .name("invert-fill-direction")
        .description("Reported behaving the same as Invert Progress above on at least one setup - not yet root-caused, so treat this as unreliable for now. Fills the bar from the right edge growing left, instead of the left edge growing right.")
        .defaultValue(true)
        .visible(showBar::get)
        .build()
    ));

    private final Setting<Boolean> smoothFill = sgGeneral.add(new BoolSetting.Builder()
        .name("smooth-fill")
        .description("Interpolates the bar's fill/color smoothly toward its real value over time, instead of snapping instantly whenever the server syncs a new value - most servers only update this in discrete steps rather than every frame. Doesn't affect the ETA estimate or Ready state, which both need the real, unsmoothed value.")
        .defaultValue(false)
        .visible(showBar::get)
        .build()
    );

    private final Setting<Double> smoothSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("smooth-speed")
        .description("How fast the displayed fill catches up to the real value. Higher catches up faster/snappier, lower lags more/smoother.")
        .defaultValue(15)
        .min(0.1)
        .sliderRange(1, 60)
        .visible(() -> showBar.get() && smoothFill.get())
        .build()
    );

    private final Setting<Boolean> hideWhenReady = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-when-ready")
        .description("Hides this element entirely once the ability has been ready for a while, instead of leaving a full/ready-colored bar on screen indefinitely. Still shows while positioning it in the HUD editor.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hideAfterTicks = sgGeneral.add(new IntSetting.Builder()
        .name("hide-after-ticks")
        .description("How many game ticks the ability must have been ready for before this element hides.")
        .defaultValue(40)
        .min(1)
        .sliderRange(1, 200)
        .visible(hideWhenReady::get)
        .build()
    );

    private final Setting<TextColorMode> textColorMode = sgGeneral.add(new EnumSetting.Builder<TextColorMode>()
        .name("text-color-mode")
        .description("White/Black: fixed color, adjustable below (despite the mode's own name) - not actually locked to pure white/black. Match Bar: same color the bar currently is - only really readable with Show Bar off, since otherwise the text matches its own background. Invert Bar: the visual opposite of the bar's current color, which stays readable either way. By Value: blends between two colors of its own based on readiness, independent of the bar's own colors.")
        .defaultValue(TextColorMode.White)
        .build()
    );

    private final Setting<SettingColor> textColorWhiteMode = sgGeneral.add(new ColorSetting.Builder()
        .name("text-color-white")
        .description("The actual color used by Text Color Mode's \"White\" option - still called White for the default, but freely adjustable.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> textColorMode.get() == TextColorMode.White)
        .build()
    );

    private final Setting<SettingColor> textColorBlackMode = sgGeneral.add(new ColorSetting.Builder()
        .name("text-color-black")
        .description("The actual color used by Text Color Mode's \"Black\" option - still called Black for the default, but freely adjustable.")
        .defaultValue(new SettingColor(0, 0, 0))
        .visible(() -> textColorMode.get() == TextColorMode.Black)
        .build()
    );

    private final Setting<SettingColor> textHighColor = sgTextByValue.add(new ColorSetting.Builder()
        .name("text-ready-color")
        .description("Text color while ready - blends toward Text Just Used Color below as the cooldown fills.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> textColorMode.get() == TextColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> textLowColor = sgTextByValue.add(new ColorSetting.Builder()
        .name("text-just-used-color")
        .description("Text color right when the cooldown is applied - Text Ready Color above blends toward this as it fills.")
        .defaultValue(new SettingColor(220, 30, 30))
        .visible(() -> textColorMode.get() == TextColorMode.ByValue)
        .build()
    );

    private final Setting<List<ColorRangeEntry>> textRanges = sgTextByValue.add(new ColorRangeListSetting.Builder()
        .name("text-value-ranges")
        .description("Each band's own readiness-percent cutoff (0 = just used, 100 = ready) and color - a hard un-blended color per band instead of By Value's smooth blend. See Edit Ranges below.")
        .visible(() -> textColorMode.get() == TextColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Void> textRangesEdit = sgTextByValue.add(new ButtonSetting.Builder()
        .name("text-value-ranges-edit")
        .description("Add/edit/remove the readiness-percent bands above.")
        .buttonText("Edit Ranges")
        .screen(theme -> new ColorRangeListScreen(theme, "Text", textRanges.get(), 100))
        .visible(() -> textColorMode.get() == TextColorMode.ByValueRanges)
        .build()
    );

    private final Setting<OutlineColor> textOutline = sgGeneral.add(new EnumSetting.Builder<OutlineColor>()
        .name("text-outline")
        .description("Draws a solid outline around the text in the chosen color (both freely adjustable below, despite being called Black/White), replacing the normal drop shadow. None disables it.")
        .defaultValue(OutlineColor.None)
        .build()
    );

    private final Setting<SettingColor> textOutlineWhiteColor = sgGeneral.add(new ColorSetting.Builder()
        .name("text-outline-white")
        .description("The actual color used by Text Outline's \"White\" option - still called White for the default, but freely adjustable.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> textOutline.get() == OutlineColor.White)
        .build()
    );

    private final Setting<SettingColor> textOutlineBlackColor = sgGeneral.add(new ColorSetting.Builder()
        .name("text-outline-black")
        .description("The actual color used by Text Outline's \"Black\" option - still called Black for the default, but freely adjustable.")
        .defaultValue(new SettingColor(0, 0, 0))
        .visible(() -> textOutline.get() == OutlineColor.Black)
        .build()
    );

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale")
        .description("Size multiplier for the percent/time text, independent of the bar's own width/height above.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 5)
        .build()
    );

    private final Setting<Boolean> pillShape = sgGeneral.add(new BoolSetting.Builder()
        .name("pill-shape")
        .description("Fully rounds the bar's ends into a pill/stadium shape, regardless of Corner Radius below.")
        .defaultValue(false)
        .visible(showBar::get)
        .build()
    );

    private final Setting<Integer> cornerRadius = sgGeneral.add(new IntSetting.Builder()
        .name("corner-radius")
        .description("How rounded the bar's corners are, in pixels (before Scale). Automatically capped to half the bar's height/width, so it can never overshoot into a pill shape on its own - use Pill Shape above for that.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 20)
        .visible(() -> showBar.get() && !pillShape.get())
        .build()
    );

    private final Setting<Integer> rotation = sgGeneral.add(new IntSetting.Builder()
        .name("rotation")
        .description("Rotates the whole bar around its own center, in degrees clockwise. The percent/time text stays upright regardless, unless Rotate Text below is on.")
        .defaultValue(0)
        .range(-359, 359)
        .sliderRange(-359, 359)
        .visible(showBar::get)
        .build()
    );

    private final Setting<Boolean> rotateText = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate-text")
        .description("Rotates the percent/time text along with the bar (per Rotation above) instead of always staying upright.")
        .defaultValue(false)
        .visible(() -> showBar.get() && rotation.get() != 0)
        .build()
    );

    private final Setting<Boolean> fillShading = sgGeneral.add(new BoolSetting.Builder()
        .name("fill-shading")
        .description("Darkens the fill smoothly from top to bottom, for a simple glossy/embossed look - applies on top of whatever color the fill already has.")
        .defaultValue(false)
        .visible(showBar::get)
        .build()
    );

    private final Setting<Double> fillShadingStrength = sgGeneral.add(new DoubleSetting.Builder()
        .name("fill-shading-strength")
        .description("How much darker the bottom of the fill gets, relative to the top. 0 = no darkening, 1 = fades to black.")
        .defaultValue(0.4)
        .range(0, 1)
        .sliderRange(0, 1)
        .visible(() -> showBar.get() && fillShading.get())
        .build()
    );

    // Colors

    private final Setting<SettingColor> readyColor = sgColors.add(new ColorSetting.Builder()
        .name("ready-color")
        .description("Color when the ability is ready (bar empty). Also the draining gradient's endpoint, regardless of ready style below.")
        .defaultValue(new SettingColor(40, 255, 40))
        .build()
    );

    private final Setting<ReadyStyle> readyStyle = sgColors.add(new EnumSetting.Builder<ReadyStyle>()
        .name("ready-style")
        .description("How the ready state itself looks once the bar reaches empty. Static just uses Ready Color. Gradient smoothly cycles through Ready Colors. Flashing hard-switches between Ready Colors every Flash Ticks.")
        .defaultValue(ReadyStyle.Static)
        .build()
    );

    private final Setting<List<SettingColor>> readyColors = sgColors.add(new ColorListSetting.Builder()
        .name("ready-colors")
        .description("Colors to cycle between when Ready Style is Gradient. Needs at least 2.")
        .defaultValue(List.of(new SettingColor(40, 255, 40), new SettingColor(40, 220, 255)))
        .visible(() -> readyStyle.get() == ReadyStyle.Gradient)
        .build()
    );

    private final Setting<Double> readyGradientSpeed = sgColors.add(new DoubleSetting.Builder()
        .name("ready-gradient-speed")
        .description("How fast Ready Colors cycles, in Gradient style.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> readyStyle.get() == ReadyStyle.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> flashColors = sgColors.add(new TimedColorListSetting.Builder()
        .name("flash-colors")
        .description("Colors to hard-switch between when Ready Style is Flashing, each with its own hold duration in game ticks. Needs at least 2.")
        .defaultValue(List.of(new TimedColorEntry(new SettingColor(40, 255, 40), 10), new TimedColorEntry(new SettingColor(40, 220, 255), 10)))
        .visible(() -> readyStyle.get() == ReadyStyle.Flashing)
        .build()
    );

    private final Setting<Boolean> emptinessGradient = sgColors.add(new BoolSetting.Builder()
        .name("emptiness-gradient")
        .description("Smoothly blends from Just Used Color toward Ready Color as the cooldown drains, whenever a surface below is using Cooldown color mode. When off, it just shows Just Used Color flat until it's fully empty, then switches straight to the ready effect - no in-between.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> midColor = sgColors.add(new ColorSetting.Builder()
        .name("mid-color")
        .description("Color at half cooldown.")
        .defaultValue(new SettingColor(255, 165, 0))
        .visible(emptinessGradient::get)
        .build()
    );

    private final Setting<SettingColor> justUsedColor = sgColors.add(new ColorSetting.Builder()
        .name("just-used-color")
        .description("Color right after the cooldown is applied (bar full).")
        .defaultValue(new SettingColor(255, 40, 40))
        .build()
    );

    private final Setting<SettingColor> endColor = sgColors.add(new ColorSetting.Builder()
        .name("end-color")
        .description("Color the bar holds right as it approaches ready, before hard-switching to Ready Style/Color the instant it actually reaches ready - a distinct \"almost there\" color instead of gradually blending straight into the ready effect. Same idea as the XP Bar module's own End Color.")
        .defaultValue(new SettingColor(40, 255, 40))
        .visible(emptinessGradient::get)
        .build()
    );

    // Background

    private final Setting<Boolean> background = sgBackground.add(new BoolSetting.Builder()
        .name("background")
        .description("Displays a background behind the bar.")
        .defaultValue(true)
        .visible(showBar::get)
        .build()
    );

    private final Setting<SurfaceColorMode> backgroundMode = sgBackground.add(new EnumSetting.Builder<SurfaceColorMode>()
        .name("background-color-mode")
        .description("How the background picks its color. Fixed: Background Color below, unchanging. Cooldown: the ready/mid/just-used gradient above, reacting to cooldown state - same as Fill's own Cooldown mode, but its own independent instance. Rainbow/Gradient/Flashing/HueShift: its own animated color, completely independent of cooldown, with its own dedicated Background Color (...) groups below.")
        .defaultValue(SurfaceColorMode.Fixed)
        .visible(() -> showBar.get() && background.get())
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgBackground.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color used for the background, when Background Color Mode above is Fixed.")
        .defaultValue(new SettingColor(25, 25, 25, 150))
        .visible(() -> showBar.get() && background.get() && backgroundMode.get() == SurfaceColorMode.Fixed)
        .build()
    );

    // Background Color (Rainbow)

    private final Setting<RainbowTransition> backgroundRainbowTransition = sgBackgroundRainbow.add(new EnumSetting.Builder<RainbowTransition>()
        .name("background-rainbow-transition")
        .description("Soft smoothly cycles hue. Hard jumps between a fixed number of steps.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> background.get() && backgroundMode.get() == SurfaceColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> backgroundRainbowSteps = sgBackgroundRainbow.add(new IntSetting.Builder()
        .name("background-rainbow-steps")
        .description("Number of distinct colors, when Background Rainbow Transition above is Hard.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 24)
        .visible(() -> background.get() && backgroundMode.get() == SurfaceColorMode.Rainbow && backgroundRainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> backgroundRainbowSpeed = sgBackgroundRainbow.add(new DoubleSetting.Builder()
        .name("background-rainbow-speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> background.get() && backgroundMode.get() == SurfaceColorMode.Rainbow)
        .build()
    );

    // Background Color (Gradient / Flashing)

    private final Setting<List<SettingColor>> backgroundGradientColors = sgBackgroundGradient.add(new ColorListSetting.Builder()
        .name("background-gradient-colors")
        .description("Colors the background cycles through, in order.")
        .defaultValue(List.of(new SettingColor(25, 25, 25), new SettingColor(60, 60, 60)))
        .visible(() -> background.get() && backgroundMode.get() == SurfaceColorMode.Gradient)
        .build()
    );

    private final Setting<Double> backgroundGradientSpeed = sgBackgroundGradient.add(new DoubleSetting.Builder()
        .name("background-gradient-speed")
        .description("How fast the gradient cycles.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> background.get() && backgroundMode.get() == SurfaceColorMode.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> backgroundFlashColors = sgBackgroundGradient.add(new TimedColorListSetting.Builder()
        .name("background-flash-colors")
        .description("Colors the background flashes between, each held for its own duration (in ticks).")
        .defaultValue(List.of())
        .visible(() -> background.get() && backgroundMode.get() == SurfaceColorMode.Flashing)
        .build()
    );

    // Background Color (Hue Shift)

    private final Setting<SettingColor> backgroundHueShiftBaseColor = sgBackgroundHueShift.add(new ColorSetting.Builder()
        .name("background-hue-shift-base-color")
        .description("Starting color - its hue continuously swings, keeping its saturation/brightness/alpha.")
        .defaultValue(new SettingColor(25, 25, 25))
        .visible(() -> background.get() && backgroundMode.get() == SurfaceColorMode.HueShift)
        .build()
    );

    private final Setting<Double> backgroundHueShiftSpeed = sgBackgroundHueShift.add(new DoubleSetting.Builder()
        .name("background-hue-shift-speed")
        .description("How fast the hue swings.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> background.get() && backgroundMode.get() == SurfaceColorMode.HueShift)
        .build()
    );

    private final Setting<Double> backgroundHueShiftRange = sgBackgroundHueShift.add(new DoubleSetting.Builder()
        .name("background-hue-shift-range")
        .description("How far the hue swings from the base color, in degrees each direction. 180 = swings the full way around.")
        .defaultValue(30)
        .min(0)
        .sliderRange(0, 180)
        .visible(() -> background.get() && backgroundMode.get() == SurfaceColorMode.HueShift)
        .build()
    );

    // Fill

    private final Setting<SurfaceColorMode> fillMode = sgFill.add(new EnumSetting.Builder<SurfaceColorMode>()
        .name("fill-color-mode")
        .description("How the fill picks its color. Fixed: Fill Color below, unchanging. Cooldown: the ready/mid/just-used gradient above, reacting to cooldown state (the original/only behavior this had before). Rainbow/Gradient/Flashing/HueShift: its own animated color, completely independent of cooldown, with its own dedicated Fill Color (...) groups below.")
        .defaultValue(SurfaceColorMode.Cooldown)
        .visible(showBar::get)
        .build()
    );

    private final Setting<SettingColor> fillColor = sgFill.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("Color used for the fill, when Fill Color Mode above is Fixed.")
        .defaultValue(new SettingColor(40, 255, 40))
        .visible(() -> showBar.get() && fillMode.get() == SurfaceColorMode.Fixed)
        .build()
    );

    // Fill Color (Rainbow)

    private final Setting<RainbowTransition> fillRainbowTransition = sgFillRainbow.add(new EnumSetting.Builder<RainbowTransition>()
        .name("fill-rainbow-transition")
        .description("Soft smoothly cycles hue. Hard jumps between a fixed number of steps.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> fillMode.get() == SurfaceColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> fillRainbowSteps = sgFillRainbow.add(new IntSetting.Builder()
        .name("fill-rainbow-steps")
        .description("Number of distinct colors, when Fill Rainbow Transition above is Hard.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 24)
        .visible(() -> fillMode.get() == SurfaceColorMode.Rainbow && fillRainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> fillRainbowSpeed = sgFillRainbow.add(new DoubleSetting.Builder()
        .name("fill-rainbow-speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> fillMode.get() == SurfaceColorMode.Rainbow)
        .build()
    );

    // Fill Color (Gradient / Flashing)

    private final Setting<List<SettingColor>> fillGradientColors = sgFillGradient.add(new ColorListSetting.Builder()
        .name("fill-gradient-colors")
        .description("Colors the fill cycles through, in order.")
        .defaultValue(List.of(new SettingColor(40, 255, 40), new SettingColor(40, 220, 255)))
        .visible(() -> fillMode.get() == SurfaceColorMode.Gradient)
        .build()
    );

    private final Setting<Double> fillGradientSpeed = sgFillGradient.add(new DoubleSetting.Builder()
        .name("fill-gradient-speed")
        .description("How fast the gradient cycles.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> fillMode.get() == SurfaceColorMode.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> fillFlashColors = sgFillGradient.add(new TimedColorListSetting.Builder()
        .name("fill-flash-colors")
        .description("Colors the fill flashes between, each held for its own duration (in ticks).")
        .defaultValue(List.of())
        .visible(() -> fillMode.get() == SurfaceColorMode.Flashing)
        .build()
    );

    // Fill Color (Hue Shift)

    private final Setting<SettingColor> fillHueShiftBaseColor = sgFillHueShift.add(new ColorSetting.Builder()
        .name("fill-hue-shift-base-color")
        .description("Starting color - its hue continuously swings, keeping its saturation/brightness/alpha.")
        .defaultValue(new SettingColor(40, 255, 40))
        .visible(() -> fillMode.get() == SurfaceColorMode.HueShift)
        .build()
    );

    private final Setting<Double> fillHueShiftSpeed = sgFillHueShift.add(new DoubleSetting.Builder()
        .name("fill-hue-shift-speed")
        .description("How fast the hue swings.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> fillMode.get() == SurfaceColorMode.HueShift)
        .build()
    );

    private final Setting<Double> fillHueShiftRange = sgFillHueShift.add(new DoubleSetting.Builder()
        .name("fill-hue-shift-range")
        .description("How far the hue swings from the base color, in degrees each direction. 180 = swings the full way around.")
        .defaultValue(30)
        .min(0)
        .sliderRange(0, 180)
        .visible(() -> fillMode.get() == SurfaceColorMode.HueShift)
        .build()
    );

    // Border

    private final Setting<Boolean> showBorder = sgBorder.add(new BoolSetting.Builder()
        .name("show-border")
        .description("Draws an outline around the whole bar, matching its own rounded/pill shape - purely additive padding, grows the element's total size rather than shrinking the bar itself to make room.")
        .defaultValue(false)
        .visible(showBar::get)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<ColorMode> borderMode = sgBorder.add(new EnumSetting.Builder<ColorMode>()
        .name("border-mode")
        .description("How the outline picks its color.")
        .defaultValue(ColorMode.Fixed)
        .visible(() -> showBar.get() && showBorder.get())
        .build()
    );

    private final Setting<SettingColor> borderColor = sgBorder.add(new ColorSetting.Builder()
        .name("border-color")
        .description("Color of the outline, when Border Mode above is Fixed.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> showBar.get() && showBorder.get() && borderMode.get() == ColorMode.Fixed)
        .build()
    );

    private final Setting<Double> borderThickness = sgBorder.add(new DoubleSetting.Builder()
        .name("border-thickness")
        .description("Thickness of the outline, in pixels (before Scale).")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .decimalPlaces(1)
        .visible(() -> showBar.get() && showBorder.get())
        .onChanged(v -> calculateSize())
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
        .name("border-ready-color")
        .description("Color while ready - blends toward Just Used Color below as the cooldown fills.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> borderLowColor = sgBorderByValue.add(new ColorSetting.Builder()
        .name("border-just-used-color")
        .description("Color right when the cooldown is applied - Border Ready Color above blends toward this as it fills.")
        .defaultValue(new SettingColor(220, 30, 30))
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<List<ColorRangeEntry>> borderRanges = sgBorderByValue.add(new ColorRangeListSetting.Builder()
        .name("border-value-ranges")
        .description("Each band's own readiness-percent cutoff (0 = just used, 100 = ready) and color - a hard un-blended color per band instead of By Value's smooth blend. See Edit Ranges below.")
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Void> borderRangesEdit = sgBorderByValue.add(new ButtonSetting.Builder()
        .name("border-value-ranges-edit")
        .description("Add/edit/remove the readiness-percent bands above.")
        .buttonText("Edit Ranges")
        .screen(theme -> new ColorRangeListScreen(theme, "Border", borderRanges.get(), 100))
        .visible(() -> showBorder.get() && borderMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    // Recent-window rate tracking, for the time-estimate display mode
    private final Deque<Sample> recentSamples = new ArrayDeque<>();
    private long lastSampleTime = -1;
    private float lastProgress = -1;
    private long readySinceMs = -1;
    private float smoothedProgress = -1;
    private long lastSmoothNanos = -1;

    /** Same exponential-approach technique as XPBarAdjust's own getRenderProgress. */
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

    public AbilityCooldownHud() {
        super(INFO);

        calculateSize();
    }

    // Border is drawn OUTSIDE the bar's own configured Bar Width/Height (see drawBorderRing) rather
    // than the box growing to make room for it - growing the box broke anchoring for Left/Top-
    // anchored bars (the common case): growing size only extends a box AWAY from its anchored
    // corner, so the bar's actual fill/background visibly shifted toward that corner every time the
    // border toggled on. Keeping the box size fixed at exactly Bar Width/Height regardless of
    // border settings means toggling/resizing the border never moves or resizes the bar's own
    // content at all.
    private void calculateSize() {
        setSize(barWidth.get() * scale.get(), barHeight.get() * scale.get());
    }

    @Override
    public int getEditorRotation() {
        return rotation.get();
    }

    private static final int CORNER_SEGMENTS = 6;

    private double effectiveRadius() {
        return pillShape.get() ? getHeight() / 2.0 : cornerRadius.get() * scale.get();
    }

    // Rotates a rectangle (given in local coords relative to the box's own top-left) around the
    // box's own center by this element's Rotation setting, then draws it as two triangles - same
    // technique Health/Stamina Bar HUD use for their own background/fill.
    private void drawRotatedQuad(HudRenderer renderer, double localX, double localY, double localW, double localH, Color color) {
        drawRotatedQuad(renderer, localX, localY, localW, localH, color, 0);
    }

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

        Color c0 = topColor, c1 = bottomColor, c2 = bottomColor, c3 = topColor;

        Renderer2D.COLOR.triangle(cx[0], cy[0], c0, cx[1], cy[1], c1, cx[2], cy[2], c2);
        Renderer2D.COLOR.triangle(cx[0], cy[0], c0, cx[2], cy[2], c2, cx[3], cy[3], c3);
    }

    private Color shadeColor(Color color, double t, double strength) {
        double factor = 1.0 - strength * MathHelper.clamp(t, 0, 1);
        return new Color(
            (int) MathHelper.clamp(color.r * factor, 0, 255),
            (int) MathHelper.clamp(color.g * factor, 0, 255),
            (int) MathHelper.clamp(color.b * factor, 0, 255),
            color.a
        );
    }

    private void drawRoundedRect(HudRenderer renderer, double localX, double localY, double localW, double localH, double radius, Color color) {
        radius = Math.min(radius, Math.min(localW, localH) / 2.0);

        if (radius < 0.5) {
            drawRotatedQuad(renderer, localX, localY, localW, localH, color);
            return;
        }

        List<double[]> pts = new ArrayList<>();
        addArcOrCorner(pts, 0, 0, radius, radius, radius, 180, 270);
        addArcOrCorner(pts, localW, 0, localW - radius, radius, radius, 270, 360);
        addArcOrCorner(pts, localW, localH, localW - radius, localH - radius, radius, 0, 90);
        addArcOrCorner(pts, 0, localH, radius, localH - radius, radius, 90, 180);

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

        double flx = x + localX + localW / 2.0 - centerX;
        double fly = y + localY + localH / 2.0 - centerY;
        double fanX = centerX + flx * cos - fly * sin;
        double fanY = centerY + flx * sin + fly * cos;

        for (int i = 0; i < p.length; i++) {
            int next = (i + 1) % p.length;
            renderer.triangle(fanX, fanY, p[i][0], p[i][1], p[next][0], p[next][1], color);
        }
    }

    private void addArcOrCorner(List<double[]> pts, double cornerX, double cornerY, double arcCx, double arcCy, double radius, double fromDeg, double toDeg) {
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
    // calculateSize()). Also not a filled rect sitting underneath the background/fill - a full solid
    // rect there bleeds the border color through any translucency in whatever's drawn on top of it,
    // visibly tinting the whole bar instead of staying confined to a thin outline. Outer and inner
    // boundaries are sampled with identical angular segments (always via the arc path, even at
    // radius 0, which just collapses to repeated corner points) so they always pair up 1:1 into a
    // clean quad strip regardless of how small the radius/thickness actually are.
    private void drawBorderRing(HudRenderer renderer, double innerRadius, double thickness, Color color) {
        double w = getWidth(), h = getHeight();
        double outerRadius = innerRadius + thickness;

        List<double[]> outerPts = new ArrayList<>();
        addArcOrCorner(outerPts, -thickness, -thickness, -thickness + outerRadius, -thickness + outerRadius, outerRadius, 180, 270);
        addArcOrCorner(outerPts, w + thickness, -thickness, w + thickness - outerRadius, -thickness + outerRadius, outerRadius, 270, 360);
        addArcOrCorner(outerPts, w + thickness, h + thickness, w + thickness - outerRadius, h + thickness - outerRadius, outerRadius, 0, 90);
        addArcOrCorner(outerPts, -thickness, h + thickness, -thickness + outerRadius, h + thickness - outerRadius, outerRadius, 90, 180);

        List<double[]> innerPts = new ArrayList<>();
        addArcOrCorner(innerPts, 0, 0, innerRadius, innerRadius, innerRadius, 180, 270);
        addArcOrCorner(innerPts, w, 0, w - innerRadius, innerRadius, innerRadius, 270, 360);
        addArcOrCorner(innerPts, w, h, w - innerRadius, h - innerRadius, innerRadius, 0, 90);
        addArcOrCorner(innerPts, 0, h, innerRadius, h - innerRadius, innerRadius, 90, 180);

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
    // vertical half-plane x <= xClip (keepLeft) or x >= xClip (!keepLeft) - see Health/Stamina Bar
    // HUD's own copy of this for the full explanation of why the fill is sliced this way instead of
    // toggling between a flat-cut and fully-rounded shape.
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
            addArcOrCorner(pts, 0, 0, radius, radius, radius, 180, 270);
            addArcOrCorner(pts, localW, 0, localW - radius, radius, radius, 270, 360);
            addArcOrCorner(pts, localW, localH, localW - radius, localH - radius, radius, 0, 90);
            addArcOrCorner(pts, 0, localH, radius, localH - radius, radius, 90, 180);
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

    private Color getBorderColor(float readiness) {
        return switch (borderMode.get()) {
            case Fixed -> borderColor.get();
            case Rainbow -> borderRainbowColor();
            case Gradient -> borderGradientColor();
            case Flashing -> borderFlashColorValue();
            case HueShift -> borderHueShiftColorValue();
            case ByValue -> borderByValueColor(readiness);
            case ByValueRainbow -> byValueRainbowColor(readiness);
            case ByValueRanges -> ColorRangeEntry.resolve(borderRanges.get(), readiness * 100, Color.WHITE);
        };
    }

    private Color borderRainbowColor() {
        double time = System.currentTimeMillis() / 1000.0 * borderRainbowSpeed.get() * 60.0;

        float hue;
        if (borderRainbowTransition.get() == RainbowTransition.Soft) {
            hue = (float) (time % 360.0);
        } else {
            int steps = borderRainbowSteps.get();
            int step = (int) (time / (360.0 / steps)) % steps;
            hue = step * (360f / steps);
        }

        return Color.fromHsv(hue, 1, 1);
    }

    private Color borderGradientColor() {
        List<SettingColor> colors = borderGradientColors.get();
        if (colors.isEmpty()) return Color.WHITE;
        if (colors.size() == 1) return colors.get(0);

        double time = (System.currentTimeMillis() / 1000.0 * borderGradientSpeed.get()) % colors.size();
        int index = (int) time;
        float t = (float) (time - index);

        return lerp(colors.get(index), colors.get((index + 1) % colors.size()), t);
    }

    private Color borderFlashColorValue() {
        List<TimedColorEntry> entries = borderFlashColors.get();
        if (entries.isEmpty()) return borderColor.get();
        if (entries.size() == 1) return entries.get(0).color;

        long totalTicks = 0;
        for (TimedColorEntry entry : entries) totalTicks += Math.max(1, entry.ticks);

        long tick = System.currentTimeMillis() / 50;
        long pos = tick % totalTicks;

        long accumulated = 0;
        for (TimedColorEntry entry : entries) {
            accumulated += Math.max(1, entry.ticks);
            if (pos < accumulated) return entry.color;
        }

        return entries.get(entries.size() - 1).color;
    }

    private Color borderHueShiftColorValue() {
        SettingColor base = borderHueShiftBaseColor.get();
        float[] hsb = java.awt.Color.RGBtoHSB(base.r, base.g, base.b, null);

        double time = System.currentTimeMillis() / 1000.0 * borderHueShiftSpeed.get();
        double offset = Math.sin(time) * borderHueShiftRange.get();
        float hue = (float) (((hsb[0] * 360.0 + offset) % 360.0 + 360.0) % 360.0);

        Color color = Color.fromHsv(hue, hsb[1], hsb[2]);
        color.a = base.a;
        return color;
    }

    /** readiness: 0 = just used, 1 = ready. */
    private Color textByValueColor(float readiness) {
        float t = MathHelper.clamp(readiness, 0f, 1f);
        SettingColor low = textLowColor.get();
        SettingColor high = textHighColor.get();

        return new Color(
            (int) (low.r + (high.r - low.r) * t),
            (int) (low.g + (high.g - low.g) * t),
            (int) (low.b + (high.b - low.b) * t),
            (int) (low.a + (high.a - low.a) * t)
        );
    }

    /** readiness: 0 = just used, 1 = ready. */
    private Color borderByValueColor(float readiness) {
        float t = MathHelper.clamp(readiness, 0f, 1f);
        SettingColor low = borderLowColor.get();
        SettingColor high = borderHighColor.get();

        return new Color(
            (int) (low.r + (high.r - low.r) * t),
            (int) (low.g + (high.g - low.g) * t),
            (int) (low.b + (high.b - low.b) * t),
            (int) (low.a + (high.a - low.a) * t)
        );
    }

    @Override
    public void render(HudRenderer renderer) {
        // Raw, uninverted progress - drives sample tracking and the ETA estimate below, which both
        // need to reflect the real rate of change regardless of how Invert Progress displays it.
        float progress = mc.player != null ? MathHelper.clamp(mc.player.experienceProgress, 0, 1) : 0;
        long now = System.currentTimeMillis();

        // A visible jump upward means the cooldown was just (re)applied - drop the old window so
        // the estimate doesn't briefly mix pre- and post-reset samples.
        if (lastProgress >= 0 && progress > lastProgress + 0.001f) recentSamples.clear();
        lastProgress = progress;

        if (lastSampleTime == -1 || now - lastSampleTime >= SAMPLE_INTERVAL_MS) {
            recentSamples.addLast(new Sample(now, progress));
            lastSampleTime = now;
        }
        while (!recentSamples.isEmpty() && now - recentSamples.peekFirst().timestamp() > RATE_WINDOW_MS) recentSamples.pollFirst();

        boolean ready = progress <= 0.001f;

        if (ready) {
            if (readySinceMs == -1) readySinceMs = now;
        } else {
            readySinceMs = -1;
        }

        if (hideWhenReady.get() && !isInEditor() && readySinceMs != -1 && now - readySinceMs >= hideAfterTicks.get() * 50L) {
            return;
        }

        // Display progress - what color/fill amount actually show, separate from the raw value
        // tracked above (ready state, sample tracking, and the ETA text below all keep using the
        // real, unsmoothed progress - only the bar/color's own visual catch-up is smoothed).
        float renderProgress = getRenderProgress(progress);
        float displayProgress = invertProgress.get() ? 1f - renderProgress : renderProgress;

        Color color = getFillColorValue(ready, displayProgress);

        if (showBar.get()) {
            double radius = effectiveRadius();
            boolean flipped = invertFillDirection.get();

            double borderT = showBorder.get() ? borderThickness.get() * scale.get() : 0;
            if (borderT > 0) drawBorderRing(renderer, radius, borderT, getBorderColor(1 - displayProgress));

            double bx = 0, by = 0;
            double bw = getWidth(), bh = getHeight();
            double insetRadius = Math.min(radius, Math.min(bw, bh) / 2.0);

            if (background.get()) drawRoundedRect(renderer, bx, by, bw, bh, insetRadius, getBackgroundColorValue(ready, displayProgress));

            double fillWidth = (ready && fullBarWhenReady.get()) ? bw : bw * displayProgress;
            if (fillWidth > 0) drawFillSlice(renderer, bx, by, bw, bh, insetRadius, fillWidth, flipped, color, fillShading.get() ? fillShadingStrength.get() : 0);
        }

        String text = displayMode.get() == DisplayMode.Percent
            ? Math.round((invertPercentText.get() ? progress : 1 - progress) * 100) + "%"
            : timeEstimate(progress);
        double textWidth = renderer.textWidth(text, textScale.get());
        double textHeight = renderer.textHeight(false, textScale.get());

        Color textColor = switch (textColorMode.get()) {
            case White -> textColorWhiteMode.get();
            case Black -> textColorBlackMode.get();
            case MatchBar -> color;
            case InvertBar -> new Color(255 - color.r, 255 - color.g, 255 - color.b, color.a);
            case ByValue -> textByValueColor(1 - progress);
            case ByValueRanges -> ColorRangeEntry.resolve(textRanges.get(), (1 - progress) * 100, Color.WHITE);
        };

        double tx = x + getWidth() / 2.0 - textWidth / 2.0;
        double ty = y + getHeight() / 2.0 - textHeight / 2.0;

        if (rotateText.get() && rotation.get() != 0) {
            VanillaTextRenderer.INSTANCE.rotation = rotation.get();
            VanillaTextRenderer.INSTANCE.rotationPivotX = x + getWidth() / 2.0;
            VanillaTextRenderer.INSTANCE.rotationPivotY = y + getHeight() / 2.0;

            drawText(renderer, text, tx, ty, textColor);

            VanillaTextRenderer.INSTANCE.rotation = 0;
        }
        else drawText(renderer, text, tx, ty, textColor);
    }

    private void drawText(HudRenderer renderer, String text, double x, double y, Color color) {
        OutlineColor outline = textOutline.get();
        double scale = textScale.get();

        if (outline == OutlineColor.None) {
            renderer.text(text, x, y, color, true, scale);
            return;
        }

        Color outlineColor = outline == OutlineColor.Black ? textOutlineBlackColor.get() : textOutlineWhiteColor.get();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                renderer.text(text, x + dx, y + dy, outlineColor, false, scale);
            }
        }

        renderer.text(text, x, y, color, false, scale);
    }

    // Only ever called for the NOT-ready range - real ready (progress <= 0.001) hard-switches to
    // Ready Style/Color at the caller instead (see getFillColorValue/getBackgroundColorValue),
    // same split XPBarAdjust uses between its own Ready Color Mode and this End->Mid->Just Used
    // gradient for everything short of actually being ready.
    private Color gradientColor(float progress) {
        // progress: ~0 = about to be ready, 1 = just used
        // Hardcoded swap (used to be a togglable "Invert Colors" setting, removed for now to cut down
        // on confusing combinations while Invert Progress/Invert Fill Direction's own quirk is
        // unresolved) - paired with Invert Progress defaulting true: Just Used Color shows right when
        // applied (bar empty), End/Ready Color as it fills up to full.
        SettingColor end = justUsedColor.get();
        SettingColor justUsed = endColor.get();

        if (!emptinessGradient.get()) return justUsed;
        if (progress <= 0.5f) return lerp(end, midColor.get(), progress / 0.5f);

        return lerp(midColor.get(), justUsed, (progress - 0.5f) / 0.5f);
    }

    private Color getReadyStyleColor() {
        return switch (readyStyle.get()) {
            case Static -> readyColor.get();
            case Gradient -> cycleGradient(readyColors.get(), readyGradientSpeed.get());
            case Flashing -> flashColor(flashColors.get());
        };
    }

    /** ready/displayProgress: same meaning as in render() - used by Cooldown mode to reuse the existing Ready/gradient logic above. */
    private Color getBackgroundColorValue(boolean ready, float displayProgress) {
        return switch (backgroundMode.get()) {
            case Fixed -> backgroundColor.get();
            case Cooldown -> ready ? getReadyStyleColor() : gradientColor(displayProgress);
            case Rainbow -> rainbowColorFor(backgroundRainbowTransition.get(), backgroundRainbowSteps.get(), backgroundRainbowSpeed.get());
            case Gradient -> gradientColorFor(backgroundGradientColors.get(), backgroundGradientSpeed.get());
            case Flashing -> flashColorFor(backgroundFlashColors.get(), backgroundColor.get());
            case HueShift -> hueShiftColorFor(backgroundHueShiftBaseColor.get(), backgroundHueShiftSpeed.get(), backgroundHueShiftRange.get());
        };
    }

    private Color getFillColorValue(boolean ready, float displayProgress) {
        return switch (fillMode.get()) {
            case Fixed -> fillColor.get();
            case Cooldown -> ready ? getReadyStyleColor() : gradientColor(displayProgress);
            case Rainbow -> rainbowColorFor(fillRainbowTransition.get(), fillRainbowSteps.get(), fillRainbowSpeed.get());
            case Gradient -> gradientColorFor(fillGradientColors.get(), fillGradientSpeed.get());
            case Flashing -> flashColorFor(fillFlashColors.get(), fillColor.get());
            case HueShift -> hueShiftColorFor(fillHueShiftBaseColor.get(), fillHueShiftSpeed.get(), fillHueShiftRange.get());
        };
    }

    // Shared math for the independent (non-Cooldown) surface color modes - Background and Fill each
    // bring their own fully independent settings values, so picking the same mode for both does NOT
    // make them animate together unless their speeds/steps/colors happen to match.

    private Color rainbowColorFor(RainbowTransition transition, int steps, double speed) {
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

    private Color gradientColorFor(List<SettingColor> colors, double speed) {
        if (colors.isEmpty()) return Color.WHITE;
        if (colors.size() == 1) return colors.get(0);

        double time = (System.currentTimeMillis() / 1000.0 * speed) % colors.size();
        int index = (int) time;
        float t = (float) (time - index);

        return lerp(colors.get(index), colors.get((index + 1) % colors.size()), t);
    }

    private Color flashColorFor(List<TimedColorEntry> entries, Color fallback) {
        if (entries.isEmpty()) return fallback;
        if (entries.size() == 1) return entries.get(0).color;

        long totalTicks = 0;
        for (TimedColorEntry entry : entries) totalTicks += Math.max(1, entry.ticks);

        long tick = System.currentTimeMillis() / 50;
        long pos = tick % totalTicks;

        long accumulated = 0;
        for (TimedColorEntry entry : entries) {
            accumulated += Math.max(1, entry.ticks);
            if (pos < accumulated) return entry.color;
        }

        return entries.get(entries.size() - 1).color;
    }

    private Color hueShiftColorFor(SettingColor base, double speed, double range) {
        float[] hsb = java.awt.Color.RGBtoHSB(base.r, base.g, base.b, null);

        double time = System.currentTimeMillis() / 1000.0 * speed;
        double offset = Math.sin(time) * range;
        float hue = (float) (((hsb[0] * 360.0 + offset) % 360.0 + 360.0) % 360.0);

        Color color = Color.fromHsv(hue, hsb[1], hsb[2]);
        color.a = base.a;
        return color;
    }

    private Color cycleGradient(List<SettingColor> colors, double speed) {
        if (colors.isEmpty()) return readyColor.get();
        if (colors.size() == 1) return colors.get(0);

        double time = (System.currentTimeMillis() / 1000.0 * speed) % colors.size();
        int index = (int) time;
        float t = (float) (time - index);

        return lerp(colors.get(index), colors.get((index + 1) % colors.size()), t);
    }

    private Color flashColor(List<TimedColorEntry> entries) {
        if (entries.isEmpty()) return readyColor.get();
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

    private String timeEstimate(float progress) {
        if (progress <= 0.001f) return "Ready";
        if (recentSamples.size() < 3) return "...";

        // Least-squares linear fit over every sample in the window, rather than just the oldest
        // and current one - a two-point estimate is very sensitive to exactly which sample happens
        // to be "oldest" as the window slides, and experienceProgress updates in discrete
        // server-synced steps (not smoothly), so that noise otherwise shows up directly in the ETA.
        long baseTime = recentSamples.peekFirst().timestamp();
        int n = recentSamples.size();
        double sumT = 0, sumP = 0, sumTP = 0, sumTT = 0;

        for (Sample s : recentSamples) {
            double t = s.timestamp() - baseTime;
            double p = s.progress();

            sumT += t;
            sumP += p;
            sumTP += t * p;
            sumTT += t * t;
        }

        double denom = n * sumTT - sumT * sumT;
        if (Math.abs(denom) < 1e-9) return "...";

        double slope = (n * sumTP - sumT * sumP) / denom; // progress change per ms
        if (slope >= -1e-9) return "..."; // not draining right now - can't estimate

        double rate = -slope;
        long remainingMs = (long) (progress / rate);

        return formatDuration(remainingMs);
    }

    private String formatDuration(long ms) {
        if (ms < 1000) {
            long roundTo = Math.max(1, roundSubSecondTo.get());
            long rounded = Math.round(ms / (double) roundTo) * roundTo;

            if (rounded < 1000) {
                boolean pad = padSubSecond.get();

                if (subSecondUnit.get() == SubSecondUnit.Ticks) {
                    long ticks = rounded / 50;
                    return pad ? String.format("%02dt", ticks) : ticks + "t";
                }

                return pad ? String.format("%03dms", rounded) : rounded + "ms";
            }

            ms = rounded; // rounded up into the next whole second
        }

        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }

    /** "By Value Rainbow": the whole rainbow mapped onto the value - red at 0 sweeping through to violet at full. */
    private Color byValueRainbowColor(float progress) {
        return Color.fromHsv(MathHelper.clamp(progress, 0f, 1f) * 300.0, 1, 1);
    }
}
