/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.renderer.text.FontFace;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Recolors/restyles the vanilla XP level number itself (the number InGameHud draws above/inside
 * the real XP bar) - the number equivalent of XPBarAdjust. Same feature set as the XP Level HUD
 * element (On Zero behavior, By Value gradient coloring, a Max Level highlight, a per-element
 * custom font), just applied to the real vanilla number in its normal on-screen position instead
 * of a separately positioned HUD element.
 */
public class XPLevelAdjust extends Module {
    public enum ColorMode {
        Static,
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

    public enum ZeroBehavior {
        ShowZero,
        Hide,
        CustomText,
        DistinctColor
    }

    public enum OutlineColor {
        None,
        Black,
        White,
        Custom
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRainbow = settings.createGroup("Rainbow");
    private final SettingGroup sgGradient = settings.createGroup("Gradient / Flashing");
    private final SettingGroup sgHueShift = settings.createGroup("Hue Shift");
    private final SettingGroup sgByValue = settings.createGroup("By Value");
    private final SettingGroup sgZero = settings.createGroup("On Zero");
    private final SettingGroup sgMax = settings.createGroup("Max Level");
    private final SettingGroup sgFont = settings.createGroup("Font");

    // General

    private final Setting<ColorMode> colorMode = sgGeneral.add(new EnumSetting.Builder<ColorMode>()
        .name("color-mode")
        .description("How the number is colored.")
        .defaultValue(ColorMode.Static)
        .build()
    );

    private final Setting<SettingColor> staticColor = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the number.")
        .defaultValue(new SettingColor(128, 255, 32))
        .visible(() -> colorMode.get() == ColorMode.Static)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Size multiplier over vanilla's normal size.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 5)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Draws a drop shadow behind the number.")
        .defaultValue(true)
        .build()
    );

    private final Setting<OutlineColor> outline = sgGeneral.add(new EnumSetting.Builder<OutlineColor>()
        .name("outline")
        .description("Draws a solid outline around the number in the chosen color, same as vanilla's own default look (a thick black outline, keeping it readable against any background) - None disables it, leaving only the drop shadow above (if on).")
        .defaultValue(OutlineColor.Black)
        .build()
    );

    private final Setting<SettingColor> customOutlineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("custom-outline-color")
        .description("Outline color, when Outline above is Custom.")
        .defaultValue(new SettingColor(0, 0, 0))
        .visible(() -> outline.get() == OutlineColor.Custom)
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
        .description("The colors to cycle between. Needs at least 2.")
        .defaultValue(List.of(new SettingColor(255, 0, 0), new SettingColor(0, 0, 255)))
        .visible(() -> colorMode.get() == ColorMode.Gradient)
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

    private final Setting<List<TimedColorEntry>> flashColors = sgGradient.add(new TimedColorListSetting.Builder()
        .name("flash-colors")
        .description("The colors to hard-switch between, each with its own hold duration in game ticks. Needs at least 2.")
        .defaultValue(List.of(new TimedColorEntry(new SettingColor(255, 0, 0), 10), new TimedColorEntry(new SettingColor(0, 0, 255), 10)))
        .visible(() -> colorMode.get() == ColorMode.Flashing)
        .build()
    );

    // Hue Shift

    private final Setting<SettingColor> hueShiftBaseColor = sgHueShift.add(new ColorSetting.Builder()
        .name("base-color")
        .description("Starting color - its hue continuously rotates, keeping its saturation/brightness/alpha, unlike Rainbow which always uses full saturation/brightness regardless of the base color.")
        .defaultValue(new SettingColor(128, 255, 32))
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

    private final Setting<Double> hueShiftRange = sgHueShift.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far the hue swings from the base color, in degrees each direction, instead of cycling continuously - keeps it reading as a variation of the base color rather than looking like Rainbow mode. 180 = swings the full way around.")
        .defaultValue(30)
        .range(0, 180)
        .sliderRange(0, 180)
        .visible(() -> colorMode.get() == ColorMode.HueShift)
        .build()
    );

    // By Value

    private final Setting<List<SettingColor>> valueColors = sgByValue.add(new ColorListSetting.Builder()
        .name("colors")
        .description("The colors to blend across as the level goes from 0 to Max Level below. Needs at least 2.")
        .defaultValue(List.of(new SettingColor(255, 0, 0), new SettingColor(255, 255, 0), new SettingColor(0, 255, 0)))
        .visible(() -> colorMode.get() == ColorMode.ByValue)
        .build()
    );

    // On Zero

    private final Setting<ZeroBehavior> zeroBehavior = sgZero.add(new EnumSetting.Builder<ZeroBehavior>()
        .name("on-zero")
        .description("What to do once the level reaches 0. Vanilla normally hides the number entirely at 0 - Show Zero draws over that and displays \"0\" like any other value.")
        .defaultValue(ZeroBehavior.ShowZero)
        .build()
    );

    private final Setting<String> zeroText = sgZero.add(new StringSetting.Builder()
        .name("zero-text")
        .description("Text to show instead of \"0\" once the level reaches 0.")
        .defaultValue("")
        .visible(() -> zeroBehavior.get() == ZeroBehavior.CustomText)
        .build()
    );

    private final Setting<SettingColor> zeroColor = sgZero.add(new ColorSetting.Builder()
        .name("zero-color")
        .description("Color forced once the level reaches 0, overriding Color Mode above entirely while it's showing.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> zeroBehavior.get() == ZeroBehavior.DistinctColor)
        .build()
    );

    // Max Level

    private final Setting<Boolean> highlightMax = sgMax.add(new BoolSetting.Builder()
        .name("highlight-max")
        .description("Forces Max Color below once the level reaches Max Level (or Max Level Range below it), overriding Color Mode above entirely while it's showing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxLevel = sgMax.add(new IntSetting.Builder()
        .name("max-level")
        .description("The level considered \"max\" - the top end of the By Value gradient above, and the threshold Highlight Max below counts from.")
        .defaultValue(108)
        .min(1)
        .sliderRange(1, 200)
        .build()
    );

    private final Setting<Integer> maxLevelRange = sgMax.add(new IntSetting.Builder()
        .name("max-level-range")
        .description("How many levels below Max Level already count as \"at max\" for Highlight Max below - 0 means only Max Level itself triggers it.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 50)
        .visible(highlightMax::get)
        .build()
    );

    private final Setting<SettingColor> maxColor = sgMax.add(new ColorSetting.Builder()
        .name("max-color")
        .description("Color forced once the level is within Max Level Range of Max Level.")
        .defaultValue(new SettingColor(255, 215, 0))
        .visible(highlightMax::get)
        .build()
    );

    private final Setting<Boolean> maxCustomTextEnabled = sgMax.add(new BoolSetting.Builder()
        .name("max-custom-text")
        .description("Replaces the number with custom text once the level is within Max Level Range of Max Level - independent of Highlight Max above, so you can use either or both together (e.g. show \"MAX\" in gold).")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> maxText = sgMax.add(new StringSetting.Builder()
        .name("max-text")
        .description("Text to show instead of the number, when Max Custom Text above is on.")
        .defaultValue("MAX")
        .visible(maxCustomTextEnabled::get)
        .build()
    );

    // Font

    // Disabled (see InGameHudMixin.onRenderExperienceLevel) - CustomFontRenderer's immediate-mode
    // GL draw call corrupts vanilla's batched rendering when run from a mixin hook mid vanilla HUD
    // render, the same way it did for Item Info's badges - confirmed crashing the game here too.
    private final Setting<Boolean> useCustomFont = sgFont.add(new BoolSetting.Builder()
        .name("use-custom-font")
        .description("Currently disabled - was crashing the game. Renders the number in its own chosen font, independent of the global Custom Font setting (or lack of one).")
        .defaultValue(false)
        .build()
    );

    private final Setting<FontFace> font = sgFont.add(new FontFaceSetting.Builder()
        .name("font")
        .description("Font to render the number in.")
        .visible(useCustomFont::get)
        .build()
    );

    public XPLevelAdjust() {
        super(Categories.Render, "xp-level-adjust", "Recolors/restyles the vanilla XP level number - useful on servers that repurpose it, or just to make it stand out.");
    }

    public boolean isHidden(int level) {
        return level <= 0 && zeroBehavior.get() == ZeroBehavior.Hide;
    }

    public String getText(int level) {
        if (level <= 0 && zeroBehavior.get() == ZeroBehavior.CustomText) return zeroText.get();
        if (maxCustomTextEnabled.get() && isAtMax(level)) return maxText.get();

        return String.valueOf(level);
    }

    private boolean isAtMax(int level) {
        return level >= maxLevel.get() - maxLevelRange.get();
    }

    public double getScale() {
        return scale.get();
    }

    public boolean getShadow() {
        return shadow.get();
    }

    public OutlineColor getOutline() {
        return outline.get();
    }

    public SettingColor getCustomOutlineColor() {
        return customOutlineColor.get();
    }

    public boolean useCustomFont() {
        return false; // see the field's own comment above
    }

    public FontFace getFont() {
        return font.get();
    }

    public Color getColor(int level) {
        boolean atZero = level <= 0;

        if (atZero && zeroBehavior.get() == ZeroBehavior.DistinctColor) return zeroColor.get();
        if (highlightMax.get() && isAtMax(level)) return maxColor.get();

        return switch (colorMode.get()) {
            case Static -> staticColor.get();
            case Rainbow -> getRainbowColor();
            case Gradient -> getGradientColor();
            case Flashing -> getFlashColor();
            case HueShift -> getHueShiftColor();
            case ByValue -> getByValueColor(level);
        };
    }

    private Color getByValueColor(int level) {
        List<SettingColor> list = valueColors.get();
        if (list.isEmpty()) return Color.WHITE;
        if (list.size() == 1) return list.get(0);

        float progress = maxLevel.get() <= 0 ? 0 : MathHelper.clamp(level / (float) maxLevel.get(), 0, 1);

        double pos = progress * (list.size() - 1);
        int index = Math.min((int) pos, list.size() - 2);
        float t = (float) (pos - index);

        SettingColor from = list.get(index);
        SettingColor to = list.get(index + 1);

        return new Color(
            (int) (from.r + (to.r - from.r) * t),
            (int) (from.g + (to.g - from.g) * t),
            (int) (from.b + (to.b - from.b) * t),
            (int) (from.a + (to.a - from.a) * t)
        );
    }

    private Color getHueShiftColor() {
        SettingColor base = hueShiftBaseColor.get();
        float[] hsb = java.awt.Color.RGBtoHSB(base.r, base.g, base.b, null);

        double time = System.currentTimeMillis() / 1000.0 * hueShiftSpeed.get();
        double offset = Math.sin(time) * hueShiftRange.get();
        float hue = (float) (((hsb[0] * 360.0 + offset) % 360.0 + 360.0) % 360.0);

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
        List<TimedColorEntry> entries = flashColors.get();
        if (entries.isEmpty()) return Color.WHITE;
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
}
