/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.renderer.text.FontFace;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.VLPlusAdditions;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the "Quality: 78 ||||||||||134%125  ⚡4" line the server writes into item lore (see an
 * item-dumps capture for the exact format) and shows the Ward number (⚡N) and Quality percent as
 * small badges over the item icon, in both the hotbar and inventory-type screens - both share the
 * same DrawContext.drawItemInSlot call, so a single mixin hook covers both. Min/Max Quality are
 * also parsed per-item (the two bare numbers flanking the bar/percent) since the server already
 * prints each item's own floor/ceiling there - no need for this module to guess a single global
 * cap. When Quality display is on, the vanilla durability bar (normally damage-based) is
 * repurposed to visualize Quality % instead - colored with the server's own lore color (already
 * blue/aqua for Super Enhanced items above Max Quality) so the bar and badge always agree on color
 * without this module having to guess the server's own tier thresholds.
 */
public class ItemInfo extends Module {
    private static final Pattern QUALITY_PATTERN = Pattern.compile("^(\\d+)%$");
    private static final Pattern WARD_PATTERN = Pattern.compile("^⚡(\\d+)$");
    private static final Pattern BARE_NUMBER_PATTERN = Pattern.compile("^(\\d+)\\s*$");
    private static final Pattern TRAILING_TIER_PATTERN = Pattern.compile("\\s+[IVXLCDM]+$");
    private static final int VANILLA_LINE_HEIGHT = 9;

    // Built-in name-to-symbol tables, from item-dumps captures (2026-09-08/09) - see Enchant
    // Symbols/Attachment Symbols above for user-added overrides/extras. Enchant names carry their
    // tier in the name itself in lore (e.g. "Frost III"), stripped via TRAILING_TIER_PATTERN before
    // lookup so every tier of the same enchant resolves to one symbol. Attachment names in lore
    // never carry a tier. Only special (ability-type) Attachments are listed here on purpose - the
    // many plain stat-bonus Attachments (Gold Bonus, Health Regen, etc.) are deliberately absent so
    // they never get a badge, matching "only show what special attachment is used, if it is".
    private static final Map<String, String> BUILTIN_ENCHANT_SYMBOLS = Map.of(
        "Cooldown Reduction", "⌛",
        "Fire", "♨",
        "Frost", "❄",
        "Pure Damage", "⚔",
        "Revenge", "☠"
    );

    // Covers the full ability-name catalog from the weapon skill screen dumps (2026-09-09), not
    // just the 8 seen as actual item attachments so far - Attachments are drawn from this same
    // ability pool per-weapon, so new items are very likely to surface names already listed here.
    private static final Map<String, String> BUILTIN_ATTACHMENT_SYMBOLS = Map.ofEntries(
        Map.entry("Arcane Haste", "✦"),
        Map.entry("Barrage", "➶"),
        Map.entry("Binding Orb", "◉"),
        Map.entry("Charge", "↠"),
        Map.entry("Combo Attack", "✕"),
        Map.entry("Cyclone", "✵"),
        Map.entry("Damage Block", "❖"),
        Map.entry("Dash", "≫"),
        Map.entry("Deep Wound", "✼"),
        Map.entry("Deflection", "↯"),
        Map.entry("Dodge", "↺"),
        Map.entry("Fortify", "⛨"),
        Map.entry("Frenzy", "✺"),
        Map.entry("Fury", "✹"),
        Map.entry("Kunai", "⚔"),
        Map.entry("Lasso", "➰"),
        Map.entry("Life Steal", "✥"),
        Map.entry("Magic Pillar", "▲"),
        Map.entry("Missile", "➹"),
        Map.entry("Momentum Shot", "➳"),
        Map.entry("Needle Strike", "†"),
        Map.entry("Parry", "◈"),
        Map.entry("Pounce", "↷"),
        Map.entry("Powershot", "⇗"),
        Map.entry("Rapid Fire", "⋙"),
        Map.entry("Sever", "✂"),
        Map.entry("Shadow Cloak", "☾"),
        Map.entry("Shockwave", "≋"),
        Map.entry("Smash", "⚒"),
        Map.entry("Uppercut", "⇧"),
        Map.entry("Vacuum", "⊚"),
        Map.entry("Wild Swings", "↻")
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAppearance = settings.createGroup("Appearance");
    private final SettingGroup sgEnchantColor = settings.createGroup("Enchant Color (Rainbow/Gradient/Flashing/Hue Shift)");
    private final SettingGroup sgAttachmentColor = settings.createGroup("Attachment Color (Rainbow/Gradient/Flashing/Hue Shift)");
    private final SettingGroup sgDurabilityBar = settings.createGroup("Durability Bar");

    private final Setting<DisplayMode> wardDisplay = sgGeneral.add(new EnumSetting.Builder<DisplayMode>()
        .name("ward-display")
        .description("When to show the Ward (⚡) badge on items.")
        .defaultValue(DisplayMode.Always)
        .build()
    );

    private final Setting<Keybind> wardKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("ward-key")
        .description("Key for Ward Display's On Keypress, independent of the other badges' own keys - so each badge can be revealed on its own, without covering the whole item at once.")
        .defaultValue(Keybind.none())
        .visible(() -> wardDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<KeyMode> wardKeyMode = sgGeneral.add(new EnumSetting.Builder<KeyMode>()
        .name("ward-key-mode")
        .description("Hold shows the Ward badge only while the key is held. Toggle flips it on/off each press.")
        .defaultValue(KeyMode.Hold)
        .visible(() -> wardDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<Boolean> wardRangeEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("ward-range-enabled")
        .description("Only shows the Ward badge when its number falls within the range below - hidden entirely outside it, regardless of Ward Display above.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> wardRangeMin = sgGeneral.add(new IntSetting.Builder()
        .name("ward-range-min")
        .description("Minimum Ward number to show, when Ward Range is enabled.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 20)
        .visible(wardRangeEnabled::get)
        .build()
    );

    private final Setting<Integer> wardRangeMax = sgGeneral.add(new IntSetting.Builder()
        .name("ward-range-max")
        .description("Maximum Ward number to show, when Ward Range is enabled.")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 20)
        .visible(wardRangeEnabled::get)
        .build()
    );

    private final Setting<BadgeAnchor> wardAnchor = sgGeneral.add(new EnumSetting.Builder<BadgeAnchor>()
        .name("ward-anchor")
        .description("Which corner of the icon the Ward badge is anchored to.")
        .defaultValue(BadgeAnchor.TopLeft)
        .build()
    );

    private final Setting<Integer> wardLine = sgGeneral.add(new IntSetting.Builder()
        .name("ward-line")
        .description("Which row the Ward badge sits on, counting outward from its anchor corner (0 = the row closest to the corner). Raise this to stack it further from the corner, e.g. to make room for another badge sharing the same corner.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<DisplayMode> qualityDisplay = sgGeneral.add(new EnumSetting.Builder<DisplayMode>()
        .name("quality-display")
        .description("When to show the Quality % badge (and repurpose the durability bar to visualize it) on items.")
        .defaultValue(DisplayMode.Always)
        .build()
    );

    private final Setting<Keybind> qualityKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("quality-key")
        .description("Key for Quality Display's On Keypress, independent of the other badges' own keys.")
        .defaultValue(Keybind.none())
        .visible(() -> qualityDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<KeyMode> qualityKeyMode = sgGeneral.add(new EnumSetting.Builder<KeyMode>()
        .name("quality-key-mode")
        .description("Hold shows the Quality badge only while the key is held. Toggle flips it on/off each press.")
        .defaultValue(KeyMode.Hold)
        .visible(() -> qualityDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<Boolean> qualityRangeEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("quality-range-enabled")
        .description("Only shows the Quality % badge (and durability bar override) when the percent falls within the range below - e.g. 100-125 to only care about normal-range quality. Hidden entirely outside it, regardless of Quality Display above.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> qualityRangeMin = sgGeneral.add(new IntSetting.Builder()
        .name("quality-range-min")
        .description("Minimum Quality % to show, when Quality Range is enabled.")
        .defaultValue(100)
        .min(0)
        .sliderRange(0, 200)
        .visible(qualityRangeEnabled::get)
        .build()
    );

    private final Setting<Integer> qualityRangeMax = sgGeneral.add(new IntSetting.Builder()
        .name("quality-range-max")
        .description("Maximum Quality % to show, when Quality Range is enabled.")
        .defaultValue(125)
        .min(0)
        .sliderRange(0, 200)
        .visible(qualityRangeEnabled::get)
        .build()
    );

    private final Setting<BadgeAnchor> qualityAnchor = sgGeneral.add(new EnumSetting.Builder<BadgeAnchor>()
        .name("quality-anchor")
        .description("Which corner of the icon the Quality badge is anchored to.")
        .defaultValue(BadgeAnchor.TopLeft)
        .build()
    );

    private final Setting<Integer> qualityLine = sgGeneral.add(new IntSetting.Builder()
        .name("quality-line")
        .description("Which row the Quality badge sits on, counting outward from its anchor corner (0 = the row closest to the corner). Defaults to 1 so it doesn't overlap the Ward badge on line 0 by default. Ignored while Quality Fills Ward's Spot below is active and Ward isn't showing.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<Boolean> qualityFillsWardSpot = sgGeneral.add(new BoolSetting.Builder()
        .name("quality-fills-ward-spot")
        .description("Whenever Ward isn't currently showing on an item (Ward Display is Off, its range filter excludes it, or it's keypress-gated and not currently held/toggled), Quality uses Ward's own anchor/line instead of its own - so it takes over the more prominent spot instead of leaving it empty.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> overrideAllScale = sgGeneral.add(new BoolSetting.Builder()
        .name("override-all-scale")
        .description("Uses Override Scale below for every badge (Ward, Quality, Enchant, Attachment) instead of each badge's own scale setting.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> overrideScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("override-scale")
        .description("Size multiplier applied to every badge at once, when Override All Scale above is on - independent of the game's own GUI scale.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 2)
        .visible(overrideAllScale::get)
        .build()
    );

    private final Setting<Double> wardScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("ward-scale")
        .description("Size multiplier for the Ward badge, independent of the game's own GUI scale. Ignored while Override All Scale above is on.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 2)
        .visible(() -> !overrideAllScale.get())
        .build()
    );

    private final Setting<Double> qualityScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("quality-scale")
        .description("Size multiplier for the Quality badge, independent of the game's own GUI scale. Ignored while Override All Scale above is on.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 2)
        .visible(() -> !overrideAllScale.get())
        .build()
    );

    private final Setting<Double> enchantScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("enchant-scale")
        .description("Size multiplier for the Enchant badge, independent of the game's own GUI scale. Ignored while Override All Scale above is on.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 2)
        .visible(() -> !overrideAllScale.get())
        .build()
    );

    private final Setting<Double> attachmentScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("attachment-scale")
        .description("Size multiplier for the Attachment badge, independent of the game's own GUI scale. Ignored while Override All Scale above is on.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 2)
        .visible(() -> !overrideAllScale.get())
        .build()
    );

    private final Setting<Boolean> overrideAllAlpha = sgAppearance.add(new BoolSetting.Builder()
        .name("override-all-alpha")
        .description("Uses Override Alpha below for every badge (Ward, Quality, Enchant, Attachment) instead of each badge's own alpha setting.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> overrideAlpha = sgAppearance.add(new IntSetting.Builder()
        .name("override-alpha")
        .description("Transparency applied to every badge at once, when Override All Alpha above is on - 255 is fully opaque, 0 is invisible.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .visible(overrideAllAlpha::get)
        .build()
    );

    private final Setting<Integer> wardAlpha = sgAppearance.add(new IntSetting.Builder()
        .name("ward-alpha")
        .description("Transparency for the Ward badge - 255 is fully opaque, 0 is invisible. Ignored while Override All Alpha above is on.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .visible(() -> !overrideAllAlpha.get())
        .build()
    );

    private final Setting<Integer> qualityAlpha = sgAppearance.add(new IntSetting.Builder()
        .name("quality-alpha")
        .description("Transparency for the Quality badge - 255 is fully opaque, 0 is invisible. Ignored while Override All Alpha above is on.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .visible(() -> !overrideAllAlpha.get())
        .build()
    );

    private final Setting<Integer> enchantAlpha = sgAppearance.add(new IntSetting.Builder()
        .name("enchant-alpha")
        .description("Transparency for the Enchant badge - 255 is fully opaque, 0 is invisible. Ignored while Override All Alpha above is on.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .visible(() -> !overrideAllAlpha.get())
        .build()
    );

    private final Setting<Integer> attachmentAlpha = sgAppearance.add(new IntSetting.Builder()
        .name("attachment-alpha")
        .description("Transparency for the Attachment badge - 255 is fully opaque, 0 is invisible. Ignored while Override All Alpha above is on.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .visible(() -> !overrideAllAlpha.get())
        .build()
    );

    private final Setting<ColorMode> wardColorMode = sgAppearance.add(new EnumSetting.Builder<ColorMode>()
        .name("ward-color-mode")
        .description("Lore Color: whatever color the server put on the ⚡ number in the item's own lore. Custom: always use Ward Custom Color below instead.")
        .defaultValue(ColorMode.LoreColor)
        .build()
    );

    private final Setting<SettingColor> wardCustomColor = sgAppearance.add(new ColorSetting.Builder()
        .name("ward-custom-color")
        .description("Color for the Ward badge, when Ward Color Mode above is Custom.")
        .defaultValue(new SettingColor(85, 85, 255))
        .visible(() -> wardColorMode.get() == ColorMode.Custom)
        .build()
    );

    private final Setting<ColorMode> qualityColorMode = sgAppearance.add(new EnumSetting.Builder<ColorMode>()
        .name("quality-color-mode")
        .description("Lore Color: whatever color the server put on the percent in the item's own lore (already reflects its quality tier). Custom: always use Quality Custom Color below instead.")
        .defaultValue(ColorMode.LoreColor)
        .build()
    );

    private final Setting<SettingColor> qualityCustomColor = sgAppearance.add(new ColorSetting.Builder()
        .name("quality-custom-color")
        .description("Color for the Quality badge, when Quality Color Mode above is Custom.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> qualityColorMode.get() == ColorMode.Custom)
        .build()
    );

    // No Lore Color option here (unlike Ward/Quality above) - the server doesn't color the
    // Enchantment/Attachment name portion of the lore line as its own distinct segment the way it
    // does the Ward/Quality numbers, so there's no single reliable "the name's own color" to read.
    private final Setting<SymbolColorMode> enchantColorMode = sgAppearance.add(new EnumSetting.Builder<SymbolColorMode>()
        .name("enchant-color-mode")
        .description("How the Enchant badge picks its color. Custom: Enchant Custom Color below, unchanging. Rainbow/Gradient/Flashing/Hue Shift: its own animated color, with dedicated settings in the Enchant Color group below.")
        .defaultValue(SymbolColorMode.Custom)
        .build()
    );

    private final Setting<SettingColor> enchantCustomColor = sgAppearance.add(new ColorSetting.Builder()
        .name("enchant-custom-color")
        .description("Color for the Enchant badge, when Enchant Color Mode above is Custom.")
        .defaultValue(new SettingColor(255, 85, 85))
        .visible(() -> enchantColorMode.get() == SymbolColorMode.Custom)
        .build()
    );

    private final Setting<SymbolColorMode> attachmentColorMode = sgAppearance.add(new EnumSetting.Builder<SymbolColorMode>()
        .name("attachment-color-mode")
        .description("How the Attachment badge picks its color. Custom: Attachment Custom Color below, unchanging. Rainbow/Gradient/Flashing/Hue Shift: its own animated color, with dedicated settings in the Attachment Color group below.")
        .defaultValue(SymbolColorMode.Custom)
        .build()
    );

    private final Setting<SettingColor> attachmentCustomColor = sgAppearance.add(new ColorSetting.Builder()
        .name("attachment-custom-color")
        .description("Color for the Attachment badge, when Attachment Color Mode above is Custom.")
        .defaultValue(new SettingColor(255, 255, 85))
        .visible(() -> attachmentColorMode.get() == SymbolColorMode.Custom)
        .build()
    );

    // Enchant Color (Rainbow/Gradient/Flashing/Hue Shift) - fully independent of Attachment
    // Color's own copies of the same settings below, even when both pick the same mode.

    private final Setting<RainbowTransition> enchantRainbowTransition = sgEnchantColor.add(new EnumSetting.Builder<RainbowTransition>()
        .name("enchant-rainbow-transition")
        .description("Soft smoothly cycles hue. Hard jumps between a fixed number of steps.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> enchantColorMode.get() == SymbolColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> enchantRainbowSteps = sgEnchantColor.add(new IntSetting.Builder()
        .name("enchant-rainbow-steps")
        .description("Number of distinct colors, when Enchant Rainbow Transition above is Hard.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 24)
        .visible(() -> enchantColorMode.get() == SymbolColorMode.Rainbow && enchantRainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> enchantRainbowSpeed = sgEnchantColor.add(new DoubleSetting.Builder()
        .name("enchant-rainbow-speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> enchantColorMode.get() == SymbolColorMode.Rainbow)
        .build()
    );

    private final Setting<List<SettingColor>> enchantGradientColors = sgEnchantColor.add(new ColorListSetting.Builder()
        .name("enchant-gradient-colors")
        .description("Colors the Enchant badge cycles through, in order.")
        .defaultValue(List.of(new SettingColor(255, 85, 85), new SettingColor(85, 85, 255)))
        .visible(() -> enchantColorMode.get() == SymbolColorMode.Gradient)
        .build()
    );

    private final Setting<Double> enchantGradientSpeed = sgEnchantColor.add(new DoubleSetting.Builder()
        .name("enchant-gradient-speed")
        .description("How fast the gradient cycles.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> enchantColorMode.get() == SymbolColorMode.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> enchantFlashColors = sgEnchantColor.add(new TimedColorListSetting.Builder()
        .name("enchant-flash-colors")
        .description("Colors the Enchant badge flashes between, each held for its own duration (in ticks).")
        .defaultValue(List.of())
        .visible(() -> enchantColorMode.get() == SymbolColorMode.Flashing)
        .build()
    );

    private final Setting<SettingColor> enchantHueShiftBaseColor = sgEnchantColor.add(new ColorSetting.Builder()
        .name("enchant-hue-shift-base-color")
        .description("Starting color - its hue continuously swings, keeping its saturation/brightness/alpha.")
        .defaultValue(new SettingColor(255, 85, 85))
        .visible(() -> enchantColorMode.get() == SymbolColorMode.HueShift)
        .build()
    );

    private final Setting<Double> enchantHueShiftSpeed = sgEnchantColor.add(new DoubleSetting.Builder()
        .name("enchant-hue-shift-speed")
        .description("How fast the hue swings.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> enchantColorMode.get() == SymbolColorMode.HueShift)
        .build()
    );

    private final Setting<Double> enchantHueShiftRange = sgEnchantColor.add(new DoubleSetting.Builder()
        .name("enchant-hue-shift-range")
        .description("How far the hue swings from the base color, in degrees each direction. 180 = swings the full way around.")
        .defaultValue(30)
        .min(0)
        .sliderRange(0, 180)
        .visible(() -> enchantColorMode.get() == SymbolColorMode.HueShift)
        .build()
    );

    // Attachment Color (Rainbow/Gradient/Flashing/Hue Shift) - fully independent of Enchant
    // Color's own copies of the same settings above, even when both pick the same mode.

    private final Setting<RainbowTransition> attachmentRainbowTransition = sgAttachmentColor.add(new EnumSetting.Builder<RainbowTransition>()
        .name("attachment-rainbow-transition")
        .description("Soft smoothly cycles hue. Hard jumps between a fixed number of steps.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> attachmentColorMode.get() == SymbolColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> attachmentRainbowSteps = sgAttachmentColor.add(new IntSetting.Builder()
        .name("attachment-rainbow-steps")
        .description("Number of distinct colors, when Attachment Rainbow Transition above is Hard.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 24)
        .visible(() -> attachmentColorMode.get() == SymbolColorMode.Rainbow && attachmentRainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> attachmentRainbowSpeed = sgAttachmentColor.add(new DoubleSetting.Builder()
        .name("attachment-rainbow-speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> attachmentColorMode.get() == SymbolColorMode.Rainbow)
        .build()
    );

    private final Setting<List<SettingColor>> attachmentGradientColors = sgAttachmentColor.add(new ColorListSetting.Builder()
        .name("attachment-gradient-colors")
        .description("Colors the Attachment badge cycles through, in order.")
        .defaultValue(List.of(new SettingColor(255, 255, 85), new SettingColor(85, 255, 85)))
        .visible(() -> attachmentColorMode.get() == SymbolColorMode.Gradient)
        .build()
    );

    private final Setting<Double> attachmentGradientSpeed = sgAttachmentColor.add(new DoubleSetting.Builder()
        .name("attachment-gradient-speed")
        .description("How fast the gradient cycles.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> attachmentColorMode.get() == SymbolColorMode.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> attachmentFlashColors = sgAttachmentColor.add(new TimedColorListSetting.Builder()
        .name("attachment-flash-colors")
        .description("Colors the Attachment badge flashes between, each held for its own duration (in ticks).")
        .defaultValue(List.of())
        .visible(() -> attachmentColorMode.get() == SymbolColorMode.Flashing)
        .build()
    );

    private final Setting<SettingColor> attachmentHueShiftBaseColor = sgAttachmentColor.add(new ColorSetting.Builder()
        .name("attachment-hue-shift-base-color")
        .description("Starting color - its hue continuously swings, keeping its saturation/brightness/alpha.")
        .defaultValue(new SettingColor(255, 255, 85))
        .visible(() -> attachmentColorMode.get() == SymbolColorMode.HueShift)
        .build()
    );

    private final Setting<Double> attachmentHueShiftSpeed = sgAttachmentColor.add(new DoubleSetting.Builder()
        .name("attachment-hue-shift-speed")
        .description("How fast the hue swings.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> attachmentColorMode.get() == SymbolColorMode.HueShift)
        .build()
    );

    private final Setting<Double> attachmentHueShiftRange = sgAttachmentColor.add(new DoubleSetting.Builder()
        .name("attachment-hue-shift-range")
        .description("How far the hue swings from the base color, in degrees each direction. 180 = swings the full way around.")
        .defaultValue(30)
        .min(0)
        .sliderRange(0, 180)
        .visible(() -> attachmentColorMode.get() == SymbolColorMode.HueShift)
        .build()
    );

    private final Setting<List<String>> enchantSymbols = sgAppearance.add(new StringListSetting.Builder()
        .name("enchant-symbols")
        .description("Extra/override Enchantment name-to-symbol mappings, one per entry, format \"Name=Symbol\" (e.g. \"Frost=❄\"). Takes priority over the built-in list below - add a new server enchant here, or override one of the built-in symbols. An Enchantment name matching neither this list nor the built-in one shows no badge.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<List<String>> attachmentSymbols = sgAppearance.add(new StringListSetting.Builder()
        .name("attachment-symbols")
        .description("Extra/override special-Attachment name-to-symbol mappings, one per entry, format \"Name=Symbol\" (e.g. \"Powershot=⇗\"). Takes priority over the built-in list below - add a new server attachment here, or override one of the built-in symbols. An Attachment name matching neither this list nor the built-in one shows no badge (this is how plain stat attachments like Gold Bonus/Health Regen/etc get filtered out automatically - only add the ones you actually want a badge for).")
        .defaultValue(List.of())
        .build()
    );

    // Disabled (see drawBadge()) - CustomFontRenderer's immediate-mode GL draw call corrupts
    // vanilla's batched rendering when run from here, making items go invisible. Settings kept
    // (rather than removed) so a saved "on" value doesn't get silently discarded, but they no
    // longer do anything.
    private final Setting<Boolean> useCustomFont = VLPlusAdditions.markExperimental(sgAppearance.add(new BoolSetting.Builder()
        .name("use-custom-font")
        .description("Currently disabled - was causing items to render invisible. Renders badge text in a chosen font instead of the vanilla one.")
        .defaultValue(false)
        .build()
    ));

    private final Setting<FontFace> font = sgAppearance.add(new FontFaceSetting.Builder()
        .name("font")
        .description("Font to render badge text in, when Use Custom Font above is on. Currently unused - see Use Custom Font.")
        .visible(useCustomFont::get)
        .build()
    );

    private final Setting<DisplayMode> enchantDisplay = sgGeneral.add(new EnumSetting.Builder<DisplayMode>()
        .name("enchant-display")
        .description("When to show a symbol badge for the item's active Enchantment (see Enchant Symbols below for the name-to-symbol mapping). Shows nothing for an Enchantment name it doesn't recognize.")
        .defaultValue(DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<Keybind> enchantKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("enchant-key")
        .description("Key for Enchant Display's On Keypress, independent of the other badges' own keys.")
        .defaultValue(Keybind.none())
        .visible(() -> enchantDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<KeyMode> enchantKeyMode = sgGeneral.add(new EnumSetting.Builder<KeyMode>()
        .name("enchant-key-mode")
        .description("Hold shows the Enchant badge only while the key is held. Toggle flips it on/off each press.")
        .defaultValue(KeyMode.Hold)
        .visible(() -> enchantDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<BadgeAnchor> enchantAnchor = sgGeneral.add(new EnumSetting.Builder<BadgeAnchor>()
        .name("enchant-anchor")
        .description("Which corner of the icon the Enchant badge is anchored to.")
        .defaultValue(BadgeAnchor.BottomLeft)
        .build()
    );

    private final Setting<Integer> enchantLine = sgGeneral.add(new IntSetting.Builder()
        .name("enchant-line")
        .description("Which row the Enchant badge sits on, counting outward from its anchor corner (0 = the row closest to the corner).")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<DisplayMode> attachmentDisplay = sgGeneral.add(new EnumSetting.Builder<DisplayMode>()
        .name("attachment-display")
        .description("When to show a badge for how many active (non-\"Inactive\") Attachment lines the item's lore has.")
        .defaultValue(DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<Keybind> attachmentKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("attachment-key")
        .description("Key for Attachment Display's On Keypress, independent of the other badges' own keys.")
        .defaultValue(Keybind.none())
        .visible(() -> attachmentDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<KeyMode> attachmentKeyMode = sgGeneral.add(new EnumSetting.Builder<KeyMode>()
        .name("attachment-key-mode")
        .description("Hold shows the Attachment badge only while the key is held. Toggle flips it on/off each press.")
        .defaultValue(KeyMode.Hold)
        .visible(() -> attachmentDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<BadgeAnchor> attachmentAnchor = sgGeneral.add(new EnumSetting.Builder<BadgeAnchor>()
        .name("attachment-anchor")
        .description("Which corner of the icon the Attachment badge is anchored to.")
        .defaultValue(BadgeAnchor.BottomRight)
        .build()
    );

    private final Setting<Integer> attachmentLine = sgGeneral.add(new IntSetting.Builder()
        .name("attachment-line")
        .description("Which row the Attachment badge sits on, counting outward from its anchor corner (0 = the row closest to the corner).")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<DisplayMode> durabilityBarDisplay = sgDurabilityBar.add(new EnumSetting.Builder<DisplayMode>()
        .name("bar-display")
        .description("When to repurpose the vanilla durability bar to visualize Quality % - independent of Quality Display above (which only controls the text badge). Off leaves vanilla's own damage-based bar untouched.")
        .defaultValue(DisplayMode.Always)
        .build()
    );

    private final Setting<Keybind> durabilityBarKey = sgDurabilityBar.add(new KeybindSetting.Builder()
        .name("bar-key")
        .description("Key for Bar Display's On Keypress, independent of the badges' own keys.")
        .defaultValue(Keybind.none())
        .visible(() -> durabilityBarDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<KeyMode> durabilityBarKeyMode = sgDurabilityBar.add(new EnumSetting.Builder<KeyMode>()
        .name("bar-key-mode")
        .description("Hold shows the bar override only while the key is held. Toggle flips it on/off each press.")
        .defaultValue(KeyMode.Hold)
        .visible(() -> durabilityBarDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<DurabilityBarMode> durabilityBarMode = sgDurabilityBar.add(new EnumSetting.Builder<DurabilityBarMode>()
        .name("bar-mode")
        .description("Proportional Headroom: the bar's full length represents the item's own Max Quality (from its lore) plus Max Bonus % below, so a normal (non-enhanced) item only fills part of the bar, leaving visible room for what a Super Enhancer could add. Bonus Only: the bar instead only represents the enhancement bonus itself - empty until Quality % actually exceeds the item's own Max Quality, then fills based on how much bonus is present, up to Max Bonus %.")
        .defaultValue(DurabilityBarMode.ProportionalHeadroom)
        .build()
    );

    private final Setting<Integer> maxBonusPercent = sgDurabilityBar.add(new IntSetting.Builder()
        .name("max-bonus-percent")
        .description("How far beyond an item's own Max Quality a Super Enhancer can push it (e.g. 10 for up to +10%). Not printed on the item itself, so this is an estimate you set - unlike Min/Max Quality, which are read directly from each item's own lore. Dev-only (.dev) - not something normal use needs to touch.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 50)
        .visible(() -> Modules.get().isDevUnlocked())
        .build()
    );

    private boolean wardToggledOn = false;
    private boolean qualityToggledOn = false;
    private boolean enchantToggledOn = false;
    private boolean attachmentToggledOn = false;
    private boolean durabilityBarToggledOn = false;

    public ItemInfo() {
        super(Categories.Render, "item-info", "Shows Ward, Quality, Enchant and Attachment info from item lore as badges on item icons.");
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (event.action != KeyAction.Press) return;

        if (wardKeyMode.get() == KeyMode.Toggle && wardKey.get().isSet() && wardKey.get().matches(true, event.key, event.modifiers)) wardToggledOn = !wardToggledOn;
        if (qualityKeyMode.get() == KeyMode.Toggle && qualityKey.get().isSet() && qualityKey.get().matches(true, event.key, event.modifiers)) qualityToggledOn = !qualityToggledOn;
        if (enchantKeyMode.get() == KeyMode.Toggle && enchantKey.get().isSet() && enchantKey.get().matches(true, event.key, event.modifiers)) enchantToggledOn = !enchantToggledOn;
        if (attachmentKeyMode.get() == KeyMode.Toggle && attachmentKey.get().isSet() && attachmentKey.get().matches(true, event.key, event.modifiers)) attachmentToggledOn = !attachmentToggledOn;
        if (durabilityBarKeyMode.get() == KeyMode.Toggle && durabilityBarKey.get().isSet() && durabilityBarKey.get().matches(true, event.key, event.modifiers)) durabilityBarToggledOn = !durabilityBarToggledOn;
    }

    private boolean isKeyActive(Setting<Keybind> keySetting, Setting<KeyMode> keyModeSetting, boolean toggledOn) {
        if (!keySetting.get().isSet()) return false;
        return keyModeSetting.get() == KeyMode.Toggle ? toggledOn : keySetting.get().isPressed();
    }

    private boolean shouldShow(DisplayMode mode, boolean keyActive) {
        return switch (mode) {
            case Always -> true;
            case OnKeypress -> keyActive;
            case Off -> false;
        };
    }

    private boolean wardInRange(int value) {
        return !wardRangeEnabled.get() || (value >= wardRangeMin.get() && value <= wardRangeMax.get());
    }

    private boolean qualityInRange(int value) {
        return !qualityRangeEnabled.get() || (value >= qualityRangeMin.get() && value <= qualityRangeMax.get());
    }

    private static final String ENCHANT_PREFIX = "Enchantment | ";
    private static final String ATTACHMENT_PREFIX = "Attachment | ";

    public void renderOverlay(DrawContext context, TextRenderer textRenderer, ItemStack stack, int x, int y) {
        QualityInfo info = findQualityInfo(stack);

        boolean showWard = info != null && info.ward() != null
            && shouldShow(wardDisplay.get(), isKeyActive(wardKey, wardKeyMode, wardToggledOn)) && wardInRange(info.ward());
        boolean showQuality = info != null && info.qualityPercent() != null
            && shouldShow(qualityDisplay.get(), isKeyActive(qualityKey, qualityKeyMode, qualityToggledOn)) && qualityInRange(info.qualityPercent());

        if (showWard) {
            int color = wardColorMode.get() == ColorMode.Custom ? wardCustomColor.get().getPacked() : (info.wardColor() | 0xFF000000);
            drawBadge(context, textRenderer, "⚡" + info.ward(), x, y, wardAnchor.get(), wardLine.get(), color, resolveAlpha(wardAlpha), resolveScale(wardScale));
        }

        if (showQuality) {
            int color = qualityColorMode.get() == ColorMode.Custom ? qualityCustomColor.get().getPacked() : (info.qualityColor() | 0xFF000000);

            boolean fillWardSpot = !showWard && qualityFillsWardSpot.get();
            BadgeAnchor anchor = fillWardSpot ? wardAnchor.get() : qualityAnchor.get();
            int line = fillWardSpot ? wardLine.get() : qualityLine.get();

            drawBadge(context, textRenderer, info.qualityPercent() + "%", x, y, anchor, line, color, resolveAlpha(qualityAlpha), resolveScale(qualityScale));
        }

        boolean showEnchant = shouldShow(enchantDisplay.get(), isKeyActive(enchantKey, enchantKeyMode, enchantToggledOn));
        boolean showAttachment = shouldShow(attachmentDisplay.get(), isKeyActive(attachmentKey, attachmentKeyMode, attachmentToggledOn));

        if (showEnchant) {
            NameInfo enchant = findLoreName(stack, ENCHANT_PREFIX, true);
            String symbol = enchant != null ? resolveSymbol(enchant.name(), enchantSymbols.get(), BUILTIN_ENCHANT_SYMBOLS) : null;

            if (symbol != null) {
                int color = getEnchantColor();
                drawBadge(context, textRenderer, symbol, x, y, enchantAnchor.get(), enchantLine.get(), color, resolveAlpha(enchantAlpha), resolveScale(enchantScale));
            }
        }

        if (showAttachment) {
            NameInfo attachment = findLoreName(stack, ATTACHMENT_PREFIX, false);
            String symbol = attachment != null ? resolveSymbol(attachment.name(), attachmentSymbols.get(), BUILTIN_ATTACHMENT_SYMBOLS) : null;

            if (symbol != null) {
                int color = getAttachmentColor();
                drawBadge(context, textRenderer, symbol, x, y, attachmentAnchor.get(), attachmentLine.get(), color, resolveAlpha(attachmentAlpha), resolveScale(attachmentScale));
            }
        }
    }

    private int getEnchantColor() {
        Color color = switch (enchantColorMode.get()) {
            case Custom -> enchantCustomColor.get();
            case Rainbow -> rainbowColor(enchantRainbowTransition.get(), enchantRainbowSteps.get(), enchantRainbowSpeed.get());
            case Gradient -> gradientColor(enchantGradientColors.get(), enchantGradientSpeed.get());
            case Flashing -> flashColor(enchantFlashColors.get(), enchantCustomColor.get());
            case HueShift -> hueShiftColor(enchantHueShiftBaseColor.get(), enchantHueShiftSpeed.get(), enchantHueShiftRange.get());
        };

        return color.getPacked() | 0xFF000000;
    }

    private int getAttachmentColor() {
        Color color = switch (attachmentColorMode.get()) {
            case Custom -> attachmentCustomColor.get();
            case Rainbow -> rainbowColor(attachmentRainbowTransition.get(), attachmentRainbowSteps.get(), attachmentRainbowSpeed.get());
            case Gradient -> gradientColor(attachmentGradientColors.get(), attachmentGradientSpeed.get());
            case Flashing -> flashColor(attachmentFlashColors.get(), attachmentCustomColor.get());
            case HueShift -> hueShiftColor(attachmentHueShiftBaseColor.get(), attachmentHueShiftSpeed.get(), attachmentHueShiftRange.get());
        };

        return color.getPacked() | 0xFF000000;
    }

    // Shared math only - Enchant Color and Attachment Color each bring their own fully independent
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

    private double resolveScale(Setting<Double> perBadgeScale) {
        return overrideAllScale.get() ? overrideScale.get() : perBadgeScale.get();
    }

    private int resolveAlpha(Setting<Integer> perBadgeAlpha) {
        return overrideAllAlpha.get() ? overrideAlpha.get() : perBadgeAlpha.get();
    }

    /**
     * iconX/iconY are the item icon's own top-left corner (screen coords) - anchor picks which
     * corner of the 16x16 icon the badge hugs (also its text alignment), and line counts rows
     * outward from that corner, so multiple badges sharing a corner can be told apart without
     * this module guessing at a stacking order itself.
     *
     * Z 190 - just under vanilla's own count-text/durability-bar convention of Z 200 (the icon
     * itself renders at Z 150, see DrawContext.drawItem, and vanilla bumps to Z 200 before drawing
     * its own overlays so they land on top of the icon) - keeps badges above the bare icon but
     * lets the vanilla durability bar (repurposed for Quality%, see getDurabilityBarOverride) draw
     * over badge text instead of under it, when they occupy the same corner.
     */
    private void drawBadge(DrawContext context, TextRenderer textRenderer, String text, int iconX, int iconY, BadgeAnchor anchor, int line, int argb, int alpha, double scale) {
        argb = withAlpha(argb, alpha);

        // Use Custom Font disabled here regardless of the setting: CustomFontRenderer issues its
        // own immediate-mode GL draw call (bindTexture + mesh.render) instead of going through
        // DrawContext's batched vertex consumers. Called from this mixin context (mid vanilla
        // drawItemInSlot), that leaves the wrong texture bound for whatever vanilla draws next
        // when its own batch flushes - not just badges rendering wrong, but the item icon itself
        // (and others after it) going invisible. Safe everywhere else this renderer is used
        // (Meteor's own HUD elements), since those run in their own dedicated end-of-frame render
        // pass instead of interleaved with vanilla's batching.
        boolean rightAligned = anchor == BadgeAnchor.TopRight || anchor == BadgeAnchor.BottomRight;
        boolean fromBottom = anchor == BadgeAnchor.BottomLeft || anchor == BadgeAnchor.BottomRight;

        double lineHeight = VANILLA_LINE_HEIGHT * scale;
        double anchorX = iconX + (rightAligned ? 15 : 1);
        double anchorY = fromBottom ? iconY + 15 - lineHeight - line * lineHeight : iconY + 1 + line * lineHeight;

        MatrixStack matrices = context.getMatrices();
        int drawX = rightAligned ? -textRenderer.getWidth(text) : 0;

        matrices.push();
        matrices.translate(anchorX, anchorY, 190);
        matrices.scale((float) scale, (float) scale, 1f);
        context.drawText(textRenderer, text, drawX, 0, argb, true);
        matrices.pop();
    }

    private int withAlpha(int argb, int alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * (alpha / 255f));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    private record NameInfo(String name, int color) {
    }

    /**
     * Finds the first lore line starting with prefix, extracts the name portion (between the
     * prefix and the following ": ") along with that portion's own lore color, and - for
     * Enchantment lines only, since Attachment names in lore never carry one - strips a trailing
     * tier (" III") so every tier of the same type resolves to one symbol. Returns null if the
     * item has no such line.
     */
    private NameInfo findLoreName(ItemStack stack, String prefix, boolean stripTier) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        for (Text line : lore.lines()) {
            String full = line.getString();
            if (!full.startsWith(prefix)) continue;

            String rest = full.substring(prefix.length());
            int colon = rest.indexOf(':');
            String name = (colon >= 0 ? rest.substring(0, colon) : rest).trim();
            if (stripTier) name = TRAILING_TIER_PATTERN.matcher(name).replaceFirst("");

            int[] color = {0xFFFFFF};
            line.visit((style, asString) -> {
                if (style.getColor() != null) color[0] = style.getColor().getRgb();
                return Optional.empty();
            }, Style.EMPTY);

            return new NameInfo(name, color[0]);
        }

        return null;
    }

    /** Custom overrides (format "Name=Symbol") take priority over the built-in table. Returns null if name matches neither - the caller shows no badge in that case. */
    private String resolveSymbol(String name, List<String> customEntries, Map<String, String> builtin) {
        for (String entry : customEntries) {
            int eq = entry.indexOf('=');
            if (eq < 0) continue;

            if (entry.substring(0, eq).trim().equalsIgnoreCase(name)) {
                String symbol = entry.substring(eq + 1).trim();
                if (!symbol.isEmpty()) return symbol;
            }
        }

        return builtin.get(name);
    }

    /** Returns null when Quality display/range doesn't apply, or the item has no Quality line - leaves vanilla's own bar untouched either way. */
    public QualityInfo getDurabilityBarOverride(ItemStack stack) {
        if (!shouldShow(durabilityBarDisplay.get(), isKeyActive(durabilityBarKey, durabilityBarKeyMode, durabilityBarToggledOn))) return null;

        QualityInfo info = findQualityInfo(stack);
        if (info == null || info.qualityPercent() == null) return null;
        if (!qualityInRange(info.qualityPercent())) return null;

        return info;
    }

    public int getBarStep(QualityInfo info) {
        int maxQuality = info.maxQuality() != null ? info.maxQuality() : 100;
        int bonus = maxBonusPercent.get();

        if (durabilityBarMode.get() == DurabilityBarMode.BonusOnly) {
            int over = info.qualityPercent() - maxQuality;
            return MathHelper.clamp(Math.round(13f * over / bonus), 0, 13);
        }

        int fullScale = maxQuality + bonus;
        return MathHelper.clamp(Math.round(13f * info.qualityPercent() / fullScale), 0, 13);
    }

    public int getBarColor(QualityInfo info) {
        int maxQuality = info.maxQuality() != null ? info.maxQuality() : 100;

        if (info.qualityPercent() > maxQuality) return info.qualityColor() | 0xFF000000;

        float f = MathHelper.clamp(info.qualityPercent() / (float) maxQuality, 0f, 1f);
        return MathHelper.hsvToRgb(f / 3f, 1f, 1f) | 0xFF000000;
    }

    private QualityInfo findQualityInfo(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        for (Text line : lore.lines()) {
            if (!line.getString().startsWith("Quality:")) continue;

            int[] qualityPercent = {-1};
            int[] qualityColor = {0xFFFFFF};
            int[] ward = {-1};
            int[] wardColor = {0xFFFFFF};
            int[] minQuality = {-1};
            int[] maxQuality = {-1};
            boolean[] seenPercent = {false};

            line.visit((style, asString) -> {
                Matcher qm = QUALITY_PATTERN.matcher(asString);
                if (qm.matches()) {
                    qualityPercent[0] = Integer.parseInt(qm.group(1));
                    if (style.getColor() != null) qualityColor[0] = style.getColor().getRgb();
                    seenPercent[0] = true;
                    return Optional.empty();
                }

                Matcher wm = WARD_PATTERN.matcher(asString);
                if (wm.matches()) {
                    ward[0] = Integer.parseInt(wm.group(1));
                    if (style.getColor() != null) wardColor[0] = style.getColor().getRgb();
                    return Optional.empty();
                }

                // Bare numbers flank the bar/percent - "78" (Min Quality) before it, "125" (Max
                // Quality) after - distinguished by whether the percent token has been seen yet,
                // since both are otherwise indistinguishable plain integers.
                Matcher bm = BARE_NUMBER_PATTERN.matcher(asString);
                if (bm.matches()) {
                    int value = Integer.parseInt(bm.group(1));
                    if (!seenPercent[0]) minQuality[0] = value;
                    else maxQuality[0] = value;
                }

                return Optional.empty();
            }, Style.EMPTY);

            if (qualityPercent[0] == -1 && ward[0] == -1) return null;

            return new QualityInfo(
                qualityPercent[0] == -1 ? null : qualityPercent[0], qualityColor[0],
                ward[0] == -1 ? null : ward[0], wardColor[0],
                minQuality[0] == -1 ? null : minQuality[0],
                maxQuality[0] == -1 ? null : maxQuality[0]
            );
        }

        return null;
    }

    public record QualityInfo(Integer qualityPercent, int qualityColor, Integer ward, int wardColor, Integer minQuality, Integer maxQuality) {
    }

    public enum DisplayMode {
        Always,
        OnKeypress,
        Off
    }

    public enum KeyMode {
        Hold,
        Toggle
    }

    public enum DurabilityBarMode {
        ProportionalHeadroom,
        BonusOnly
    }

    public enum ColorMode {
        LoreColor,
        Custom
    }

    public enum SymbolColorMode {
        Custom,
        Rainbow,
        Gradient,
        Flashing,
        HueShift
    }

    public enum RainbowTransition {
        Soft,
        Hard
    }

    /** Which corner of the 16x16 icon a badge is anchored to - also determines text alignment (Left = left-aligned, Right = right-aligned ending at the corner) and which direction Line counts from. */
    public enum BadgeAnchor {
        TopLeft,
        TopRight,
        BottomLeft,
        BottomRight
    }
}
