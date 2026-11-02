/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.gui.screens.settings.ColorRangeListScreen;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.vlitems.VLItemChoice;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.MathHelper;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ItemHud extends HudElement {

    @Override
    public boolean hasOwnBackground() {
        return true;
    }
    public static final HudElementInfo<ItemHud> INFO = new HudElementInfo<>(Hud.GROUP, "item", "Displays the item count.", ItemHud::new);

    public enum TextColorMode {
        Static,
        Rainbow,
        Gradient,
        Flashing,
        HueShift,
        ByValue,
        ByValueRanges
    }

    public enum RainbowTransition {
        Soft,
        Hard
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgText = settings.createGroup("Text Color");
    private final SettingGroup sgTextRainbow = settings.createGroup("Text Color (Rainbow)");
    private final SettingGroup sgTextGradient = settings.createGroup("Text Color (Gradient / Flashing)");
    private final SettingGroup sgTextHueShift = settings.createGroup("Text Color (Hue Shift)");
    private final SettingGroup sgTextByValue = settings.createGroup("Text Color (By Value)");
    private final SettingGroup sgBackground = settings.createGroup("Background");
    private final SettingGroup sgBorder = settings.createGroup("Border");

    // General

    private final Setting<VLItemChoice> item = sgGeneral.add(new VLItemSetting.Builder()
        .name("item")
        .description("Item to display - can also be a captured VL item.")
        .defaultValue(VLItemChoice.of(Items.TOTEM_OF_UNDYING))
        .build()
    );

    private final Setting<NoneMode> noneMode = sgGeneral.add(new EnumSetting.Builder<NoneMode>()
        .name("none-mode")
        .description("How to render the item when you don't have the specified item in your inventory.")
        .defaultValue(NoneMode.HideCount)
        .build()
    );

    private final Setting<Boolean> showZeroCount = sgGeneral.add(new BoolSetting.Builder()
        .name("show-0-on-0-items")
        .description("Forces the count text to show \"0\" while you have none of the item, even if None Mode above is set to hide the count - no effect if None Mode is Hide Item, since that hides the icon itself and there's nothing left to put a number on.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showCountAtOne = sgGeneral.add(new BoolSetting.Builder()
        .name("show-1-on-1-item")
        .description("Shows the count text (\"1\") even while you only have exactly one of the item. Off hides the number for a single item, same as vanilla's own item count normally does, and only shows it once you have 2 or more.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the item.")
        .defaultValue(2)
        .onChanged(aDouble -> calculateSize())
        .min(1)
        .sliderRange(1, 4)
        .build()
    );

    private final Setting<Integer> border = sgGeneral.add(new IntSetting.Builder()
        .name("border")
        .description("How much space to add around the element.")
        .defaultValue(0)
        .onChanged(integer -> calculateSize())
        .build()
    );

    // Text Color

    private final Setting<TextColorMode> textColorMode = sgText.add(new EnumSetting.Builder<TextColorMode>()
        .name("count-color-mode")
        .description("How the item count number is colored. Vanilla's own count text is suppressed entirely in favor of this - the durability bar (if any) is untouched.")
        .defaultValue(TextColorMode.Static)
        .build()
    );

    private final Setting<SettingColor> textStaticColor = sgText.add(new ColorSetting.Builder()
        .name("count-color")
        .description("Color of the count number.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> textColorMode.get() == TextColorMode.Static)
        .build()
    );

    private final Setting<Boolean> textShadow = sgText.add(new BoolSetting.Builder()
        .name("count-shadow")
        .description("Draws a drop shadow behind the count number.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> textScale = sgText.add(new DoubleSetting.Builder()
        .name("count-text-scale")
        .description("Size of the count number, relative to Scale above (1 roughly matches vanilla's own count text size).")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.2, 3)
        .build()
    );

    private final Setting<TextAnchor> textAnchor = sgText.add(new EnumSetting.Builder<TextAnchor>()
        .name("count-anchor")
        .description("Where the count number sits relative to the item's own box - an Inside corner sits within the icon area, an Outside corner sits just past its edge instead.")
        .defaultValue(TextAnchor.InsideTopLeft)
        .build()
    );

    private final Setting<Integer> textXOffset = sgText.add(new IntSetting.Builder()
        .name("count-text-x-offset")
        .description("Nudges the count number left/right, in pixels (before Scale) - it's drawn as its own separate text now instead of vanilla's baked-in position, so this is here to line it back up exactly if it looks off.")
        .defaultValue(0)
        .sliderRange(-20, 20)
        .build()
    );

    private final Setting<Integer> textYOffset = sgText.add(new IntSetting.Builder()
        .name("count-text-y-offset")
        .description("Nudges the count number up/down, in pixels (before Scale).")
        .defaultValue(0)
        .sliderRange(-20, 20)
        .build()
    );

    private final Setting<RainbowTransition> textRainbowTransition = sgTextRainbow.add(new EnumSetting.Builder<RainbowTransition>()
        .name("transition")
        .description("Soft smoothly cycles through every hue. Hard jumps between a fixed set of hues with no blending.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> textColorMode.get() == TextColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> textRainbowSteps = sgTextRainbow.add(new IntSetting.Builder()
        .name("hard-steps")
        .description("How many distinct hues to jump between, in Hard mode.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 16)
        .visible(() -> textColorMode.get() == TextColorMode.Rainbow && textRainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> textRainbowSpeed = sgTextRainbow.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> textColorMode.get() == TextColorMode.Rainbow)
        .build()
    );

    private final Setting<List<SettingColor>> textGradientColors = sgTextGradient.add(new ColorListSetting.Builder()
        .name("colors")
        .description("The colors to cycle between. Needs at least 2.")
        .defaultValue(List.of(new SettingColor(255, 0, 0), new SettingColor(0, 0, 255)))
        .visible(() -> textColorMode.get() == TextColorMode.Gradient)
        .build()
    );

    private final Setting<Double> textGradientSpeed = sgTextGradient.add(new DoubleSetting.Builder()
        .name("gradient-speed")
        .description("How fast the gradient cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> textColorMode.get() == TextColorMode.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> textFlashColors = sgTextGradient.add(new TimedColorListSetting.Builder()
        .name("flash-colors")
        .description("The colors to hard-switch between, each with its own hold duration in game ticks. Needs at least 2.")
        .defaultValue(List.of(new TimedColorEntry(new SettingColor(255, 0, 0), 10), new TimedColorEntry(new SettingColor(0, 0, 255), 10)))
        .visible(() -> textColorMode.get() == TextColorMode.Flashing)
        .build()
    );

    private final Setting<SettingColor> textHueShiftBaseColor = sgTextHueShift.add(new ColorSetting.Builder()
        .name("base-color")
        .description("Starting color - its hue continuously rotates, keeping its saturation/brightness/alpha.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> textColorMode.get() == TextColorMode.HueShift)
        .build()
    );

    private final Setting<Double> textHueShiftSpeed = sgTextHueShift.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the hue rotates.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> textColorMode.get() == TextColorMode.HueShift)
        .build()
    );

    private final Setting<Double> textHueShiftRange = sgTextHueShift.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far the hue swings from the base color, in degrees each direction. 180 = swings the full way around.")
        .defaultValue(30)
        .range(0, 180)
        .sliderRange(0, 180)
        .visible(() -> textColorMode.get() == TextColorMode.HueShift)
        .build()
    );

    private final Setting<List<SettingColor>> textValueColors = sgTextByValue.add(new ColorListSetting.Builder()
        .name("colors")
        .description("The colors to blend across as the count goes from 0 to By Value Max below. Needs at least 2.")
        .defaultValue(List.of(new SettingColor(220, 30, 30), new SettingColor(255, 220, 0), new SettingColor(30, 220, 30)))
        .visible(() -> textColorMode.get() == TextColorMode.ByValue)
        .build()
    );

    private final Setting<Integer> textValueMax = sgTextByValue.add(new IntSetting.Builder()
        .name("by-value-max")
        .description("Count at/above which the number is fully the last color above - e.g. a full stack (64), or however much of this item you'd consider \"plenty\".")
        .defaultValue(64)
        .min(1)
        .sliderRange(1, 64)
        .visible(() -> textColorMode.get() == TextColorMode.ByValue)
        .build()
    );

    private final Setting<List<ColorRangeEntry>> textValueRanges = sgTextByValue.add(new ColorRangeListSetting.Builder()
        .name("value-ranges")
        .description("Each band's own count cutoff and color - see Edit Ranges below, a hard un-blended color per band instead of By Value's smooth gradient.")
        .visible(() -> textColorMode.get() == TextColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Void> textValueRangesEdit = sgTextByValue.add(new ButtonSetting.Builder()
        .name("value-ranges-edit")
        .description("Add/edit/remove the count bands above.")
        .buttonText("Edit Ranges")
        .screen(theme -> new ColorRangeListScreen(theme, "Text", textValueRanges.get(), textValueMax.get()))
        .visible(() -> textColorMode.get() == TextColorMode.ByValueRanges)
        .build()
    );

    // Background

    private final Setting<Boolean> background = sgBackground.add(new BoolSetting.Builder()
        .name("background")
        .description("Displays background.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgBackground.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color used for the background.")
        .visible(background::get)
        .defaultValue(new SettingColor(25, 25, 25, 50))
        .build()
    );

    private final Setting<Boolean> showBorder = sgBorder.add(new BoolSetting.Builder()
        .name("show-border")
        .description("Draws an outline around the element.")
        .defaultValue(false)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<SettingColor> borderColor = sgBorder.add(new ColorSetting.Builder()
        .name("border-color")
        .description("Color of the outline.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(showBorder::get)
        .build()
    );

    private final Setting<Integer> borderThickness = sgBorder.add(new IntSetting.Builder()
        .name("border-thickness")
        .description("Thickness of the outline, in pixels. Also reserves this much extra space around the item so the outline doesn't overlap it.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .visible(showBorder::get)
        .onChanged(v -> calculateSize())
        .build()
    );

    private ItemHud() {
        super(INFO);

        calculateSize();
    }

    // Plain spacing setting - independent of the border outline. The outline itself (below) is
    // drawn OUTSIDE the box's own bounds rather than reserved-for by growing the box - see
    // HeldItemHud's identical itemInset() for the full rationale (growing the box broke anchoring:
    // for a Left/Top-anchored element, growing size only extends the box AWAY from its anchored
    // corner, so the item visibly shifted toward that corner whenever the border toggled on).
    private double itemInset() {
        return border.get();
    }

    @Override
    public void setSize(double width, double height) {
        super.setSize(width, height);
    }

    private void calculateSize() {
        setSize(17 * scale.get(), 17 * scale.get());
    }

    @Override
    public void render(HudRenderer renderer) {
        VLItemChoice choice = item.get();
        RegistryWrapper.WrapperLookup registries = mc.world != null ? mc.world.getRegistryManager() : null;

        int count = registries != null ? InvUtils.find(stack -> choice.matches(stack, registries)).count() : 0;

        // Icon stack always stays at its natural count (1) - some CIT/CTM resource pack rules for
        // unique-looking items key off count=1, and would silently fall back to the plain texture
        // if the real inventory count (which can be > 1 once the item stacks) were written onto it.
        // The count shown to the player is passed separately as a text overlay below.
        ItemStack itemStack = registries != null ? choice.getIcon(registries) : choice.getBaseItem().getDefaultStack();

        if (noneMode.get() == NoneMode.HideItem && count <= 0) {
            if (isInEditor()) {
                renderer.line(x, y, x + getWidth(), y + getHeight(), Color.GRAY);
                renderer.line(x, y + getHeight(), x + getWidth(), y, Color.GRAY);
            }
        } else {
            renderer.post(() -> {
                double inset = itemInset();
                double x = this.x + inset;
                double y = this.y + inset;

                render(renderer, itemStack, count, (int) x, (int) y);
            });
        }

        if (background.get()) renderer.quad(x, y, getWidth(), getHeight(), backgroundColor.get());

        if (showBorder.get()) {
            double t = borderThickness.get() * scale.get();
            double w = getWidth(), h = getHeight();
            Color c = borderColor.get();

            renderer.quad(x - t, y - t, w + t * 2, t, c);
            renderer.quad(x - t, y + h, w + t * 2, t, c);
            renderer.quad(x - t, y, t, h, c);
            renderer.quad(x + w, y, t, h, c);
        }
    }

    private void render(HudRenderer renderer, ItemStack itemStack, int count, int x, int y) {
        boolean showCount = !(count <= 0 && noneMode.get() == NoneMode.HideCount);
        if (count <= 0 && showZeroCount.get()) showCount = true;
        if (count == 1 && !showCountAtOne.get()) showCount = false;

        String countText = showCount ? String.valueOf(Math.max(count, 0)) : null;

        // "" instead of null/countText: suppresses vanilla's own count digits (an empty string still
        // draws nothing) while leaving the durability bar - drawn by the exact same vanilla call -
        // alone, so our own colored text below can take over just the number without also having to
        // reimplement the durability bar.
        renderer.item(itemStack, x, y, scale.get().floatValue(), true, "");

        if (countText == null) return;

        double s = scale.get() * textScale.get();
        double tw = renderer.textWidth(countText, textShadow.get(), s);
        double th = renderer.textHeight(textShadow.get(), s);

        double iconSize = 16 * scale.get();
        double tx, ty;

        switch (textAnchor.get()) {
            case InsideTopLeft -> { tx = x; ty = y; }
            case InsideTopRight -> { tx = x + iconSize - tw; ty = y; }
            case InsideBottomLeft -> { tx = x; ty = y + iconSize - th; }
            case InsideBottomRight -> { tx = x + iconSize - tw; ty = y + iconSize - th; }
            case OutsideTopLeft -> { tx = x - tw; ty = y - th; }
            case OutsideTopRight -> { tx = x + iconSize; ty = y - th; }
            case OutsideBottomLeft -> { tx = x - tw; ty = y + iconSize; }
            default -> { tx = x + iconSize; ty = y + iconSize; } // OutsideBottomRight - roughly vanilla's own old count position
        }

        tx += textXOffset.get() * scale.get();
        ty += textYOffset.get() * scale.get();

        renderer.text(countText, tx, ty, getTextColor(count), textShadow.get(), s);
    }

    public enum TextAnchor {
        InsideTopLeft,
        InsideTopRight,
        InsideBottomLeft,
        InsideBottomRight,
        OutsideTopLeft,
        OutsideTopRight,
        OutsideBottomLeft,
        OutsideBottomRight
    }

    private Color getTextColor(int count) {
        return switch (textColorMode.get()) {
            case Static -> textStaticColor.get();
            case Rainbow -> rainbowColor(textRainbowTransition.get(), textRainbowSteps.get(), textRainbowSpeed.get());
            case Gradient -> gradientColor(textGradientColors.get(), textGradientSpeed.get());
            case Flashing -> flashColor(textFlashColors.get());
            case HueShift -> hueShiftColor(textHueShiftBaseColor.get(), textHueShiftSpeed.get(), textHueShiftRange.get());
            case ByValue -> byValueColor(count);
            case ByValueRanges -> ColorRangeEntry.resolve(textValueRanges.get(), count, Color.WHITE);
        };
    }

    private Color byValueColor(int count) {
        List<SettingColor> list = textValueColors.get();
        if (list.isEmpty()) return Color.WHITE;
        if (list.size() == 1) return list.get(0);

        float progress = textValueMax.get() <= 0 ? 0 : MathHelper.clamp(count / (float) textValueMax.get(), 0, 1);

        double pos = progress * (list.size() - 1);
        int index = Math.min((int) pos, list.size() - 2);
        float t = (float) (pos - index);

        return lerp(list.get(index), list.get(index + 1), t);
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

        return lerp(list.get(index), list.get((index + 1) % list.size()), t);
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

    private static Color lerp(SettingColor from, SettingColor to, float t) {
        return new Color(
            (int) (from.r + (to.r - from.r) * t),
            (int) (from.g + (to.g - from.g) * t),
            (int) (from.b + (to.b - from.b) * t),
            (int) (from.a + (to.a - from.a) * t)
        );
    }

    public enum NoneMode {
        HideItem,
        HideCount,
        ShowCount;

        @Override
        public String toString() {
            return switch (this) {
                case HideItem -> "Hide Item";
                case HideCount -> "Hide Count";
                case ShowCount -> "Show Count";
            };
        }
    }
}
