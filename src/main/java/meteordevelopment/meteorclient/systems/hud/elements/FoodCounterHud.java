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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Adds up how much food you're carrying, across your whole inventory - one number, not one entry
 * per item. Leave Items empty to count anything vanilla considers food (its own Food component);
 * fill it in to count only specific items instead (a whitelist, not a filter on top of "is food") -
 * useful if only some of your food actually matters (healing items vs junk food, say).
 */
public class FoodCounterHud extends HudElement {
    public static final HudElementInfo<FoodCounterHud> INFO = new HudElementInfo<>(Hud.GROUP, "food-counter", "Adds up how much food you're carrying into one number.", FoodCounterHud::new);

    public enum ColorMode {
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
    private final SettingGroup sgRainbow = settings.createGroup("Color (Rainbow)");
    private final SettingGroup sgGradient = settings.createGroup("Color (Gradient / Flashing)");
    private final SettingGroup sgHueShift = settings.createGroup("Color (Hue Shift)");
    private final SettingGroup sgByValue = settings.createGroup("Color (By Value)");

    private final Setting<List<String>> items = sgGeneral.add(new StringListSetting.Builder()
        .name("items")
        .description("One per line: an item id (minecraft:cooked_beef) to match exactly, or name:<text> to match anything whose display name (case-insensitive) contains that text - for custom food items that don't share a vanilla id you can match by. Empty = count anything with vanilla's own Food component instead.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<Boolean> includeHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("include-hotbar")
        .description("Counts food in the hotbar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> includeInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("include-inventory")
        .description("Counts food in the main inventory (not the hotbar).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> includeOffhand = sgGeneral.add(new BoolSetting.Builder()
        .name("include-offhand")
        .description("Counts food in the off hand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder()
        .name("prefix")
        .description("Text shown before the number.")
        .defaultValue("Food: ")
        .build()
    );

    private final Setting<Boolean> hideAtZero = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-at-zero")
        .description("Hides this element entirely while you're carrying none. Still shows while positioning it in the HUD editor.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text scale.")
        .defaultValue(2.0)
        .min(0.5)
        .sliderRange(0.5, 6)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Draws a drop shadow behind the text.")
        .defaultValue(true)
        .build()
    );

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

    private final Setting<List<SettingColor>> gradientColors = sgGradient.add(new ColorListSetting.Builder()
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

    private final Setting<SettingColor> hueShiftBaseColor = sgHueShift.add(new ColorSetting.Builder()
        .name("base-color")
        .description("Starting color - its hue continuously rotates, keeping its saturation/brightness/alpha.")
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
        .description("How far the hue swings from the base color, in degrees each direction. 180 = swings the full way around.")
        .defaultValue(30)
        .range(0, 180)
        .sliderRange(0, 180)
        .visible(() -> colorMode.get() == ColorMode.HueShift)
        .build()
    );

    private final Setting<List<SettingColor>> valueColors = sgByValue.add(new ColorListSetting.Builder()
        .name("colors")
        .description("The colors to blend across as the count goes from 0 to By Value Max below. Needs at least 2.")
        .defaultValue(List.of(new SettingColor(220, 30, 30), new SettingColor(255, 220, 0), new SettingColor(30, 220, 30)))
        .visible(() -> colorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<Integer> valueMax = sgByValue.add(new IntSetting.Builder()
        .name("by-value-max")
        .description("Count at/above which the number is fully the last color above.")
        .defaultValue(64)
        .min(1)
        .sliderRange(1, 200)
        .visible(() -> colorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<List<ColorRangeEntry>> valueRanges = sgByValue.add(new ColorRangeListSetting.Builder()
        .name("value-ranges")
        .description("Each band's own count cutoff and color - see Edit Ranges below, a hard un-blended color per band instead of By Value's smooth gradient.")
        .visible(() -> colorMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Void> valueRangesEdit = sgByValue.add(new ButtonSetting.Builder()
        .name("value-ranges-edit")
        .description("Add/edit/remove the count bands above.")
        .buttonText("Edit Ranges")
        .screen(theme -> new ColorRangeListScreen(theme, "Color", valueRanges.get(), valueMax.get()))
        .visible(() -> colorMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    public FoodCounterHud() {
        super(INFO);
        calculateSize();
    }

    private void calculateSize() {
        setSize(60 * scale.get(), 9 * scale.get());
    }

    private boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;

        List<String> list = items.get();
        if (list.isEmpty()) return stack.get(DataComponentTypes.FOOD) != null;

        String id = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);

        for (String entry : list) {
            String needle = entry.trim();
            if (needle.isEmpty()) continue;

            if (needle.toLowerCase(Locale.ROOT).startsWith("name:")) {
                String text = needle.substring(5).trim().toLowerCase(Locale.ROOT);
                if (!text.isEmpty() && name.contains(text)) return true;
            } else if (needle.toLowerCase(Locale.ROOT).equals(id)) {
                return true;
            }
        }

        return false;
    }

    private int countFood() {
        if (mc.player == null) return 0;

        int total = 0;

        if (includeHotbar.get() || includeInventory.get()) {
            for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
                boolean hotbarSlot = i < 9;
                if (hotbarSlot && !includeHotbar.get()) continue;
                if (!hotbarSlot && !includeInventory.get()) continue;

                ItemStack stack = mc.player.getInventory().main.get(i);
                if (matches(stack)) total += stack.getCount();
            }
        }

        if (includeOffhand.get()) {
            for (ItemStack stack : mc.player.getInventory().offHand) {
                if (matches(stack)) total += stack.getCount();
            }
        }

        return total;
    }

    @Override
    public void render(HudRenderer renderer) {
        int count = isInEditor() ? 42 : countFood();

        if (!isInEditor() && hideAtZero.get() && count <= 0) {
            setSize(renderer.textWidth(prefix.get() + "0", shadow.get(), scale.get()), renderer.textHeight(shadow.get(), scale.get()));
            return;
        }

        String text = prefix.get() + count;
        setSize(renderer.textWidth(text, shadow.get(), scale.get()), renderer.textHeight(shadow.get(), scale.get()));
        renderer.text(text, x, y, getColor(count), shadow.get(), scale.get());
    }

    private Color getColor(int count) {
        return switch (colorMode.get()) {
            case Static -> staticColor.get();
            case Rainbow -> rainbowColor(rainbowTransition.get(), rainbowSteps.get(), rainbowSpeed.get());
            case Gradient -> gradientColor(gradientColors.get(), gradientSpeed.get());
            case Flashing -> flashColor(flashColors.get());
            case HueShift -> hueShiftColor(hueShiftBaseColor.get(), hueShiftSpeed.get(), hueShiftRange.get());
            case ByValue -> byValueColor(count);
            case ByValueRanges -> ColorRangeEntry.resolve(valueRanges.get(), count, Color.WHITE);
        };
    }

    private Color byValueColor(int count) {
        List<SettingColor> list = valueColors.get();
        if (list.isEmpty()) return Color.WHITE;
        if (list.size() == 1) return list.get(0);

        float progress = valueMax.get() <= 0 ? 0 : MathHelper.clamp(count / (float) valueMax.get(), 0, 1);

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

        long tick = System.currentTimeMillis() / 50;
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
}
