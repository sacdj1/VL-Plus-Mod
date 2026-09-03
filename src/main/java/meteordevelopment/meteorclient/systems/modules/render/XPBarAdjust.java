/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.game.ResourcePacksReloadedEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Recolors the vanilla XP bar itself - useful on servers that repurpose it to show an ability
 * cooldown instead of real XP. Follows the same convention as the Ability Cooldown HUD element:
 * experienceProgress 0 = empty/ready, 1 = full/cooldown just applied (flip with Invert Progress if
 * a given server runs the opposite way).
 *
 * The background/track is the always-full-width part of the bar - it never changes size. The
 * fill/progress part is the piece that actually grows and shrinks with the cooldown. Each surface
 * independently picks its own color mode (Background/Fill Color Mode) - a fixed color, the
 * ready/not-ready/mid/end cooldown gradient, or its own animated color unrelated to cooldown at
 * all. Which surface is the visually meaningful part of the bar turns out to vary by server/
 * resource pack, so this isn't hardcoded to one of them.
 */
public class XPBarAdjust extends Module {
    public enum RenderStyle {
        Colorize,
        Solid
    }

    public enum SurfaceColorMode {
        Fixed,
        Cooldown,
        Rainbow,
        Gradient,
        Flashing,
        HueShift
    }

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
    private final SettingGroup sgGradient = settings.createGroup("Ready Color (Gradient / Flashing)");
    private final SettingGroup sgHueShift = settings.createGroup("Ready Color (Hue Shift)");
    private final SettingGroup sgBackgroundRainbow = settings.createGroup("Background Color (Rainbow)");
    private final SettingGroup sgBackgroundGradient = settings.createGroup("Background Color (Gradient / Flashing)");
    private final SettingGroup sgBackgroundHueShift = settings.createGroup("Background Color (Hue Shift)");
    private final SettingGroup sgFillRainbow = settings.createGroup("Fill Color (Rainbow)");
    private final SettingGroup sgFillGradient = settings.createGroup("Fill Color (Gradient / Flashing)");
    private final SettingGroup sgFillHueShift = settings.createGroup("Fill Color (Hue Shift)");
    private final SettingGroup sgSound = settings.createGroup("Sound");
    private final SettingGroup sgSmoothing = settings.createGroup("Smoothing");

    // General

    private final Setting<RenderStyle> renderStyle = sgGeneral.add(new EnumSetting.Builder<RenderStyle>()
        .name("render-style")
        .description("Colorize converts the bar's own texture (background and fill both) to grayscale, then tints it - the true, full-strength color shows, but the bar's own shape/shading/border still reads through instead of turning into a flat block. Solid replaces it with a completely flat, textureless rectangle of color instead.")
        .defaultValue(RenderStyle.Colorize)
        .build()
    );

    private final Setting<Boolean> recolorBackground = sgGeneral.add(new BoolSetting.Builder()
        .name("recolor-background")
        .description("Recolors the empty/background part of the bar (the always-full-width track behind the fill) - see Background Color Mode below for how.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SurfaceColorMode> backgroundMode = sgGeneral.add(new EnumSetting.Builder<SurfaceColorMode>()
        .name("background-color-mode")
        .description("How the background/track picks its color. Fixed: Background Color below, unchanging. Cooldown: the ready/not-ready/mid/end gradient, reacting to cooldown state. Rainbow/Gradient/Flashing/HueShift: its own animated color, completely independent of cooldown, with its own dedicated Background Color (...) settings below - separate from Ready Color and from Fill Color's own copies of the same settings.")
        .defaultValue(SurfaceColorMode.Cooldown)
        .visible(recolorBackground::get)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Fixed color for the background/track, when Background Color Mode above is Fixed. Its alpha controls how strongly it blends with the bar's normal look - separate from Overall Alpha below, which fades the whole recolor.")
        .defaultValue(new SettingColor(42, 17, 0, 255))
        .visible(() -> recolorBackground.get() && backgroundMode.get() == SurfaceColorMode.Fixed)
        .build()
    );

    private final Setting<Boolean> recolorFill = sgGeneral.add(new BoolSetting.Builder()
        .name("recolor-fill")
        .description("Recolors the filled/progress part of the bar (the part that actually grows/shrinks with the cooldown) - see Fill Color Mode below for how.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SurfaceColorMode> fillMode = sgGeneral.add(new EnumSetting.Builder<SurfaceColorMode>()
        .name("fill-color-mode")
        .description("How the fill/progress part picks its color. Fixed: Fill Color below, unchanging. Cooldown: the ready/not-ready/mid/end gradient, reacting to cooldown state. Rainbow/Gradient/Flashing/HueShift: its own animated color, completely independent of cooldown, with its own dedicated Fill Color (...) settings below - separate from Ready Color and from Background Color's own copies of the same settings.")
        .defaultValue(SurfaceColorMode.Fixed)
        .visible(recolorFill::get)
        .build()
    );

    private final Setting<SettingColor> fillColor = sgGeneral.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("Fixed color for the fill/progress part, when Fill Color Mode above is Fixed. Its alpha controls how strongly it blends with the bar's normal look - separate from Overall Alpha below, which fades the whole recolor.")
        .defaultValue(new SettingColor(42, 17, 0, 255))
        .visible(() -> recolorFill.get() && fillMode.get() == SurfaceColorMode.Fixed)
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
        .description("Color right after the cooldown is applied (bar full). Its alpha controls how strongly it blends with the bar's normal look - separate from Overall Alpha below, which fades the whole recolor.")
        .defaultValue(new SettingColor(255, 40, 40))
        .build()
    );

    private final Setting<SettingColor> midColor = sgGeneral.add(new ColorSetting.Builder()
        .name("mid-color")
        .description("Color at half cooldown, same idea as the Ability Cooldown HUD element's Mid Color.")
        .defaultValue(new SettingColor(255, 165, 0))
        .visible(emptinessGradient::get)
        .build()
    );

    private final Setting<SettingColor> endColor = sgGeneral.add(new ColorSetting.Builder()
        .name("end-color")
        .description("Color the bar holds right as it approaches ready, before hard-switching to the Ready Color mode below the instant it actually reaches ready - a distinct \"almost there\" color instead of gradually blending straight into the ready effect.")
        .defaultValue(new SettingColor(40, 255, 40))
        .visible(emptinessGradient::get)
        .build()
    );

    private final Setting<Boolean> invertProgress = sgGeneral.add(new BoolSetting.Builder()
        .name("invert-progress")
        .description("Flips which end of the bar counts as \"ready\". Affects both color and the rendered fill amount together: off, the bar drains from full down to empty as it becomes ready (matching the server's real experience value directly); on, it instead fills from empty up to full as it becomes ready.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> overallAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("overall-alpha")
        .description("Final opacity of the whole recolor overlay (background and fill both), on top of each color's own alpha above - use that to control how strongly a given color blends in, and this to fade the whole effect uniformly. Safe to leave at 255 in Colorize mode, since that mode preserves the bar's own shading regardless - lower this mainly if using Solid mode and it looks too flat.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    // Ready Color

    private final Setting<ReadyColorMode> readyColorMode = sgReady.add(new EnumSetting.Builder<ReadyColorMode>()
        .name("mode")
        .description("How the ready color is picked, once the bar reaches empty.")
        .defaultValue(ReadyColorMode.Gradient)
        .build()
    );

    private final Setting<SettingColor> readyStaticColor = sgReady.add(new ColorSetting.Builder()
        .name("color")
        .description("Color when ready.")
        .defaultValue(new SettingColor(255, 0, 242))
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
        .defaultValue(List.of(new SettingColor(40, 131, 155), new SettingColor(40, 200, 255)))
        .visible(() -> readyColorMode.get() == ReadyColorMode.Gradient)
        .build()
    );

    private final Setting<Double> gradientSpeed = sgGradient.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the gradient cycles.")
        .defaultValue(3)
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

    private final Setting<Double> hueShiftRange = sgHueShift.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far the hue swings from the base color, in degrees each direction, instead of cycling continuously - keeps it reading as a variation of the base color rather than looking like Rainbow mode. 180 = swings the full way around.")
        .defaultValue(30)
        .range(0, 180)
        .sliderRange(0, 180)
        .visible(() -> readyColorMode.get() == ReadyColorMode.HueShift)
        .build()
    );

    // Background Color (Rainbow) - fully independent of Ready Color (Rainbow) above and of Fill
    // Color (Rainbow) below, even when Background Color Mode also picks Rainbow.

    private final Setting<RainbowTransition> backgroundRainbowTransition = sgBackgroundRainbow.add(new EnumSetting.Builder<RainbowTransition>()
        .name("transition")
        .description("Soft smoothly cycles through every hue. Hard jumps between a fixed set of hues with no blending.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> recolorBackground.get() && backgroundMode.get() == SurfaceColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> backgroundRainbowSteps = sgBackgroundRainbow.add(new IntSetting.Builder()
        .name("hard-steps")
        .description("How many distinct hues to jump between, in Hard mode.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 16)
        .visible(() -> recolorBackground.get() && backgroundMode.get() == SurfaceColorMode.Rainbow && backgroundRainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> backgroundRainbowSpeed = sgBackgroundRainbow.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> recolorBackground.get() && backgroundMode.get() == SurfaceColorMode.Rainbow)
        .build()
    );

    // Background Color (Gradient / Flashing)

    private final Setting<List<SettingColor>> backgroundGradientColors = sgBackgroundGradient.add(new ColorListSetting.Builder()
        .name("colors")
        .description("The colors to cycle between. Needs at least 2.")
        .defaultValue(List.of(new SettingColor(40, 131, 155), new SettingColor(40, 200, 255)))
        .visible(() -> recolorBackground.get() && backgroundMode.get() == SurfaceColorMode.Gradient)
        .build()
    );

    private final Setting<Double> backgroundGradientSpeed = sgBackgroundGradient.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the gradient cycles.")
        .defaultValue(3)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> recolorBackground.get() && backgroundMode.get() == SurfaceColorMode.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> backgroundFlashColors = sgBackgroundGradient.add(new TimedColorListSetting.Builder()
        .name("flash-colors")
        .description("The colors to hard-switch between, each with its own hold duration in game ticks. Needs at least 2.")
        .defaultValue(List.of(new TimedColorEntry(new SettingColor(40, 255, 40), 10), new TimedColorEntry(new SettingColor(40, 200, 255), 10)))
        .visible(() -> recolorBackground.get() && backgroundMode.get() == SurfaceColorMode.Flashing)
        .build()
    );

    // Background Color (Hue Shift)

    private final Setting<SettingColor> backgroundHueShiftBaseColor = sgBackgroundHueShift.add(new ColorSetting.Builder()
        .name("base-color")
        .description("Starting color - its hue continuously rotates, keeping its saturation/brightness/alpha, unlike Rainbow which always uses full saturation/brightness regardless of the base color.")
        .defaultValue(new SettingColor(40, 255, 40))
        .visible(() -> recolorBackground.get() && backgroundMode.get() == SurfaceColorMode.HueShift)
        .build()
    );

    private final Setting<Double> backgroundHueShiftSpeed = sgBackgroundHueShift.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the hue rotates.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> recolorBackground.get() && backgroundMode.get() == SurfaceColorMode.HueShift)
        .build()
    );

    private final Setting<Double> backgroundHueShiftRange = sgBackgroundHueShift.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far the hue swings from the base color, in degrees each direction, instead of cycling continuously.")
        .defaultValue(30)
        .range(0, 180)
        .sliderRange(0, 180)
        .visible(() -> recolorBackground.get() && backgroundMode.get() == SurfaceColorMode.HueShift)
        .build()
    );

    // Fill Color (Rainbow) - fully independent of Ready Color (Rainbow) and Background Color
    // (Rainbow) above, even when Fill Color Mode also picks Rainbow.

    private final Setting<RainbowTransition> fillRainbowTransition = sgFillRainbow.add(new EnumSetting.Builder<RainbowTransition>()
        .name("transition")
        .description("Soft smoothly cycles through every hue. Hard jumps between a fixed set of hues with no blending.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> recolorFill.get() && fillMode.get() == SurfaceColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> fillRainbowSteps = sgFillRainbow.add(new IntSetting.Builder()
        .name("hard-steps")
        .description("How many distinct hues to jump between, in Hard mode.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 16)
        .visible(() -> recolorFill.get() && fillMode.get() == SurfaceColorMode.Rainbow && fillRainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> fillRainbowSpeed = sgFillRainbow.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> recolorFill.get() && fillMode.get() == SurfaceColorMode.Rainbow)
        .build()
    );

    // Fill Color (Gradient / Flashing)

    private final Setting<List<SettingColor>> fillGradientColors = sgFillGradient.add(new ColorListSetting.Builder()
        .name("colors")
        .description("The colors to cycle between. Needs at least 2.")
        .defaultValue(List.of(new SettingColor(40, 131, 155), new SettingColor(40, 200, 255)))
        .visible(() -> recolorFill.get() && fillMode.get() == SurfaceColorMode.Gradient)
        .build()
    );

    private final Setting<Double> fillGradientSpeed = sgFillGradient.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the gradient cycles.")
        .defaultValue(3)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> recolorFill.get() && fillMode.get() == SurfaceColorMode.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> fillFlashColors = sgFillGradient.add(new TimedColorListSetting.Builder()
        .name("flash-colors")
        .description("The colors to hard-switch between, each with its own hold duration in game ticks. Needs at least 2.")
        .defaultValue(List.of(new TimedColorEntry(new SettingColor(40, 255, 40), 10), new TimedColorEntry(new SettingColor(40, 200, 255), 10)))
        .visible(() -> recolorFill.get() && fillMode.get() == SurfaceColorMode.Flashing)
        .build()
    );

    // Fill Color (Hue Shift)

    private final Setting<SettingColor> fillHueShiftBaseColor = sgFillHueShift.add(new ColorSetting.Builder()
        .name("base-color")
        .description("Starting color - its hue continuously rotates, keeping its saturation/brightness/alpha, unlike Rainbow which always uses full saturation/brightness regardless of the base color.")
        .defaultValue(new SettingColor(40, 255, 40))
        .visible(() -> recolorFill.get() && fillMode.get() == SurfaceColorMode.HueShift)
        .build()
    );

    private final Setting<Double> fillHueShiftSpeed = sgFillHueShift.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the hue rotates.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> recolorFill.get() && fillMode.get() == SurfaceColorMode.HueShift)
        .build()
    );

    private final Setting<Double> fillHueShiftRange = sgFillHueShift.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far the hue swings from the base color, in degrees each direction, instead of cycling continuously.")
        .defaultValue(30)
        .range(0, 180)
        .sliderRange(0, 180)
        .visible(() -> recolorFill.get() && fillMode.get() == SurfaceColorMode.HueShift)
        .build()
    );

    // Smoothing

    private final Setting<Boolean> smoothFill = sgSmoothing.add(new BoolSetting.Builder()
        .name("smooth-fill")
        .description("Interpolates the bar's fill amount (and any progress-driven color) smoothly toward its real value over time, instead of snapping instantly whenever the server syncs a new value - most servers only update this in discrete steps rather than every frame.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> smoothSpeed = sgSmoothing.add(new DoubleSetting.Builder()
        .name("smooth-speed")
        .description("How fast the displayed value catches up to the real one. Higher catches up faster/snappier, lower lags more/smoother.")
        .defaultValue(15)
        .min(0.1)
        .sliderRange(1, 60)
        .visible(smoothFill::get)
        .build()
    );

    private float smoothedProgress = -1;
    private long lastSmoothNanos = -1;

    public XPBarAdjust() {
        super(Categories.Render, "xp-bar-adjust", "Recolors the vanilla XP bar - useful on servers that repurpose it for an ability cooldown.");
    }

    @Override
    public void onActivate() {
        invalidateGrayTextures();
        smoothedProgress = -1;
        lastSmoothNanos = -1;
    }

    /** The progress value to actually render this frame - the real value unchanged, or (with Smooth Fill on) an interpolated approach toward it. Advances the interpolation state, so call this at most once per frame. 0 = ready/empty, 1 = full/just applied - not yet run through Invert Progress. */
    public float getRenderProgress(float target) {
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

    /** Fill width in bar-local pixels (0..183), from an already-smoothed render progress (see getRenderProgress) - applies Invert Progress the same way the Cooldown color mode does, so width and color always agree. */
    public int getFillWidthPixels(float renderProgress) {
        return Math.round(applyInvert(renderProgress) * 183f);
    }

    // Keeps the Colorize textures in sync with whatever resource pack is actually active, the same
    // way Minecraft's own textures do - no manual "regenerate" step needed.
    @EventHandler
    private void onResourcePacksReloaded(ResourcePacksReloadedEvent event) {
        invalidateGrayTextures();
    }

    // Colorize mode - the bar's own texture (whatever resource pack currently provides it) is read
    // once via the resource manager, converted to a normalized grayscale copy, and registered as a
    // new texture. Tinting a grayscale texture with setShaderColor multiplies cleanly to the exact
    // target color at its brightest point while still preserving all the original shading/bevel -
    // unlike tinting the original (already colored) texture, which can only ever darken toward the
    // tint and never reach it, and unlike a flat solid overlay, which shows the color perfectly but
    // erases the texture's shape entirely.
    private static final Identifier BACKGROUND_SPRITE = Identifier.ofVanilla("hud/experience_bar_background");
    private static final Identifier PROGRESS_SPRITE = Identifier.ofVanilla("hud/experience_bar_progress");

    private boolean grayTexturesReady = false;
    private Identifier grayBackgroundId, grayProgressId;
    private int grayBackgroundWidth, grayBackgroundHeight, grayProgressWidth, grayProgressHeight;

    private void invalidateGrayTextures() {
        grayTexturesReady = false;
    }

    private void ensureGrayTextures() {
        if (grayTexturesReady) return;
        grayTexturesReady = true;

        int[] backgroundSize = new int[2];
        grayBackgroundId = generateGrayTexture(BACKGROUND_SPRITE, "xpbar_background_gray", backgroundSize);
        grayBackgroundWidth = backgroundSize[0];
        grayBackgroundHeight = backgroundSize[1];

        int[] progressSize = new int[2];
        grayProgressId = generateGrayTexture(PROGRESS_SPRITE, "xpbar_progress_gray", progressSize);
        grayProgressWidth = progressSize[0];
        grayProgressHeight = progressSize[1];
    }

    private Identifier generateGrayTexture(Identifier spriteId, String outName, int[] outSize) {
        Identifier resourceId = Identifier.of(spriteId.getNamespace(), "textures/gui/sprites/" + spriteId.getPath() + ".png");

        try (InputStream input = mc.getResourceManager().open(resourceId)) {
            NativeImage original = NativeImage.read(input);
            NativeImage gray = toGrayscale(original);
            original.close();

            outSize[0] = gray.getWidth();
            outSize[1] = gray.getHeight();

            Identifier id = Identifier.of("meteor-client", outName);
            mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(gray));
            return id;
        } catch (IOException e) {
            return null;
        }
    }

    private NativeImage toGrayscale(NativeImage src) {
        int w = src.getWidth(), h = src.getHeight();

        int maxLum = 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = src.getColor(x, y);
                if (ColorHelper.Abgr.getAlpha(pixel) == 0) continue;

                int lum = luminance(pixel);
                if (lum > maxLum) maxLum = lum;
            }
        }

        float scale = 255f / maxLum;

        return src.applyToCopy(pixel -> {
            int a = ColorHelper.Abgr.getAlpha(pixel);
            int v = Math.min(255, Math.round(luminance(pixel) * scale));

            return ColorHelper.Abgr.getAbgr(a, v, v, v);
        });
    }

    private int luminance(int abgrPixel) {
        return Math.round(0.299f * ColorHelper.Abgr.getRed(abgrPixel) + 0.587f * ColorHelper.Abgr.getGreen(abgrPixel) + 0.114f * ColorHelper.Abgr.getBlue(abgrPixel));
    }

    public RenderStyle getRenderStyle() {
        return renderStyle.get();
    }

    public Identifier getBackgroundGrayTexture() {
        ensureGrayTextures();
        return grayBackgroundId;
    }

    public int getBackgroundGrayTextureWidth() {
        return grayBackgroundWidth;
    }

    public int getBackgroundGrayTextureHeight() {
        return grayBackgroundHeight;
    }

    public Identifier getProgressGrayTexture() {
        ensureGrayTextures();
        return grayProgressId;
    }

    public int getProgressGrayTextureWidth() {
        return grayProgressWidth;
    }

    public int getProgressGrayTextureHeight() {
        return grayProgressHeight;
    }

    public boolean shouldRecolorFill() {
        return recolorFill.get();
    }

    public boolean shouldRecolorBackground() {
        return recolorBackground.get();
    }

    /** Packed ARGB for the background/track overlay at the given progress, or 0 (fully transparent - draw nothing) if faded out entirely. progress: 0 = empty/ready, 1 = full/just applied. */
    public int getBackgroundOverlayArgb(float progress) {
        Color color = switch (backgroundMode.get()) {
            case Fixed -> backgroundColor.get();
            case Cooldown -> getColor(applyInvert(progress));
            case Rainbow -> rainbowColor(backgroundRainbowTransition.get(), backgroundRainbowSteps.get(), backgroundRainbowSpeed.get());
            case Gradient -> gradientColor(backgroundGradientColors.get(), backgroundGradientSpeed.get());
            case Flashing -> flashColor(backgroundFlashColors.get(), backgroundColor.get());
            case HueShift -> hueShiftColor(backgroundHueShiftBaseColor.get(), backgroundHueShiftSpeed.get(), backgroundHueShiftRange.get());
        };

        return toOverlayArgb(color);
    }

    /** Packed ARGB for the fill overlay at the given progress, or 0 (fully transparent - draw nothing) if faded out entirely. progress: 0 = empty/ready, 1 = full/just applied. */
    public int getFillOverlayArgb(float progress) {
        Color color = switch (fillMode.get()) {
            case Fixed -> fillColor.get();
            case Cooldown -> getColor(applyInvert(progress));
            case Rainbow -> rainbowColor(fillRainbowTransition.get(), fillRainbowSteps.get(), fillRainbowSpeed.get());
            case Gradient -> gradientColor(fillGradientColors.get(), fillGradientSpeed.get());
            case Flashing -> flashColor(fillFlashColors.get(), fillColor.get());
            case HueShift -> hueShiftColor(fillHueShiftBaseColor.get(), fillHueShiftSpeed.get(), fillHueShiftRange.get());
        };

        return toOverlayArgb(color);
    }

    private float applyInvert(float progress) {
        return invertProgress.get() ? 1f - progress : progress;
    }

    private int toOverlayArgb(Color color) {
        int a = Math.round((color.a / 255f) * (overallAlpha.get() / 255f) * 255f);
        if (a <= 0) return 0;

        return Color.fromRGBA(color.r, color.g, color.b, a);
    }

    /** progress: 0 = empty/ready, 1 = full/just applied - matches PlayerEntity.experienceProgress directly. */
    private Color getColor(float progress) {
        if (!emptinessGradient.get()) return progress <= 0.0001f ? getReadyColor() : notReadyColor.get();

        // Right at ready, hard-switch to the (possibly animated) ready color mode instead of
        // gradually blending into it - End Color holds as its own distinct step just before that.
        if (progress <= 0.0001f) return getReadyColor();

        if (progress <= 0.5f) return lerp(endColor.get(), midColor.get(), progress / 0.5f);

        return lerp(midColor.get(), notReadyColor.get(), (progress - 0.5f) / 0.5f);
    }

    private Color getReadyColor() {
        return switch (readyColorMode.get()) {
            case Static -> readyStaticColor.get();
            case Rainbow -> rainbowColor(rainbowTransition.get(), rainbowSteps.get(), rainbowSpeed.get());
            case Gradient -> gradientColor(gradientColors.get(), gradientSpeed.get());
            case Flashing -> flashColor(flashColors.get(), readyStaticColor.get());
            case HueShift -> hueShiftColor(hueShiftBaseColor.get(), hueShiftSpeed.get(), hueShiftRange.get());
        };
    }

    // Shared math only - every caller (Ready Color, Background Color, Fill Color) brings its own,
    // fully independent settings values, so e.g. picking Rainbow for both Background and Fill does
    // NOT make them animate together unless their speeds/steps happen to match.

    private Color hueShiftColor(SettingColor base, double speed, double range) {
        float[] hsb = java.awt.Color.RGBtoHSB(base.r, base.g, base.b, null);

        double time = System.currentTimeMillis() / 1000.0 * speed;
        double offset = Math.sin(time) * range;
        float hue = (float) (((hsb[0] * 360.0 + offset) % 360.0 + 360.0) % 360.0);

        Color color = Color.fromHsv(hue, hsb[1], hsb[2]);
        color.a = base.a;
        return color;
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

    private Color lerp(Color from, Color to, float t) {
        return new Color(
            (int) (from.r + (to.r - from.r) * t),
            (int) (from.g + (to.g - from.g) * t),
            (int) (from.b + (to.b - from.b) * t),
            (int) (from.a + (to.a - from.a) * t)
        );
    }

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
}
