/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.renderer.text.FontFace;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.CustomFontRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.MathHelper;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class XPLevelHud extends HudElement {
    public static final HudElementInfo<XPLevelHud> INFO = new HudElementInfo<>(Hud.GROUP, "xp-level", "Displays your XP level, with customizable coloring.", XPLevelHud::new);

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

    public enum MaxColorMode {
        Static,
        Rainbow,
        Gradient,
        Flashing,
        HueShift
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgVisibility = settings.createGroup("Visibility");
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

    // Visibility

    private final Setting<Boolean> hideBelow = sgVisibility.add(new BoolSetting.Builder()
        .name("hide-below")
        .description("Hides this element entirely while the number is below Hide Below Value - useful when a low value (e.g. CP repurposing this number) isn't meaningful to you. Still shows while positioning it in the HUD editor.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hideBelowValue = sgVisibility.add(new IntSetting.Builder()
        .name("hide-below-value")
        .description("Threshold for Hide Below above - hidden while the number is strictly below this.")
        .defaultValue(1)
        .sliderRange(0, 200)
        .visible(hideBelow::get)
        .build()
    );

    private final Setting<Boolean> hideAbove = sgVisibility.add(new BoolSetting.Builder()
        .name("hide-above")
        .description("Hides this element entirely while the number is above Hide Above Value - useful once a high value stops being meaningful to you. Still shows while positioning it in the HUD editor.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hideAboveValue = sgVisibility.add(new IntSetting.Builder()
        .name("hide-above-value")
        .description("Threshold for Hide Above above - hidden while the number is strictly above this.")
        .defaultValue(200)
        .sliderRange(0, 500)
        .visible(hideAbove::get)
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
        .description("What to do once the level reaches 0, instead of just showing \"0\" like every other value.")
        .defaultValue(ZeroBehavior.ShowZero)
        .build()
    );

    private final Setting<Boolean> hideAfterDelay = sgZero.add(new BoolSetting.Builder()
        .name("hide-after-delay")
        .description("Hides this element after it's been at 0 for a while, regardless of which On Zero behavior above is chosen - e.g. combine with Custom Text to show a message at 0 for a bit, then hide entirely. No effect when On Zero is Hide, since that already hides immediately.")
        .defaultValue(false)
        .visible(() -> zeroBehavior.get() != ZeroBehavior.Hide)
        .build()
    );

    private final Setting<Integer> zeroHideAfterTicks = sgZero.add(new IntSetting.Builder()
        .name("zero-hide-after-ticks")
        .description("How many game ticks the level must have been at 0 before this element hides.")
        .defaultValue(40)
        .min(1)
        .sliderRange(1, 200)
        .visible(() -> hideAfterDelay.get() && zeroBehavior.get() != ZeroBehavior.Hide)
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
        .description("Forces Max Color below once the level reaches Max Level (or Max Level Range below it), overriding Color Mode above entirely while it's showing - same idea as the XP Bar module's Ready Color hard-switch.")
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

    private final Setting<MaxColorMode> maxColorMode = sgMax.add(new EnumSetting.Builder<MaxColorMode>()
        .name("max-color-mode")
        .description("How the forced max-level color looks - same style choices as Color Mode above, but with their own independent settings below so the max look can differ from the normal one.")
        .defaultValue(MaxColorMode.Static)
        .visible(highlightMax::get)
        .build()
    );

    private final Setting<SettingColor> maxColor = sgMax.add(new ColorSetting.Builder()
        .name("max-color")
        .description("Color forced once the level is within Max Level Range of Max Level.")
        .defaultValue(new SettingColor(255, 215, 0))
        .visible(() -> highlightMax.get() && maxColorMode.get() == MaxColorMode.Static)
        .build()
    );

    private final Setting<RainbowTransition> maxRainbowTransition = sgMax.add(new EnumSetting.Builder<RainbowTransition>()
        .name("max-rainbow-transition")
        .description("Soft smoothly cycles through every hue. Hard jumps between a fixed set of hues with no blending.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> highlightMax.get() && maxColorMode.get() == MaxColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> maxRainbowSteps = sgMax.add(new IntSetting.Builder()
        .name("max-rainbow-hard-steps")
        .description("How many distinct hues to jump between, in Hard mode.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 16)
        .visible(() -> highlightMax.get() && maxColorMode.get() == MaxColorMode.Rainbow && maxRainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> maxRainbowSpeed = sgMax.add(new DoubleSetting.Builder()
        .name("max-rainbow-speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> highlightMax.get() && maxColorMode.get() == MaxColorMode.Rainbow)
        .build()
    );

    private final Setting<List<SettingColor>> maxGradientColors = sgMax.add(new ColorListSetting.Builder()
        .name("max-gradient-colors")
        .description("The colors to cycle between. Needs at least 2.")
        .defaultValue(List.of(new SettingColor(255, 215, 0), new SettingColor(255, 255, 255)))
        .visible(() -> highlightMax.get() && maxColorMode.get() == MaxColorMode.Gradient)
        .build()
    );

    private final Setting<Double> maxGradientSpeed = sgMax.add(new DoubleSetting.Builder()
        .name("max-gradient-speed")
        .description("How fast the gradient cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> highlightMax.get() && maxColorMode.get() == MaxColorMode.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> maxFlashColors = sgMax.add(new TimedColorListSetting.Builder()
        .name("max-flash-colors")
        .description("The colors to hard-switch between, each with its own hold duration in game ticks. Needs at least 2.")
        .defaultValue(List.of(new TimedColorEntry(new SettingColor(255, 215, 0), 10), new TimedColorEntry(new SettingColor(255, 255, 255), 10)))
        .visible(() -> highlightMax.get() && maxColorMode.get() == MaxColorMode.Flashing)
        .build()
    );

    private final Setting<SettingColor> maxHueShiftBaseColor = sgMax.add(new ColorSetting.Builder()
        .name("max-hue-shift-base-color")
        .description("Starting color - its hue continuously rotates, keeping its saturation/brightness/alpha.")
        .defaultValue(new SettingColor(255, 215, 0))
        .visible(() -> highlightMax.get() && maxColorMode.get() == MaxColorMode.HueShift)
        .build()
    );

    private final Setting<Double> maxHueShiftSpeed = sgMax.add(new DoubleSetting.Builder()
        .name("max-hue-shift-speed")
        .description("How fast the hue rotates.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> highlightMax.get() && maxColorMode.get() == MaxColorMode.HueShift)
        .build()
    );

    private final Setting<Double> maxHueShiftRange = sgMax.add(new DoubleSetting.Builder()
        .name("max-hue-shift-range")
        .description("How far the hue swings from the base color, in degrees each direction, instead of cycling continuously. 180 = swings the full way around.")
        .defaultValue(30)
        .range(0, 180)
        .sliderRange(0, 180)
        .visible(() -> highlightMax.get() && maxColorMode.get() == MaxColorMode.HueShift)
        .build()
    );

    // Font

    private final Setting<Boolean> useCustomFont = sgFont.add(new BoolSetting.Builder()
        .name("use-custom-font")
        .description("Renders this element's text in its own chosen font, independent of the global Custom Font setting (or lack of one).")
        .defaultValue(false)
        .build()
    );

    private final Setting<FontFace> font = sgFont.add(new FontFaceSetting.Builder()
        .name("font")
        .description("Font to render this element's text in.")
        .visible(useCustomFont::get)
        .build()
    );

    private long zeroSinceMs = -1;

    public XPLevelHud() {
        super(INFO);

        calculateSize();
    }

    private void calculateSize() {
        setSize(30 * scale.get(), 9 * scale.get());
    }

    @Override
    public void render(HudRenderer renderer) {
        int level = mc.player != null ? mc.player.experienceLevel : 0;
        boolean atZero = level <= 0;
        long now = System.currentTimeMillis();

        if (atZero) {
            if (zeroSinceMs == -1) zeroSinceMs = now;
        } else {
            zeroSinceMs = -1;
        }

        if (atZero && zeroBehavior.get() == ZeroBehavior.Hide) return;

        if (!isInEditor()) {
            if (atZero && hideAfterDelay.get() && zeroBehavior.get() != ZeroBehavior.Hide && zeroSinceMs != -1 && now - zeroSinceMs >= zeroHideAfterTicks.get() * 50L) {
                return;
            }

            if (hideBelow.get() && level < hideBelowValue.get()) return;
            if (hideAbove.get() && level > hideAboveValue.get()) return;
        }

        String text = atZero && zeroBehavior.get() == ZeroBehavior.CustomText ? zeroText.get() : String.valueOf(level);
        Color color = getColor(level, atZero);

        if (useCustomFont.get() && font.get() != null) {
            setSize(CustomFontRenderer.width(font.get(), text, scale.get(), shadow.get()), CustomFontRenderer.height(font.get(), scale.get(), shadow.get()));
            CustomFontRenderer.render(font.get(), text, x, y, color, scale.get(), shadow.get());
        }
        else {
            setSize(renderer.textWidth(text, scale.get()), renderer.textHeight(shadow.get(), scale.get()));
            renderer.text(text, x, y, color, shadow.get(), scale.get());
        }
    }

    private Color getColor(int level, boolean atZero) {
        if (atZero && zeroBehavior.get() == ZeroBehavior.DistinctColor) return zeroColor.get();

        if (highlightMax.get() && level >= maxLevel.get() - maxLevelRange.get()) {
            return switch (maxColorMode.get()) {
                case Static -> maxColor.get();
                case Rainbow -> rainbowColor(maxRainbowTransition.get(), maxRainbowSteps.get(), maxRainbowSpeed.get());
                case Gradient -> gradientColor(maxGradientColors.get(), maxGradientSpeed.get());
                case Flashing -> flashColor(maxFlashColors.get());
                case HueShift -> hueShiftColor(maxHueShiftBaseColor.get(), maxHueShiftSpeed.get(), maxHueShiftRange.get());
            };
        }

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
        return hueShiftColor(hueShiftBaseColor.get(), hueShiftSpeed.get(), hueShiftRange.get());
    }

    private Color getRainbowColor() {
        return rainbowColor(rainbowTransition.get(), rainbowSteps.get(), rainbowSpeed.get());
    }

    private Color getGradientColor() {
        return gradientColor(colors.get(), gradientSpeed.get());
    }

    private Color getFlashColor() {
        return flashColor(flashColors.get());
    }

    // Parameterized so the Max Level highlight (see getColor()) can reuse the exact same math
    // against its own independent settings, instead of duplicating each formula.

    private Color hueShiftColor(SettingColor base, double speed, double range) {
        float[] hsb = java.awt.Color.RGBtoHSB(base.r, base.g, base.b, null);

        double time = System.currentTimeMillis() / 1000.0 * speed;
        double offset = Math.sin(time) * range;
        float hue = (float) (((hsb[0] * 360.0 + offset) % 360.0 + 360.0) % 360.0);

        Color color = Color.fromHsv(hue, hsb[1], hsb[2]);
        color.a = base.a;
        return color;
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

    private Color gradientColor(List<SettingColor> list, double speed) {
        if (list.isEmpty()) return Color.WHITE;
        if (list.size() == 1) return list.get(0);

        double time = (System.currentTimeMillis() / 1000.0 * speed) % list.size();
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

    private Color flashColor(List<TimedColorEntry> entries) {
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
