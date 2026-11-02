/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.gui.screens.settings.ColorRangeListScreen;
import meteordevelopment.meteorclient.renderer.text.FontFace;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.CheeseControl;
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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
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
 * cap (Min/Max Quality themselves can be overridden with a Custom Min/Max Threshold instead, if
 * you'd rather set the bar's own scale directly than trust what each item prints). When Bar
 * Display is on, the vanilla durability bar (normally damage-based) is repurposed to visualize
 * Quality (Bar Mode: Stacked, the default) - spanning that floor up to that ceiling, extended by
 * Max Bonus % if Include Super Enhancer Headroom is on. Ward gets its own guaranteed share of the
 * bar (Ward Headroom Weight) when Include Ward Headroom is on, instead of being added as a raw
 * point value the way it first was - Max Ward (10) is tiny next to Quality's own range (often
 * 100+), so that used to dilute Ward down to a fraction of one of the bar's 13 pixel-steps,
 * technically "included" but invisible in practice. Priority mode shows only whichever of Ward/
 * enhancer-bonus/plain-Quality is currently most relevant instead of one combined scale. Either
 * way the bar drops to a flat Min Floor Color once effective Quality (Quality % plus any active
 * Ward) reaches the floor, and otherwise colors with the server's own lore color so the bar and
 * badge always agree without this module having to guess the server's own tier thresholds. Max
 * Bonus % and Max Ward are both
 * known fixed constants (+10% and 10 respectively - Ward absorbs 10 Quality worth of drain before
 * real Quality starts dropping), not read from the item itself the way Min/Max Quality are.
 */
public class ItemInfo extends Module {
    private static final Pattern QUALITY_PATTERN = Pattern.compile("^(\\d+)%$");
    private static final Pattern WARD_PATTERN = Pattern.compile("^⚡(\\d+)$");
    private static final Pattern BARE_NUMBER_PATTERN = Pattern.compile("^(\\d+)\\s*$");
    private static final Pattern UNIQUE_TIER_PATTERN = Pattern.compile("^Tier\\s+(\\d+)\\s+Unique Item");
    // Tier 7's own lore line never actually says "Tier 7 Unique Item" like every other tier does -
    // it says "Cosmic Item [...]" instead, which the pattern above simply never matches. tierBadgeText()
    // already knew to *display* "Cosmic" for tier 7 - this is the other half, actually *detecting* one.
    private static final Pattern COSMIC_TIER_PATTERN = Pattern.compile("^Cosmic Item\\b");
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

    // General holds only what applies across every badge at once (the tier filters, and the
    // Override All Scale/Alpha pair) - everything specific to one badge now lives entirely in
    // that badge's own category below, scale/alpha/anchor/color included, instead of being spread
    // across a shared General/Appearance split that made every badge's settings hard to find.
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWard = settings.createGroup("Ward");
    private final SettingGroup sgQuality = settings.createGroup("Quality");
    private final SettingGroup sgTier = settings.createGroup("Tier");
    private final SettingGroup sgEnchant = settings.createGroup("Enchant");
    private final SettingGroup sgEnchantColor = settings.createGroup("Enchant Color (Rainbow/Gradient/Flashing/Hue Shift)");
    private final SettingGroup sgAttachment = settings.createGroup("Attachment");
    private final SettingGroup sgAttachmentColor = settings.createGroup("Attachment Color (Rainbow/Gradient/Flashing/Hue Shift)");
    private final SettingGroup sgDurabilityBar = settings.createGroup("Durability Bar");
    private final SettingGroup sgArtifact = settings.createGroup("Artifact");
    private final SettingGroup sgBootMod = settings.createGroup("Boot Mod");

    private final Setting<UniqueFilter> uniqueFilter = sgGeneral.add(new EnumSetting.Builder<UniqueFilter>()
        .name("unique-filter")
        .description("Only show Item Info's badges on unique items (lore line \"Tier N Unique Item\"), only on non-unique items, or on both.")
        .defaultValue(UniqueFilter.Any)
        .build()
    );

    private final Setting<Integer> minTier = sgGeneral.add(new IntSetting.Builder()
        .name("min-tier")
        .description("Hide badges on items below this tier (the N in \"Tier N Unique Item\"). Items with no tier line count as tier 0, so any value above 0 also hides them. 0 = off.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> maxTier = sgGeneral.add(new IntSetting.Builder()
        .name("max-tier")
        .description("Hide badges on items above this tier. 0 = off (no upper limit).")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<DisplayMode> wardDisplay = sgWard.add(new EnumSetting.Builder<DisplayMode>()
        .name("ward-display")
        .description("When to show the Ward (⚡) badge on items.")
        .defaultValue(DisplayMode.Always)
        .build()
    );

    private final Setting<Keybind> wardKey = sgWard.add(new KeybindSetting.Builder()
        .name("ward-key")
        .description("Key for Ward Display's On Keypress, independent of the other badges' own keys - so each badge can be revealed on its own, without covering the whole item at once.")
        .defaultValue(Keybind.none())
        .visible(() -> wardDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<KeyMode> wardKeyMode = sgWard.add(new EnumSetting.Builder<KeyMode>()
        .name("ward-key-mode")
        .description("Hold shows the Ward badge only while the key is held. Toggle flips it on/off each press.")
        .defaultValue(KeyMode.Hold)
        .visible(() -> wardDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<Boolean> wardRangeEnabled = sgWard.add(new BoolSetting.Builder()
        .name("ward-range-enabled")
        .description("Only shows the Ward badge when its number falls within the range below - hidden entirely outside it, regardless of Ward Display above.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> wardRangeMin = sgWard.add(new IntSetting.Builder()
        .name("ward-range-min")
        .description("Minimum Ward number to show, when Ward Range is enabled.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 20)
        .visible(wardRangeEnabled::get)
        .build()
    );

    private final Setting<Integer> wardRangeMax = sgWard.add(new IntSetting.Builder()
        .name("ward-range-max")
        .description("Maximum Ward number to show, when Ward Range is enabled.")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 20)
        .visible(wardRangeEnabled::get)
        .build()
    );

    private final Setting<BadgeAnchor> wardAnchor = sgWard.add(new EnumSetting.Builder<BadgeAnchor>()
        .name("ward-anchor")
        .description("Which corner of the icon the Ward badge is anchored to.")
        .defaultValue(BadgeAnchor.TopLeft)
        .build()
    );

    private final Setting<Integer> wardLine = sgWard.add(new IntSetting.Builder()
        .name("ward-line")
        .description("Which row the Ward badge sits on, counting outward from its anchor corner (0 = the row closest to the corner). Raise this to stack it further from the corner, e.g. to make room for another badge sharing the same corner.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<DisplayMode> qualityDisplay = sgQuality.add(new EnumSetting.Builder<DisplayMode>()
        .name("quality-display")
        .description("When to show the Quality % badge (and repurpose the durability bar to visualize it) on items.")
        .defaultValue(DisplayMode.Always)
        .build()
    );

    private final Setting<Keybind> qualityKey = sgQuality.add(new KeybindSetting.Builder()
        .name("quality-key")
        .description("Key for Quality Display's On Keypress, independent of the other badges' own keys.")
        .defaultValue(Keybind.none())
        .visible(() -> qualityDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<KeyMode> qualityKeyMode = sgQuality.add(new EnumSetting.Builder<KeyMode>()
        .name("quality-key-mode")
        .description("Hold shows the Quality badge only while the key is held. Toggle flips it on/off each press.")
        .defaultValue(KeyMode.Hold)
        .visible(() -> qualityDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<Boolean> qualityRangeEnabled = sgQuality.add(new BoolSetting.Builder()
        .name("quality-range-enabled")
        .description("Only shows the Quality % badge (and durability bar override) when the percent falls within the range below - e.g. 100-125 to only care about normal-range quality. Hidden entirely outside it, regardless of Quality Display above.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> qualityRangeMin = sgQuality.add(new IntSetting.Builder()
        .name("quality-range-min")
        .description("Minimum Quality % to show, when Quality Range is enabled.")
        .defaultValue(100)
        .min(0)
        .sliderRange(0, 200)
        .visible(qualityRangeEnabled::get)
        .build()
    );

    private final Setting<Integer> qualityRangeMax = sgQuality.add(new IntSetting.Builder()
        .name("quality-range-max")
        .description("Maximum Quality % to show, when Quality Range is enabled.")
        .defaultValue(135)
        .min(0)
        .sliderRange(0, 200)
        .visible(qualityRangeEnabled::get)
        .build()
    );

    private final Setting<BadgeAnchor> qualityAnchor = sgQuality.add(new EnumSetting.Builder<BadgeAnchor>()
        .name("quality-anchor")
        .description("Which corner of the icon the Quality badge is anchored to.")
        .defaultValue(BadgeAnchor.TopLeft)
        .build()
    );

    private final Setting<Integer> qualityLine = sgQuality.add(new IntSetting.Builder()
        .name("quality-line")
        .description("Which row the Quality badge sits on, counting outward from its anchor corner (0 = the row closest to the corner). Defaults to 1 so it doesn't overlap the Ward badge on line 0 by default. Ignored while Quality Fills Ward's Spot below is active and Ward isn't showing.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<Boolean> qualityFillsWardSpot = sgQuality.add(new BoolSetting.Builder()
        .name("quality-fills-ward-spot")
        .description("Whenever Ward isn't currently showing on an item (Ward Display is Off, its range filter excludes it, or it's keypress-gated and not currently held/toggled), Quality uses Ward's own anchor/line instead of its own - so it takes over the more prominent spot instead of leaving it empty.")
        .defaultValue(false)
        .build()
    );

    private final Setting<DisplayMode> tierDisplay = sgTier.add(new EnumSetting.Builder<DisplayMode>()
        .name("tier-display")
        .description("When to show the Tier badge (the N in \"Tier N Unique Item\" lore). Shows nothing on a non-unique item, regardless of this setting.")
        .defaultValue(DisplayMode.Always)
        .build()
    );

    private final Setting<String> tierExclude = sgTier.add(new StringSetting.Builder()
        .name("tier-exclude")
        .description("Comma-separated tier numbers to never show the Tier badge for (e.g. \"7\" to hide it on every Cosmic/Tier 7 item) - only suppresses this badge, the item's other badges (Quality, Ward, ...) are unaffected.")
        .defaultValue("")
        .build()
    );

    private final Setting<Keybind> tierKey = sgTier.add(new KeybindSetting.Builder()
        .name("tier-key")
        .description("Key for Tier Display's On Keypress, independent of the other badges' own keys.")
        .defaultValue(Keybind.none())
        .visible(() -> tierDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<KeyMode> tierKeyMode = sgTier.add(new EnumSetting.Builder<KeyMode>()
        .name("tier-key-mode")
        .description("Hold shows the Tier badge only while the key is held. Toggle flips it on/off each press.")
        .defaultValue(KeyMode.Hold)
        .visible(() -> tierDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<BadgeAnchor> tierAnchor = sgTier.add(new EnumSetting.Builder<BadgeAnchor>()
        .name("tier-anchor")
        .description("Which corner of the icon the Tier badge is anchored to.")
        .defaultValue(BadgeAnchor.TopRight)
        .build()
    );

    private final Setting<Integer> tierLine = sgTier.add(new IntSetting.Builder()
        .name("tier-line")
        .description("Which row the Tier badge sits on, counting outward from its anchor corner (0 = the row closest to the corner).")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 4)
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

    private final Setting<Double> wardScale = sgWard.add(new DoubleSetting.Builder()
        .name("ward-scale")
        .description("Size multiplier for the Ward badge, independent of the game's own GUI scale. Ignored while Override All Scale above is on.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 2)
        .visible(() -> !overrideAllScale.get())
        .build()
    );

    private final Setting<Double> tierScale = sgTier.add(new DoubleSetting.Builder()
        .name("tier-scale")
        .description("Size multiplier for the Tier badge, independent of the game's own GUI scale. Ignored while Override All Scale above is on.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 2)
        .visible(() -> !overrideAllScale.get())
        .build()
    );

    private final Setting<Double> qualityScale = sgQuality.add(new DoubleSetting.Builder()
        .name("quality-scale")
        .description("Size multiplier for the Quality badge, independent of the game's own GUI scale. Ignored while Override All Scale above is on.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 2)
        .visible(() -> !overrideAllScale.get())
        .build()
    );

    private final Setting<Double> enchantScale = sgEnchant.add(new DoubleSetting.Builder()
        .name("enchant-scale")
        .description("Size multiplier for the Enchant badge, independent of the game's own GUI scale. Ignored while Override All Scale above is on.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 2)
        .visible(() -> !overrideAllScale.get())
        .build()
    );

    private final Setting<Double> attachmentScale = sgAttachment.add(new DoubleSetting.Builder()
        .name("attachment-scale")
        .description("Size multiplier for the Attachment badge, independent of the game's own GUI scale. Ignored while Override All Scale above is on.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 2)
        .visible(() -> !overrideAllScale.get())
        .build()
    );

    private final Setting<Boolean> overrideAllAlpha = sgGeneral.add(new BoolSetting.Builder()
        .name("override-all-alpha")
        .description("Uses Override Alpha below for every badge (Ward, Quality, Enchant, Attachment) instead of each badge's own alpha setting.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> overrideAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("override-alpha")
        .description("Transparency applied to every badge at once, when Override All Alpha above is on - 255 is fully opaque, 0 is invisible.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .visible(overrideAllAlpha::get)
        .build()
    );

    private final Setting<Integer> wardAlpha = sgWard.add(new IntSetting.Builder()
        .name("ward-alpha")
        .description("Transparency for the Ward badge - 255 is fully opaque, 0 is invisible. Ignored while Override All Alpha above is on.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .visible(() -> !overrideAllAlpha.get())
        .build()
    );

    private final Setting<Integer> tierAlpha = sgTier.add(new IntSetting.Builder()
        .name("tier-alpha")
        .description("Transparency for the Tier badge - 255 is fully opaque, 0 is invisible. Ignored while Override All Alpha above is on.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .visible(() -> !overrideAllAlpha.get())
        .build()
    );

    private final Setting<Integer> qualityAlpha = sgQuality.add(new IntSetting.Builder()
        .name("quality-alpha")
        .description("Transparency for the Quality badge - 255 is fully opaque, 0 is invisible. Ignored while Override All Alpha above is on.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .visible(() -> !overrideAllAlpha.get())
        .build()
    );

    private final Setting<Integer> enchantAlpha = sgEnchant.add(new IntSetting.Builder()
        .name("enchant-alpha")
        .description("Transparency for the Enchant badge - 255 is fully opaque, 0 is invisible. Ignored while Override All Alpha above is on.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .visible(() -> !overrideAllAlpha.get())
        .build()
    );

    private final Setting<Integer> attachmentAlpha = sgAttachment.add(new IntSetting.Builder()
        .name("attachment-alpha")
        .description("Transparency for the Attachment badge - 255 is fully opaque, 0 is invisible. Ignored while Override All Alpha above is on.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .visible(() -> !overrideAllAlpha.get())
        .build()
    );

    private final Setting<ColorMode> wardColorMode = sgWard.add(new EnumSetting.Builder<ColorMode>()
        .name("ward-color-mode")
        .description("Lore Color: whatever color the server put on the ⚡ number in the item's own lore. Custom: always use Ward Custom Color below instead. By Value: blends from Ward Low Color (at the low value) to Ward High Color (at the high value) - wards run 1 to 10.")
        .defaultValue(ColorMode.LoreColor)
        .build()
    );

    private final Setting<Integer> wardLowValue = sgWard.add(new IntSetting.Builder()
        .name("ward-low-value")
        .description("The Ward number that gets fully the low color, for Ward Color Mode's By Value. Wards run 1 to 10.")
        .defaultValue(1)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(() -> wardColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<Integer> wardHighValue = sgWard.add(new IntSetting.Builder()
        .name("ward-high-value")
        .description("The Ward number that gets fully the high color, for Ward Color Mode's By Value. Wards run 1 to 10.")
        .defaultValue(10)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(() -> wardColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<Integer> wardMidValue = sgWard.add(new IntSetting.Builder()
        .name("ward-mid-value")
        .description("The Ward number that gets fully the mid color - clamped to sit between the low and high values. Wards run 1 to 10.")
        .defaultValue(5)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(() -> wardColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> wardLowColor = sgWard.add(new ColorSetting.Builder()
        .name("ward-low-color")
        .description("Ward color at the low value, for Ward Color Mode's By Value.")
        .defaultValue(new SettingColor(220, 30, 30))
        .visible(() -> wardColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> wardHighColor = sgWard.add(new ColorSetting.Builder()
        .name("ward-high-color")
        .description("Ward color at the high value, for Ward Color Mode's By Value.")
        .defaultValue(new SettingColor(30, 220, 30))
        .visible(() -> wardColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> wardMidColor = sgWard.add(new ColorSetting.Builder()
        .name("ward-mid-color")
        .description("Ward color at the mid value, for Ward Color Mode's By Value. Set it equal to a neighbouring color to effectively skip the mid step.")
        .defaultValue(new SettingColor(255, 140, 0))
        .visible(() -> wardColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<List<ColorRangeEntry>> wardRanges = sgWard.add(new ColorRangeListSetting.Builder()
        .name("ward-value-ranges")
        .description("Each band's own ward-number cutoff and color, for Ward Color Mode's By Value Ranges - a hard un-blended color per band instead of By Value's smooth blend. See Edit Ranges below.")
        .visible(() -> wardColorMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Void> wardRangesEdit = sgWard.add(new ButtonSetting.Builder()
        .name("ward-value-ranges-edit")
        .description("Add/edit/remove the ward-number bands above.")
        .buttonText("Edit Ranges")
        .screen(theme -> new ColorRangeListScreen(theme, "Ward", wardRanges.get(), 10))
        .visible(() -> wardColorMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<SettingColor> wardCustomColor = sgWard.add(new ColorSetting.Builder()
        .name("ward-custom-color")
        .description("Color for the Ward badge, when Ward Color Mode above is Custom.")
        .defaultValue(new SettingColor(85, 85, 255))
        .visible(() -> wardColorMode.get() == ColorMode.Custom)
        .build()
    );

    private final Setting<ColorMode> tierColorMode = sgTier.add(new EnumSetting.Builder<ColorMode>()
        .name("tier-color-mode")
        .description("Lore Color: whatever color the server put on the \"Tier N Unique Item\" line. Custom: always use Tier Custom Color below instead. By Value: blends from Tier Low Color (at the low value) to Tier High Color (at the high value).")
        .defaultValue(ColorMode.LoreColor)
        .build()
    );

    private final Setting<Integer> tierLowValue = sgTier.add(new IntSetting.Builder()
        .name("tier-low-value")
        .description("The Tier number that gets fully the low color, for Tier Color Mode's By Value.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .visible(() -> tierColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<Integer> tierHighValue = sgTier.add(new IntSetting.Builder()
        .name("tier-high-value")
        .description("The Tier number that gets fully the high color, for Tier Color Mode's By Value.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 10)
        .visible(() -> tierColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<Integer> tierMidValue = sgTier.add(new IntSetting.Builder()
        .name("tier-mid-value")
        .description("The Tier number that gets fully the mid color - clamped to sit between the low and high values.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 10)
        .visible(() -> tierColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> tierLowColor = sgTier.add(new ColorSetting.Builder()
        .name("tier-low-color")
        .description("Tier color at the low value, for Tier Color Mode's By Value.")
        .defaultValue(new SettingColor(150, 150, 150))
        .visible(() -> tierColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> tierHighColor = sgTier.add(new ColorSetting.Builder()
        .name("tier-high-color")
        .description("Tier color at the high value, for Tier Color Mode's By Value.")
        .defaultValue(new SettingColor(255, 170, 0))
        .visible(() -> tierColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> tierMidColor = sgTier.add(new ColorSetting.Builder()
        .name("tier-mid-color")
        .description("Tier color at the mid value, for Tier Color Mode's By Value. Set it equal to a neighbouring color to effectively skip the mid step.")
        .defaultValue(new SettingColor(255, 255, 85))
        .visible(() -> tierColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<List<ColorRangeEntry>> tierRanges = sgTier.add(new ColorRangeListSetting.Builder()
        .name("tier-value-ranges")
        .description("Each band's own tier-number cutoff and color, for Tier Color Mode's By Value Ranges - a hard un-blended color per band instead of By Value's smooth blend. See Edit Ranges below.")
        .visible(() -> tierColorMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Void> tierRangesEdit = sgTier.add(new ButtonSetting.Builder()
        .name("tier-value-ranges-edit")
        .description("Add/edit/remove the tier-number bands above.")
        .buttonText("Edit Ranges")
        .screen(theme -> new ColorRangeListScreen(theme, "Tier", tierRanges.get(), 10))
        .visible(() -> tierColorMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<SettingColor> tierCustomColor = sgTier.add(new ColorSetting.Builder()
        .name("tier-custom-color")
        .description("Color for the Tier badge, when Tier Color Mode above is Custom.")
        .defaultValue(new SettingColor(255, 170, 0))
        .visible(() -> tierColorMode.get() == ColorMode.Custom)
        .build()
    );

    private final Setting<QualityColorMode> qualityColorMode = sgQuality.add(new EnumSetting.Builder<QualityColorMode>()
        .name("quality-color-mode")
        .description("Lore Color: whatever color the server put on the percent in the item's own lore (already reflects its quality tier). Custom: always use Quality Custom Color below instead. By Value: blends between Quality Low Value Color and Quality High Value Color below based on the item's own quality percent (0% = low color, 100% = high color), independent of whatever color the server itself uses.")
        .defaultValue(QualityColorMode.LoreColor)
        .build()
    );

    private final Setting<SettingColor> qualityCustomColor = sgQuality.add(new ColorSetting.Builder()
        .name("quality-custom-color")
        .description("Color for the Quality badge, when Quality Color Mode above is Custom.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> qualityColorMode.get() == QualityColorMode.Custom)
        .build()
    );

    private final Setting<Integer> qualityLowThreshold = sgQuality.add(new IntSetting.Builder()
        .name("quality-low-threshold")
        .description("Quality value at/below which the badge is fully Quality Low Value Color below, when Quality Color Mode above is By Value - not necessarily 0, since quality isn't always a plain 0-100 percent on every server (e.g. set this to 115 if anything at or under 115 should already read as fully \"low\").")
        .defaultValue(0)
        .sliderRange(0, 300)
        .visible(() -> qualityColorMode.get() == QualityColorMode.ByValue)
        .build()
    );

    private final Setting<Integer> qualityHighThreshold = sgQuality.add(new IntSetting.Builder()
        .name("quality-high-threshold")
        .description("Quality value at/above which the badge is fully Quality High Value Color below, when Quality Color Mode above is By Value.")
        .defaultValue(100)
        .sliderRange(0, 300)
        .visible(() -> qualityColorMode.get() == QualityColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> qualityLowColor = sgQuality.add(new ColorSetting.Builder()
        .name("quality-low-value-color")
        .description("Color at/below Quality Low Threshold above, when Quality Color Mode above is By Value - blends toward Quality High Value Color below as the item's quality increases past it.")
        .defaultValue(new SettingColor(220, 30, 30))
        .visible(() -> qualityColorMode.get() == QualityColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> qualityHighColor = sgQuality.add(new ColorSetting.Builder()
        .name("quality-high-value-color")
        .description("Color at/above Quality High Threshold above, when Quality Color Mode above is By Value - Quality Low Value Color above blends toward this as the item's quality increases toward it.")
        .defaultValue(new SettingColor(30, 220, 30))
        .visible(() -> qualityColorMode.get() == QualityColorMode.ByValue)
        .build()
    );

    private final Setting<Boolean> qualityMidEnabled = sgQuality.add(new BoolSetting.Builder()
        .name("quality-mid-enabled")
        .description("Adds a middle color between Low and High for Quality Color Mode's By Value, instead of one straight blend from low to high.")
        .defaultValue(true)
        .visible(() -> qualityColorMode.get() == QualityColorMode.ByValue)
        .build()
    );

    private final Setting<Integer> qualityMidThreshold = sgQuality.add(new IntSetting.Builder()
        .name("quality-mid-threshold")
        .description("The Quality that gets fully the mid color - clamped to sit between the low and high thresholds.")
        .defaultValue(50)
        .min(0)
        .sliderRange(0, 300)
        .visible(() -> qualityColorMode.get() == QualityColorMode.ByValue && qualityMidEnabled.get())
        .build()
    );

    private final Setting<SettingColor> qualityMidColor = sgQuality.add(new ColorSetting.Builder()
        .name("quality-mid-value-color")
        .description("Quality color at the mid threshold, for Quality Color Mode's By Value.")
        .defaultValue(new SettingColor(255, 140, 0))
        .visible(() -> qualityColorMode.get() == QualityColorMode.ByValue && qualityMidEnabled.get())
        .build()
    );

    private final Setting<List<ColorRangeEntry>> qualityRanges = sgQuality.add(new ColorRangeListSetting.Builder()
        .name("quality-value-ranges")
        .description("Each band's own quality-value cutoff and color, for Quality Color Mode's By Value Ranges - a hard un-blended color per band instead of By Value's smooth blend. See Edit Ranges below.")
        .visible(() -> qualityColorMode.get() == QualityColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Void> qualityRangesEdit = sgQuality.add(new ButtonSetting.Builder()
        .name("quality-value-ranges-edit")
        .description("Add/edit/remove the quality-value bands above.")
        .buttonText("Edit Ranges")
        .screen(theme -> new ColorRangeListScreen(theme, "Quality", qualityRanges.get(), qualityHighThreshold.get()))
        .visible(() -> qualityColorMode.get() == QualityColorMode.ByValueRanges)
        .build()
    );

    private final Setting<SettingColor> qualitySuperColor = sgQuality.add(new ColorSetting.Builder()
        .name("quality-super-color")
        .description("Color used instead of the normal Low/High blend whenever the item's quality is actually above ITS OWN max quality (a bonus/\"Super Enhancer\" item, e.g. 115% quality on a 100% max item) - the same over-max condition Quality Durability Bar's Bonus Only mode already uses, just applied to this badge's color too. Takes priority over the blend regardless of Quality High Threshold above.")
        .defaultValue(new SettingColor(0, 220, 255))
        .visible(() -> qualityColorMode.get() == QualityColorMode.ByValue)
        .build()
    );

    // No Lore Color option here (unlike Ward/Quality above) - the server doesn't color the
    // Enchantment/Attachment name portion of the lore line as its own distinct segment the way it
    // does the Ward/Quality numbers, so there's no single reliable "the name's own color" to read.
    private final Setting<SymbolColorMode> enchantColorMode = sgEnchant.add(new EnumSetting.Builder<SymbolColorMode>()
        .name("enchant-color-mode")
        .description("How the Enchant badge picks its color. Custom: Enchant Custom Color below, unchanging. Rainbow/Gradient/Flashing/Hue Shift: its own animated color, with dedicated settings in the Enchant Color group below.")
        .defaultValue(SymbolColorMode.Custom)
        .build()
    );

    private final Setting<SettingColor> enchantCustomColor = sgEnchant.add(new ColorSetting.Builder()
        .name("enchant-custom-color")
        .description("Color for the Enchant badge, when Enchant Color Mode above is Custom.")
        .defaultValue(new SettingColor(255, 85, 85))
        .visible(() -> enchantColorMode.get() == SymbolColorMode.Custom)
        .build()
    );

    private final Setting<SymbolColorMode> attachmentColorMode = sgAttachment.add(new EnumSetting.Builder<SymbolColorMode>()
        .name("attachment-color-mode")
        .description("How the Attachment badge picks its color. Custom: Attachment Custom Color below, unchanging. Rainbow/Gradient/Flashing/Hue Shift: its own animated color, with dedicated settings in the Attachment Color group below.")
        .defaultValue(SymbolColorMode.Custom)
        .build()
    );

    private final Setting<SettingColor> attachmentCustomColor = sgAttachment.add(new ColorSetting.Builder()
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

    private final Setting<List<String>> enchantSymbols = sgEnchant.add(new StringListSetting.Builder()
        .name("enchant-symbols")
        .description("Extra/override Enchantment name-to-symbol mappings, one per entry, format \"Name=Symbol\" (e.g. \"Frost=❄\"). Takes priority over the built-in list below - add a new server enchant here, or override one of the built-in symbols. An Enchantment name matching neither this list nor the built-in one shows no badge.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<List<String>> attachmentSymbols = sgAttachment.add(new StringListSetting.Builder()
        .name("attachment-symbols")
        .description("Extra/override special-Attachment name-to-symbol mappings, one per entry, format \"Name=Symbol\" (e.g. \"Powershot=⇗\"). Takes priority over the built-in list below - add a new server attachment here, or override one of the built-in symbols. An Attachment name matching neither this list nor the built-in one shows no badge (this is how plain stat attachments like Gold Bonus/Health Regen/etc get filtered out automatically - only add the ones you actually want a badge for).")
        .defaultValue(List.of())
        .build()
    );

    // Disabled (see drawBadge()) - CustomFontRenderer's immediate-mode GL draw call corrupts
    // vanilla's batched rendering when run from here, making items go invisible. Settings kept
    // (rather than removed) so a saved "on" value doesn't get silently discarded, but they no
    // longer do anything.
    private final Setting<Boolean> useCustomFont = VLPlusAdditions.markExperimental(sgGeneral.add(new BoolSetting.Builder()
        .name("use-custom-font")
        .description("Currently disabled - was causing items to render invisible. Renders badge text in a chosen font instead of the vanilla one.")
        .defaultValue(false)
        .build()
    ));

    private final Setting<FontFace> font = sgGeneral.add(new FontFaceSetting.Builder()
        .name("font")
        .description("Font to render badge text in, when Use Custom Font above is on. Currently unused - see Use Custom Font.")
        .visible(useCustomFont::get)
        .build()
    );

    private final Setting<DisplayMode> enchantDisplay = sgEnchant.add(new EnumSetting.Builder<DisplayMode>()
        .name("enchant-display")
        .description("When to show a symbol badge for the item's active Enchantment (see Enchant Symbols below for the name-to-symbol mapping). Shows nothing for an Enchantment name it doesn't recognize.")
        .defaultValue(DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<Keybind> enchantKey = sgEnchant.add(new KeybindSetting.Builder()
        .name("enchant-key")
        .description("Key for Enchant Display's On Keypress, independent of the other badges' own keys.")
        .defaultValue(Keybind.none())
        .visible(() -> enchantDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<KeyMode> enchantKeyMode = sgEnchant.add(new EnumSetting.Builder<KeyMode>()
        .name("enchant-key-mode")
        .description("Hold shows the Enchant badge only while the key is held. Toggle flips it on/off each press.")
        .defaultValue(KeyMode.Hold)
        .visible(() -> enchantDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<BadgeAnchor> enchantAnchor = sgEnchant.add(new EnumSetting.Builder<BadgeAnchor>()
        .name("enchant-anchor")
        .description("Which corner of the icon the Enchant badge is anchored to.")
        .defaultValue(BadgeAnchor.BottomLeft)
        .build()
    );

    private final Setting<Integer> enchantLine = sgEnchant.add(new IntSetting.Builder()
        .name("enchant-line")
        .description("Which row the Enchant badge sits on, counting outward from its anchor corner (0 = the row closest to the corner).")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<DisplayMode> attachmentDisplay = sgAttachment.add(new EnumSetting.Builder<DisplayMode>()
        .name("attachment-display")
        .description("When to show a badge for how many active (non-\"Inactive\") Attachment lines the item's lore has.")
        .defaultValue(DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<Keybind> attachmentKey = sgAttachment.add(new KeybindSetting.Builder()
        .name("attachment-key")
        .description("Key for Attachment Display's On Keypress, independent of the other badges' own keys.")
        .defaultValue(Keybind.none())
        .visible(() -> attachmentDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<KeyMode> attachmentKeyMode = sgAttachment.add(new EnumSetting.Builder<KeyMode>()
        .name("attachment-key-mode")
        .description("Hold shows the Attachment badge only while the key is held. Toggle flips it on/off each press.")
        .defaultValue(KeyMode.Hold)
        .visible(() -> attachmentDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<BadgeAnchor> attachmentAnchor = sgAttachment.add(new EnumSetting.Builder<BadgeAnchor>()
        .name("attachment-anchor")
        .description("Which corner of the icon the Attachment badge is anchored to.")
        .defaultValue(BadgeAnchor.BottomRight)
        .build()
    );

    private final Setting<Integer> attachmentLine = sgAttachment.add(new IntSetting.Builder()
        .name("attachment-line")
        .description("Which row the Attachment badge sits on, counting outward from its anchor corner (0 = the row closest to the corner).")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 4)
        .build()
    );

    // Artifact - Cosmic Artifact's own "Exchange rate: N.Nx (✦raw)" lore line. The multiplier
    // (N.Nx) is the number that actually matters day to day (4.0x+ is worth cashing in, 10.0x is
    // the max) - shown as the main thing on the icon, background and all, not a small corner
    // badge like everything else here.

    private final Setting<DisplayMode> artifactDisplay = sgArtifact.add(new EnumSetting.Builder<DisplayMode>()
        .name("artifact-display")
        .description("When to show the Cosmic Artifact exchange-rate badge.")
        .defaultValue(DisplayMode.Always)
        .build()
    );

    private final Setting<Keybind> artifactKey = sgArtifact.add(new KeybindSetting.Builder()
        .name("artifact-key")
        .description("Key for Artifact Display's On Keypress.")
        .defaultValue(Keybind.none())
        .visible(() -> artifactDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<KeyMode> artifactKeyMode = sgArtifact.add(new EnumSetting.Builder<KeyMode>()
        .name("artifact-key-mode")
        .defaultValue(KeyMode.Hold)
        .visible(() -> artifactDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<BadgeAnchor> artifactAnchor = sgArtifact.add(new EnumSetting.Builder<BadgeAnchor>()
        .name("artifact-anchor")
        .description("Which corner/edge the exchange-rate badge sits at.")
        .defaultValue(BadgeAnchor.TopRight)
        .build()
    );

    private final Setting<Integer> artifactLine = sgArtifact.add(new IntSetting.Builder()
        .name("artifact-line")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<Double> artifactScale = sgArtifact.add(new DoubleSetting.Builder()
        .name("artifact-scale")
        .defaultValue(0.8)
        .min(0.1)
        .sliderRange(0.1, 2)
        .build()
    );

    private final Setting<Integer> artifactAlpha = sgArtifact.add(new IntSetting.Builder()
        .name("artifact-alpha")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    private final Setting<Boolean> artifactRangeEnabled = sgArtifact.add(new BoolSetting.Builder()
        .name("artifact-range-enabled")
        .description("Only shows the badge when the exchange rate falls within the range below - hidden entirely outside it. Off by default so you always see what you're holding; turn on if you only want to be told about the ones actually worth noticing (4.0x+).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> artifactRangeMin = sgArtifact.add(new DoubleSetting.Builder()
        .name("artifact-range-min")
        .defaultValue(4.0)
        .min(0)
        .sliderRange(0, 10)
        .visible(artifactRangeEnabled::get)
        .build()
    );

    private final Setting<Double> artifactRangeMax = sgArtifact.add(new DoubleSetting.Builder()
        .name("artifact-range-max")
        .defaultValue(10.0)
        .min(0)
        .sliderRange(0, 10)
        .visible(artifactRangeEnabled::get)
        .build()
    );

    private final Setting<ColorMode> artifactColorMode = sgArtifact.add(new EnumSetting.Builder<ColorMode>()
        .name("artifact-color-mode")
        .description("Lore Color: the server's own aqua. Custom: one flat color. By Value: blends Low→Mid→High across the exchange rate - defaults to red at 0x, yellow at 4.0x (the \"worth cashing in\" point), green at 10.0x (max). By Value Ranges: a hard color per band, see Edit Ranges below.")
        .defaultValue(ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> artifactCustomColor = sgArtifact.add(new ColorSetting.Builder()
        .name("artifact-custom-color")
        .defaultValue(new SettingColor(0, 220, 255))
        .visible(() -> artifactColorMode.get() == ColorMode.Custom)
        .build()
    );

    private final Setting<SettingColor> artifactLowColor = sgArtifact.add(new ColorSetting.Builder()
        .name("artifact-low-value-color")
        .description("Color at/below 0x.")
        .defaultValue(new SettingColor(220, 30, 30))
        .visible(() -> artifactColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> artifactMidColor = sgArtifact.add(new ColorSetting.Builder()
        .name("artifact-mid-value-color")
        .description("Color at the mid threshold below - defaults to 4.0x, the point that's actually worth cashing in on.")
        .defaultValue(new SettingColor(255, 220, 30))
        .visible(() -> artifactColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<Double> artifactMidThreshold = sgArtifact.add(new DoubleSetting.Builder()
        .name("artifact-mid-threshold")
        .defaultValue(4.0)
        .min(0)
        .sliderRange(0, 10)
        .visible(() -> artifactColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<SettingColor> artifactHighColor = sgArtifact.add(new ColorSetting.Builder()
        .name("artifact-high-value-color")
        .description("Color at/above 10.0x (max).")
        .defaultValue(new SettingColor(30, 220, 30))
        .visible(() -> artifactColorMode.get() == ColorMode.ByValue)
        .build()
    );

    private final Setting<List<ColorRangeEntry>> artifactRanges = sgArtifact.add(new ColorRangeListSetting.Builder()
        .name("artifact-value-ranges")
        .visible(() -> artifactColorMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Void> artifactRangesEdit = sgArtifact.add(new ButtonSetting.Builder()
        .name("artifact-value-ranges-edit")
        .buttonText("Edit Ranges")
        .screen(theme -> new ColorRangeListScreen(theme, "Artifact", artifactRanges.get(), 10))
        .visible(() -> artifactColorMode.get() == ColorMode.ByValueRanges)
        .build()
    );

    private final Setting<Boolean> artifactBackground = sgArtifact.add(new BoolSetting.Builder()
        .name("artifact-background")
        .description("Draws a filled background behind the exchange-rate badge, so it reads as the main thing on the icon instead of a small corner indicator.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> artifactBackgroundColor = sgArtifact.add(new ColorSetting.Builder()
        .name("artifact-background-color")
        .defaultValue(new SettingColor(0, 0, 0, 160))
        .visible(artifactBackground::get)
        .build()
    );

    // Amount - Cosmic Artifact's own "Amount: N" lore line, a server-tracked deposited count
    // separate from the stack's own vanilla item count (which caps visually at 64) - read and
    // rendered independently from the Exchange Rate badge above and from Boot Mod's value parsing
    // below (that one used to loosely match ANY bold plain number in an item's lore, which is
    // exactly what this "Amount: 0" line is shaped like - confirmed as the cause of a stray "0"
    // badge showing on every Cosmic Artifact once Boot Mod's detection was tightened elsewhere).

    private final Setting<Boolean> artifactAmountEnabled = sgArtifact.add(new BoolSetting.Builder()
        .name("artifact-amount-enabled")
        .description("Also shows the artifact's own \"Amount: N\" (deposited count) lore value as a second badge - separate from the stack's vanilla item count, which can't show past 64.")
        .defaultValue(true)
        .build()
    );

    private final Setting<BadgeAnchor> artifactAmountAnchor = sgArtifact.add(new EnumSetting.Builder<BadgeAnchor>()
        .name("artifact-amount-anchor")
        .description("Which corner/edge the amount badge sits at.")
        .defaultValue(BadgeAnchor.BottomLeft)
        .visible(artifactAmountEnabled::get)
        .build()
    );

    private final Setting<Integer> artifactAmountLine = sgArtifact.add(new IntSetting.Builder()
        .name("artifact-amount-line")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 4)
        .visible(artifactAmountEnabled::get)
        .build()
    );

    private final Setting<Double> artifactAmountScale = sgArtifact.add(new DoubleSetting.Builder()
        .name("artifact-amount-scale")
        .defaultValue(0.6)
        .min(0.1)
        .sliderRange(0.1, 2)
        .visible(artifactAmountEnabled::get)
        .build()
    );

    private final Setting<Integer> artifactAmountAlpha = sgArtifact.add(new IntSetting.Builder()
        .name("artifact-amount-alpha")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .visible(artifactAmountEnabled::get)
        .build()
    );

    private final Setting<SettingColor> artifactAmountColor = sgArtifact.add(new ColorSetting.Builder()
        .name("artifact-amount-color")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(artifactAmountEnabled::get)
        .build()
    );

    // Boot Mod - the selectable mod books from the boot mod menu ("Left click to equip slot 1" /
    // "Right click to equip slot 2"). Stat name comes straight off the item's own display name
    // (too long to print on a tiny icon badge every time) - Symbols below maps it to a short
    // symbol instead, same convention Enchant/Attachment Symbols already use. Only a handful of
    // names are confirmed (see the built-in table) - add more as you run into them.

    private static final Map<String, String> BUILTIN_BOOT_MOD_SYMBOLS = Map.ofEntries(
        Map.entry("Damage In Boss Fights", "☠"),
        Map.entry("Armor Vs Players", "⚔"),
        Map.entry("Armor Vs Ranged", "➶"),
        Map.entry("Mace Bash Chance", "🔨"),
        Map.entry("Dagger Max Puncture", "➷"),
        Map.entry("Bow Arrow Speed", "➹"),
        Map.entry("More Poison Damage Vs Mobs", "☣"),
        Map.entry("More Rage Gain", "♨"),
        Map.entry("Chance To Regen Extra Stamina", "⚡")
    );

    private final Setting<DisplayMode> bootModDisplay = sgBootMod.add(new EnumSetting.Builder<DisplayMode>()
        .name("boot-mod-display")
        .description("When to show the Boot Mod badge (its stat's symbol + current value).")
        .defaultValue(DisplayMode.Always)
        .build()
    );

    private final Setting<Keybind> bootModKey = sgBootMod.add(new KeybindSetting.Builder()
        .name("boot-mod-key")
        .defaultValue(Keybind.none())
        .visible(() -> bootModDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<KeyMode> bootModKeyMode = sgBootMod.add(new EnumSetting.Builder<KeyMode>()
        .name("boot-mod-key-mode")
        .defaultValue(KeyMode.Hold)
        .visible(() -> bootModDisplay.get() == DisplayMode.OnKeypress)
        .build()
    );

    private final Setting<BadgeAnchor> bootModAnchor = sgBootMod.add(new EnumSetting.Builder<BadgeAnchor>()
        .name("boot-mod-anchor")
        .defaultValue(BadgeAnchor.BottomLeft)
        .build()
    );

    private final Setting<Integer> bootModLine = sgBootMod.add(new IntSetting.Builder()
        .name("boot-mod-line")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<Double> bootModScale = sgBootMod.add(new DoubleSetting.Builder()
        .name("boot-mod-scale")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 2)
        .build()
    );

    private final Setting<Integer> bootModAlpha = sgBootMod.add(new IntSetting.Builder()
        .name("boot-mod-alpha")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    private final Setting<List<String>> bootModSymbols = sgBootMod.add(new StringListSetting.Builder()
        .name("boot-mod-symbols")
        .description("Extra/override Boot Mod stat name-to-symbol mappings, format \"Name=Symbol\" (e.g. \"Dagger Max Puncture=➷\") - same convention as Enchant/Attachment Symbols. A stat name matching neither this list nor the built-in one still shows (just with no symbol, only the value), unlike Enchant/Attachment which hide entirely.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<SettingColor> bootModColor = sgBootMod.add(new ColorSetting.Builder()
        .name("boot-mod-color")
        .description("Color of the badge while the stat isn't at its own max value yet.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> bootModMaxedColor = sgBootMod.add(new ColorSetting.Builder()
        .name("boot-mod-maxed-color")
        .description("Color of the badge once the stat actually reads \"(Maxed)\" in its own lore.")
        .defaultValue(new SettingColor(255, 215, 0))
        .build()
    );

    private final Setting<DisplayMode> durabilityBarDisplay = sgDurabilityBar.add(new EnumSetting.Builder<DisplayMode>()
        .name("bar-display")
        .description("When to repurpose the vanilla durability bar to visualize Quality % - independent of Quality Display above (which only controls the text badge). Off leaves vanilla's own damage-based bar untouched. To recolor this bar, use Quality Color Mode up in the Quality settings group (not a separate setting here) - the bar follows the exact same color Quality Color Mode gives the badge.")
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
        .description("Stacked: the bar spans Min Quality up to Max Quality, extended by Max Bonus % and/or Max Ward below if their own Include Headroom settings (separate for weapons vs chest/legs armor) are on for the item you're looking at. Priority: shows only whichever of these is currently most relevant instead of one combined scale - Ward's own 0-Max Ward scale while any Ward is active, otherwise the enhancer bonus alone (empty until Quality % actually passes Max Quality) while one's active, otherwise plain Quality from Min to Max.")
        .defaultValue(DurabilityBarMode.Stacked)
        .build()
    );

    // Split by item category - you almost always keep a Super Enhancer and a Ward active on a
    // weapon (re-applying Ward the moment it runs out), but rarely bother with either on armor, so
    // reserving that same headroom space on an armor piece's bar just wastes most of its length on
    // something that's basically never there. Only chest/legs count as "armor" here - head/feet
    // aren't worth a separate ruleset. Anything that isn't specifically chest/legs armor (weapons,
    // and everything else) follows the Weapon settings.

    private final Setting<Boolean> includeSuperEnhancerHeadroomWeapon = sgDurabilityBar.add(new BoolSetting.Builder()
        .name("include-super-enhancer-headroom-weapon")
        .description("Stacked mode, weapons (and anything else that isn't chest/legs armor): extends the bar's scale by Max Bonus % so there's visible room for a Super Enhancer's extra Quality, instead of the bar already reading full the moment Quality % passes the item's own Max Quality.")
        .defaultValue(true)
        .visible(() -> durabilityBarMode.get() == DurabilityBarMode.Stacked)
        .build()
    );

    private final Setting<Boolean> includeWardHeadroomWeapon = sgDurabilityBar.add(new BoolSetting.Builder()
        .name("include-ward-headroom-weapon")
        .description("Stacked mode, weapons (and anything else that isn't chest/legs armor): extends the bar's scale by Max Ward so there's visible room for Ward's extra effective Quality while it's active.")
        .defaultValue(true)
        .visible(() -> durabilityBarMode.get() == DurabilityBarMode.Stacked)
        .build()
    );

    private final Setting<Boolean> includeSuperEnhancerHeadroomArmor = sgDurabilityBar.add(new BoolSetting.Builder()
        .name("include-super-enhancer-headroom-armor")
        .description("Stacked mode, chest/legs armor only: same as the Weapon version above, just off by default since a Super Enhancer is rarely kept on armor.")
        .defaultValue(false)
        .visible(() -> durabilityBarMode.get() == DurabilityBarMode.Stacked)
        .build()
    );

    private final Setting<Boolean> includeWardHeadroomArmor = sgDurabilityBar.add(new BoolSetting.Builder()
        .name("include-ward-headroom-armor")
        .description("Stacked mode, chest/legs armor only: same as the Weapon version above, just off by default since Ward is rarely kept on armor.")
        .defaultValue(false)
        .visible(() -> durabilityBarMode.get() == DurabilityBarMode.Stacked)
        .build()
    );

    private final Setting<Integer> maxBonusPercent = sgDurabilityBar.add(new IntSetting.Builder()
        .name("max-bonus-percent")
        .description("How far beyond an item's own Max Quality a Super Enhancer can push it - always +10%, a fixed game constant rather than something printed per-item, so there's nothing to read it from directly the way Min/Max Quality are read from each item's own lore. Was .dev-gated (so it never actually showed up to be adjusted for most people - stuck at its default of 10 regardless of what you tried) - now tied to Cheese Control being on instead, since that's the module actually dealing with enhanced/cheesed items.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 50)
        .visible(() -> Modules.get().get(CheeseControl.class).isActive())
        .build()
    );

    private final Setting<Integer> maxWard = sgDurabilityBar.add(new IntSetting.Builder()
        .name("max-ward")
        .description("The Ward value the bar should treat as full - always 10 (a freshly-applied Ward absorbs 10 Quality worth of drain before your item's real Quality starts dropping), a fixed game constant rather than something printed per-item, same idea as Max Bonus % for Quality.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 50)
        .build()
    );

    private final Setting<Boolean> showMinFloor = sgDurabilityBar.add(new BoolSetting.Builder()
        .name("show-min-floor")
        .description("Shows the whole bar in Min Floor Color instead of its normal color once effective Quality (Quality % plus any active Ward) drops to Min Quality (or Custom Min Threshold below, if that's on) - an unmissable \"at the floor\" warning instead of just a short, easy-to-miss bar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useCustomMinThreshold = sgDurabilityBar.add(new BoolSetting.Builder()
        .name("use-custom-min-threshold")
        .description("Uses Custom Min Threshold below instead of the item's own Min Quality (from its lore) as the bar's floor and Show Min Floor's trigger point.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> customMinThreshold = sgDurabilityBar.add(new IntSetting.Builder()
        .name("custom-min-threshold")
        .description("The floor to use instead of the item's own Min Quality, when Use Custom Min Threshold is on.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 100)
        .visible(useCustomMinThreshold::get)
        .build()
    );

    private final Setting<Boolean> useCustomMaxThreshold = sgDurabilityBar.add(new BoolSetting.Builder()
        .name("use-custom-max-threshold")
        .description("Uses Custom Max Threshold below instead of the item's own Max Quality (from its lore) as the bar's ceiling - the point Quality itself (before any Super Enhancer/Ward headroom on top) reads as completely full.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> customMaxThreshold = sgDurabilityBar.add(new IntSetting.Builder()
        .name("custom-max-threshold")
        .description("The ceiling to use instead of the item's own Max Quality, when Use Custom Max Threshold is on. Doesn't affect whether the item currently counts as \"over its own max\" for Priority mode or Super Enhanced coloring - that's still read from the item's real Max Quality, this only changes where the bar's scale ends.")
        .defaultValue(100)
        .min(1)
        .sliderRange(1, 300)
        .visible(useCustomMaxThreshold::get)
        .build()
    );

    private final Setting<Double> wardHeadroomWeight = sgDurabilityBar.add(new DoubleSetting.Builder()
        .name("ward-headroom-weight")
        .description("Stacked mode only, wherever its own Include Ward Headroom (Weapon or Armor) is on: how much of the bar's total length is dedicated to Ward specifically, as a fraction (0.25 = Ward can move the last quarter of the bar). Needed because Max Ward (10) is tiny next to Quality's own range (often 100+) - just adding Ward's raw point value on top used to get diluted down to a fraction of a single pixel-step, meaning Ward visibly did nothing to the bar even though it was technically included in the number. Shared between both - the weight that reads well for a weapon reads just as well for armor, it's only whether Ward headroom shows up AT ALL that differs between them.")
        .defaultValue(0.25)
        .min(0.05)
        .sliderRange(0.05, 0.9)
        .visible(() -> includeWardHeadroomWeapon.get() || includeWardHeadroomArmor.get())
        .build()
    );

    private final Setting<SettingColor> minFloorColor = sgDurabilityBar.add(new ColorSetting.Builder()
        .name("min-floor-color")
        .description("Color the whole bar shows at/below the floor, when Show Min Floor is on.")
        .defaultValue(new SettingColor(255, 40, 40))
        .visible(showMinFloor::get)
        .build()
    );

    private final Setting<Boolean> minFloorFlash = sgDurabilityBar.add(new BoolSetting.Builder()
        .name("min-floor-flash")
        .description("Flashes the bar at/below the floor instead of showing Min Floor Color flat - hard-alternates with Min Floor Flash Color below, same idea as the Flashing color mode elsewhere in this pack.")
        .defaultValue(true)
        .visible(showMinFloor::get)
        .build()
    );

    private final Setting<SettingColor> minFloorFlashColor = sgDurabilityBar.add(new ColorSetting.Builder()
        .name("min-floor-flash-color")
        .description("The color Min Floor Color alternates with, when Min Floor Flash is on.")
        .defaultValue(new SettingColor(0, 0, 0))
        .visible(() -> showMinFloor.get() && minFloorFlash.get())
        .build()
    );

    private final Setting<Double> minFloorFlashSpeed = sgDurabilityBar.add(new DoubleSetting.Builder()
        .name("min-floor-flash-speed")
        .description("Flashes per second, when Min Floor Flash is on.")
        .defaultValue(2.0)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> showMinFloor.get() && minFloorFlash.get())
        .build()
    );

    private final Setting<MinFloorWarningMode> minFloorWarningMode = sgDurabilityBar.add(new EnumSetting.Builder<MinFloorWarningMode>()
        .name("min-floor-warning-mode")
        .description("How far above the actual floor the Min Floor warning starts, instead of only at/below the floor itself - gives you a heads-up to repair before you're actually there. None: no early warning, exactly the old behavior. Flat: a fixed number of Quality points above the floor. Percent: a percentage of the bar's own range (ceiling minus floor) above the floor.")
        .defaultValue(MinFloorWarningMode.Percent)
        .visible(showMinFloor::get)
        .build()
    );

    private final Setting<Integer> minFloorWarningFlat = sgDurabilityBar.add(new IntSetting.Builder()
        .name("min-floor-warning-flat")
        .description("Starts the Min Floor warning this many Quality points above the floor, when Min Floor Warning Mode is Flat.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 50)
        .visible(() -> showMinFloor.get() && minFloorWarningMode.get() == MinFloorWarningMode.Flat)
        .build()
    );

    private final Setting<Double> minFloorWarningPercent = sgDurabilityBar.add(new DoubleSetting.Builder()
        .name("min-floor-warning-percent")
        .description("Starts the Min Floor warning this percentage of the bar's own range (ceiling minus floor) above the floor, when Min Floor Warning Mode is Percent.")
        .defaultValue(10.0)
        .min(0)
        .sliderRange(0, 50)
        .visible(() -> showMinFloor.get() && minFloorWarningMode.get() == MinFloorWarningMode.Percent)
        .build()
    );

    private boolean wardToggledOn = false;
    private boolean tierToggledOn = false;
    private boolean qualityToggledOn = false;
    private boolean enchantToggledOn = false;
    private boolean attachmentToggledOn = false;
    private boolean durabilityBarToggledOn = false;
    private boolean artifactToggledOn = false;
    private boolean bootModToggledOn = false;

    public ItemInfo() {
        super(Categories.Items_, "item-info", "Shows Ward, Quality, Tier, Enchant and Attachment info from item lore as badges on item icons.");
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (event.action != KeyAction.Press) return;

        if (wardKeyMode.get() == KeyMode.Toggle && wardKey.get().isSet() && wardKey.get().matches(true, event.key, event.modifiers)) wardToggledOn = !wardToggledOn;
        if (tierKeyMode.get() == KeyMode.Toggle && tierKey.get().isSet() && tierKey.get().matches(true, event.key, event.modifiers)) tierToggledOn = !tierToggledOn;
        if (qualityKeyMode.get() == KeyMode.Toggle && qualityKey.get().isSet() && qualityKey.get().matches(true, event.key, event.modifiers)) qualityToggledOn = !qualityToggledOn;
        if (enchantKeyMode.get() == KeyMode.Toggle && enchantKey.get().isSet() && enchantKey.get().matches(true, event.key, event.modifiers)) enchantToggledOn = !enchantToggledOn;
        if (attachmentKeyMode.get() == KeyMode.Toggle && attachmentKey.get().isSet() && attachmentKey.get().matches(true, event.key, event.modifiers)) attachmentToggledOn = !attachmentToggledOn;
        if (durabilityBarKeyMode.get() == KeyMode.Toggle && durabilityBarKey.get().isSet() && durabilityBarKey.get().matches(true, event.key, event.modifiers)) durabilityBarToggledOn = !durabilityBarToggledOn;
        if (artifactKeyMode.get() == KeyMode.Toggle && artifactKey.get().isSet() && artifactKey.get().matches(true, event.key, event.modifiers)) artifactToggledOn = !artifactToggledOn;
        if (bootModKeyMode.get() == KeyMode.Toggle && bootModKey.get().isSet() && bootModKey.get().matches(true, event.key, event.modifiers)) bootModToggledOn = !bootModToggledOn;
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
        if (!passesTierFilters(stack)) return;

        QualityInfo info = findQualityInfo(stack);

        boolean showWard = info != null && info.ward() != null
            && shouldShow(wardDisplay.get(), isKeyActive(wardKey, wardKeyMode, wardToggledOn)) && wardInRange(info.ward());
        boolean showQuality = info != null && info.qualityPercent() != null
            && shouldShow(qualityDisplay.get(), isKeyActive(qualityKey, qualityKeyMode, qualityToggledOn)) && qualityInRange(info.qualityPercent());

        TierInfo tierInfo = findTierInfo(stack);
        boolean showTier = tierInfo != null && shouldShow(tierDisplay.get(), isKeyActive(tierKey, tierKeyMode, tierToggledOn)) && !isTierExcluded(tierInfo.tier());

        String enchantSymbol = null;
        if (shouldShow(enchantDisplay.get(), isKeyActive(enchantKey, enchantKeyMode, enchantToggledOn))) {
            NameInfo enchant = findLoreName(stack, ENCHANT_PREFIX, true);
            enchantSymbol = enchant != null ? resolveSymbol(enchant.name(), enchantSymbols.get(), BUILTIN_ENCHANT_SYMBOLS) : null;
        }

        String attachmentSymbol = null;
        if (shouldShow(attachmentDisplay.get(), isKeyActive(attachmentKey, attachmentKeyMode, attachmentToggledOn))) {
            NameInfo attachment = findLoreName(stack, ATTACHMENT_PREFIX, false);
            attachmentSymbol = attachment != null ? resolveSymbol(attachment.name(), attachmentSymbols.get(), BUILTIN_ATTACHMENT_SYMBOLS) : null;
        }

        boolean fillWardSpot = showQuality && !showWard && qualityFillsWardSpot.get();
        BadgeAnchor qualityAnchorResolved = fillWardSpot ? wardAnchor.get() : qualityAnchor.get();
        int qualityLineResolved = fillWardSpot ? wardLine.get() : qualityLine.get();

        // A badge only needs to shrink to avoid crossing the icon's center when something is
        // ACTUALLY sharing its line from the opposite side on THIS item - not just because it
        // theoretically could, against some other badge that isn't even showing right now. Without
        // this, e.g. Quality shrank itself on every single item just because Tier exists as a
        // concept, even on items with no Tier badge at all to actually collide with.
        boolean[] occupied = new boolean[BadgeAnchor.values().length * 8];
        if (showWard) markOccupied(occupied, wardAnchor.get(), wardLine.get());
        if (showTier) markOccupied(occupied, tierAnchor.get(), tierLine.get());
        if (showQuality) markOccupied(occupied, qualityAnchorResolved, qualityLineResolved);
        if (enchantSymbol != null) markOccupied(occupied, enchantAnchor.get(), enchantLine.get());
        if (attachmentSymbol != null) markOccupied(occupied, attachmentAnchor.get(), attachmentLine.get());

        if (showWard) {
            int color = switch (wardColorMode.get()) {
                case Custom -> wardCustomColor.get().getPacked();
                case ByValue -> wardByValueColor(info.ward()).getPacked();
                case ByValueRanges -> ColorRangeEntry.resolve(wardRanges.get(), info.ward(), Color.WHITE).getPacked();
                case LoreColor -> info.wardColor() | 0xFF000000;
            };
            drawBadge(context, textRenderer, "⚡" + info.ward(), x, y, wardAnchor.get(), wardLine.get(), color, resolveAlpha(wardAlpha), resolveScale(wardScale), hasOppositeOccupant(occupied, wardAnchor.get(), wardLine.get()));
        }

        if (showTier) {
            int color = switch (tierColorMode.get()) {
                case Custom -> tierCustomColor.get().getPacked();
                case ByValue -> tierByValueColor(tierInfo.tier()).getPacked();
                case ByValueRanges -> ColorRangeEntry.resolve(tierRanges.get(), tierInfo.tier(), Color.WHITE).getPacked();
                case LoreColor -> tierInfo.color() | 0xFF000000;
            };
            drawBadge(context, textRenderer, tierBadgeText(tierInfo.tier()), x, y, tierAnchor.get(), tierLine.get(), color, resolveAlpha(tierAlpha), resolveScale(tierScale), hasOppositeOccupant(occupied, tierAnchor.get(), tierLine.get()));
        }

        if (showQuality) {
            int color = switch (qualityColorMode.get()) {
                case Custom -> qualityCustomColor.get().getPacked();
                case ByValue -> qualityByValueColor(info).getPacked();
                case ByValueRanges -> ColorRangeEntry.resolve(qualityRanges.get(), info.qualityPercent(), Color.WHITE).getPacked();
                case LoreColor -> info.qualityColor() | 0xFF000000;
            };

            drawBadge(context, textRenderer, info.qualityPercent() + "%", x, y, qualityAnchorResolved, qualityLineResolved, color, resolveAlpha(qualityAlpha), resolveScale(qualityScale), hasOppositeOccupant(occupied, qualityAnchorResolved, qualityLineResolved));
        }

        if (enchantSymbol != null) {
            int color = getEnchantColor();
            drawBadge(context, textRenderer, enchantSymbol, x, y, enchantAnchor.get(), enchantLine.get(), color, resolveAlpha(enchantAlpha), resolveScale(enchantScale), hasOppositeOccupant(occupied, enchantAnchor.get(), enchantLine.get()));
        }

        if (attachmentSymbol != null) {
            int color = getAttachmentColor();
            drawBadge(context, textRenderer, attachmentSymbol, x, y, attachmentAnchor.get(), attachmentLine.get(), color, resolveAlpha(attachmentAlpha), resolveScale(attachmentScale), hasOppositeOccupant(occupied, attachmentAnchor.get(), attachmentLine.get()));
        }

        if (shouldShow(artifactDisplay.get(), isKeyActive(artifactKey, artifactKeyMode, artifactToggledOn))) {
            ArtifactInfo artifactInfo = findArtifactInfo(stack);

            if (artifactInfo != null && (!artifactRangeEnabled.get() || (artifactInfo.rate() >= artifactRangeMin.get() && artifactInfo.rate() <= artifactRangeMax.get()))) {
                int color = switch (artifactColorMode.get()) {
                    case LoreColor -> artifactInfo.color() | 0xFF000000;
                    case Custom -> artifactCustomColor.get().getPacked() | 0xFF000000;
                    case ByValue -> artifactByValueColor(artifactInfo.rate()).getPacked() | 0xFF000000;
                    case ByValueRanges -> ColorRangeEntry.resolve(artifactRanges.get(), artifactInfo.rate(), Color.WHITE).getPacked() | 0xFF000000;
                };

                String text = (artifactInfo.rate() == Math.floor(artifactInfo.rate()) ? String.format("%.1f", artifactInfo.rate()) : String.valueOf(artifactInfo.rate())) + "x";
                int background = artifactBackground.get() ? artifactBackgroundColor.get().getPacked() | 0xFF000000 : 0;

                drawBadge(context, textRenderer, text, x, y, artifactAnchor.get(), artifactLine.get(), color, resolveAlpha(artifactAlpha), resolveScale(artifactScale), false, background);
            }

            if (artifactInfo != null && artifactAmountEnabled.get()) {
                Integer amount = findArtifactAmount(stack);

                if (amount != null) {
                    drawBadge(context, textRenderer, String.valueOf(amount), x, y, artifactAmountAnchor.get(), artifactAmountLine.get(),
                        artifactAmountColor.get().getPacked() | 0xFF000000, resolveAlpha(artifactAmountAlpha), resolveScale(artifactAmountScale),
                        hasOppositeOccupant(occupied, artifactAmountAnchor.get(), artifactAmountLine.get()));
                }
            }
        }

        if (shouldShow(bootModDisplay.get(), isKeyActive(bootModKey, bootModKeyMode, bootModToggledOn))) {
            BootModInfo bootModInfo = findBootModInfo(stack);

            if (bootModInfo != null) {
                String symbol = resolveSymbol(bootModInfo.statName(), bootModSymbols.get(), BUILTIN_BOOT_MOD_SYMBOLS);
                String text = (symbol != null ? symbol + " " : "") + bootModInfo.valueText();
                int color = (bootModInfo.maxed() ? bootModMaxedColor.get() : bootModColor.get()).getPacked() | 0xFF000000;

                drawBadge(context, textRenderer, text, x, y, bootModAnchor.get(), bootModLine.get(), color, resolveAlpha(bootModAlpha), resolveScale(bootModScale), hasOppositeOccupant(occupied, bootModAnchor.get(), bootModLine.get()));
            }
        }
    }

    private static final int BADGE_MAX_LINE = 8;

    private static void markOccupied(boolean[] occupied, BadgeAnchor anchor, int line) {
        if (line < 0 || line >= BADGE_MAX_LINE) return;
        occupied[anchor.ordinal() * BADGE_MAX_LINE + line] = true;
    }

    private static boolean hasOppositeOccupant(boolean[] occupied, BadgeAnchor anchor, int line) {
        if (line < 0 || line >= BADGE_MAX_LINE) return false;

        // Only the plain corner/middle Left<->Right pairs have one clear "opposite" worth
        // shrinking away from - a Center-aligned badge already straddles the icon's own middle on
        // purpose, so there's no single side to avoid crossing into; left as a power-user option
        // you place deliberately, same as choosing a Line number already is.
        BadgeAnchor opposite = switch (anchor) {
            case TopLeft -> BadgeAnchor.TopRight;
            case TopRight -> BadgeAnchor.TopLeft;
            case MiddleLeft -> BadgeAnchor.MiddleRight;
            case MiddleRight -> BadgeAnchor.MiddleLeft;
            case BottomLeft -> BadgeAnchor.BottomRight;
            case BottomRight -> BadgeAnchor.BottomLeft;
            case TopCenter, Center, BottomCenter -> null;
        };

        return opposite != null && occupied[opposite.ordinal() * BADGE_MAX_LINE + line];
    }

    /** Linear blend from Quality Low Value Color (at/below Quality Low Threshold) to Quality High Value Color (at/above Quality High Threshold) - Quality Super Color instead, whenever the item's own quality exceeds ITS OWN max quality (a bonus/"Super Enhancer" item). */
    /** Low -> mid over [low, mid], mid -> high over [mid, high]; wards run 1 to 10. */
    private Color wardByValueColor(int ward) {
        int lowV = wardLowValue.get(), highV = wardHighValue.get();
        SettingColor low = wardLowColor.get();
        SettingColor mid = wardMidColor.get();
        SettingColor high = wardHighColor.get();

        if (highV <= lowV) return ward <= lowV ? low : high;

        int midV = MathHelper.clamp(wardMidValue.get(), lowV, highV);

        // Clamped into [lowV, highV] before picking a segment - otherwise a ward BELOW lowV (or
        // above highV) that also happens to fall on the wrong side of a Mid Value pushed to the very
        // edge (e.g. Mid Value left at its default while Low Value gets raised past it, collapsing
        // the low->mid segment to zero width) hit the "zero-width segment" fallback below and came
        // back as fully mid instead of fully low/high - clamping first keeps every out-of-range value
        // pinned to the correct end color regardless of where Mid Value ends up.
        int clamped = MathHelper.clamp(ward, lowV, highV);

        if (clamped <= midV) {
            float t = midV > lowV ? MathHelper.clamp((clamped - lowV) / (float) (midV - lowV), 0f, 1f) : 0f;
            return blend(low, mid, t);
        }

        float t = highV > midV ? MathHelper.clamp((clamped - midV) / (float) (highV - midV), 0f, 1f) : 1f;
        return blend(mid, high, t);
    }

    /** Low -> mid over [low, mid], mid -> high over [mid, high]. */
    private Color tierByValueColor(int tier) {
        int lowV = tierLowValue.get(), highV = tierHighValue.get();
        SettingColor low = tierLowColor.get();
        SettingColor mid = tierMidColor.get();
        SettingColor high = tierHighColor.get();

        if (highV <= lowV) return tier <= lowV ? low : high;

        int midV = MathHelper.clamp(tierMidValue.get(), lowV, highV);
        int clamped = MathHelper.clamp(tier, lowV, highV);

        if (clamped <= midV) {
            float t = midV > lowV ? MathHelper.clamp((clamped - lowV) / (float) (midV - lowV), 0f, 1f) : 0f;
            return blend(low, mid, t);
        }

        float t = highV > midV ? MathHelper.clamp((clamped - midV) / (float) (highV - midV), 0f, 1f) : 1f;
        return blend(mid, high, t);
    }

    private Color qualityByValueColor(QualityInfo info) {
        int percent = info.qualityPercent();
        int itemMaxQuality = info.maxQuality() != null ? info.maxQuality() : 100;
        if (percent > itemMaxQuality) return qualitySuperColor.get();

        int lowT = qualityLowThreshold.get();
        int highT = qualityHighThreshold.get();

        SettingColor low = qualityLowColor.get();
        SettingColor high = qualityHighColor.get();

        if (!qualityMidEnabled.get() || highT <= lowT) {
            float t = highT > lowT
                ? MathHelper.clamp((percent - lowT) / (float) (highT - lowT), 0f, 1f)
                : (percent <= lowT ? 0f : 1f);

            return blend(low, high, t);
        }

        // Three-point blend: low -> mid over [low, mid], mid -> high over [mid, high]. Mid is clamped
        // inside the low/high range so a stray value can't invert the segments.
        int midT = MathHelper.clamp(qualityMidThreshold.get(), lowT, highT);
        SettingColor mid = qualityMidColor.get();

        // Clamped into [lowT, highT] first - see the identical comment in wardByValueColor. Without
        // this, e.g. raising Quality Low Threshold above the still-default Mid Threshold collapses
        // the low->mid segment to zero width, and any percent at or below that point (even one well
        // under Low Threshold) came back fully mid instead of fully low.
        int clamped = MathHelper.clamp(percent, lowT, highT);

        if (clamped <= midT) {
            float t = midT > lowT ? MathHelper.clamp((clamped - lowT) / (float) (midT - lowT), 0f, 1f) : 0f;
            return blend(low, mid, t);
        }

        float t = highT > midT ? MathHelper.clamp((clamped - midT) / (float) (highT - midT), 0f, 1f) : 1f;
        return blend(mid, high, t);
    }

    private static Color blend(SettingColor from, SettingColor to, float t) {
        return new Color(
            (int) (from.r + (to.r - from.r) * t),
            (int) (from.g + (to.g - from.g) * t),
            (int) (from.b + (to.b - from.b) * t),
            (int) (from.a + (to.a - from.a) * t)
        );
    }

    private record ArtifactInfo(double rate, int color) {
    }

    private static final double ARTIFACT_MAX_RATE = 10.0; // the game's own fixed cap, not read from the item

    private static final Pattern ARTIFACT_RATE_PATTERN = Pattern.compile("^([0-9.]+)x$");

    /** "Exchange rate: N.Nx (✦raw)" - only the multiplier matters here (see this module's own class-level doc for why), not the raw Stardust number next to it. */
    private ArtifactInfo findArtifactInfo(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        for (Text line : lore.lines()) {
            if (!line.getString().startsWith("Exchange rate:")) continue;

            double[] rate = {-1};
            int[] color = {0xFFFFFF};

            line.visit((style, asString) -> {
                Matcher m = ARTIFACT_RATE_PATTERN.matcher(asString.trim());
                if (m.matches()) {
                    rate[0] = Double.parseDouble(m.group(1));
                    if (style.getColor() != null) color[0] = style.getColor().getRgb();
                }

                return Optional.empty();
            }, Style.EMPTY);

            return rate[0] < 0 ? null : new ArtifactInfo(rate[0], color[0]);
        }

        return null;
    }

    private static final Pattern ARTIFACT_AMOUNT_PATTERN = Pattern.compile("^[0-9]+$");

    /** "Amount: N" - the server's own deposited count, read independently of Boot Mod's unrelated bold-number value parsing (see the Amount settings' own class comment for why that distinction matters). Null if there's no such line at all. */
    private Integer findArtifactAmount(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        for (Text line : lore.lines()) {
            if (!line.getString().startsWith("Amount:")) continue;

            int[] amount = {-1};

            line.visit((style, asString) -> {
                String trimmed = asString.trim();
                if (ARTIFACT_AMOUNT_PATTERN.matcher(trimmed).matches()) amount[0] = Integer.parseInt(trimmed);

                return Optional.empty();
            }, Style.EMPTY);

            return amount[0] < 0 ? null : amount[0];
        }

        return null;
    }

    private Color artifactByValueColor(double rate) {
        if (rate > ARTIFACT_MAX_RATE) return artifactHighColor.get();

        double midT = artifactMidThreshold.get();
        SettingColor low = artifactLowColor.get();
        SettingColor mid = artifactMidColor.get();
        SettingColor high = artifactHighColor.get();

        if (midT <= 0 || rate <= midT) {
            float t = midT > 0 ? MathHelper.clamp((float) (rate / midT), 0f, 1f) : 1f;
            return blend(low, mid, t);
        }

        float t = MathHelper.clamp((float) ((rate - midT) / (ARTIFACT_MAX_RATE - midT)), 0f, 1f);
        return blend(mid, high, t);
    }

    private record BootModInfo(String statName, String valueText, boolean maxed, int color) {
    }

    // The current-value line's number is bold; the "Max: X" line right above it has the exact
    // same shape of number but never bold - requiring isBold() is what tells them apart, not the
    // regex alone (both would otherwise match it equally well).
    private static final Pattern BOOT_MOD_VALUE_PATTERN = Pattern.compile("^[+-]?[0-9.]+%?$");

    /** Stat name from the item's own display name (e.g. "Damage In Boss Fights"), current value + maxed-or-not from its lore's one bold "+N%(...)" / "(Maxed)" line. Null if this isn't a boot mod item at all (no matching bold value line found). */
    private BootModInfo findBootModInfo(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        String statName = stack.getName().getString().trim();
        if (statName.isEmpty()) return null;

        // The value pattern alone (any bold plain number) isn't specific to boot mods at all - it
        // matched a stray bold "0" on completely unrelated items (confirmed: every Cosmic Artifact
        // in the Cosmic Exchange GUI showed a false "0" badge this way, Artifact items have nothing
        // to do with boot mods). Requiring the real boot-mod selector's own fingerprint text first -
        // "Left click to equip slot 1" / "Right click to equip slot 2", present on every genuine boot
        // mod book and nothing else - confirms this actually IS a boot mod before parsing any value.
        boolean isBootMod = false;
        for (Text line : lore.lines()) {
            if (line.getString().contains("to equip slot")) {
                isBootMod = true;
                break;
            }
        }
        if (!isBootMod) return null;

        for (Text line : lore.lines()) {
            boolean maxed = line.getString().contains("(Maxed)");

            String[] valueText = {null};
            int[] color = {0xFFFFFF};

            line.visit((style, asString) -> {
                String trimmed = asString.trim();
                if (valueText[0] == null && style.isBold() && BOOT_MOD_VALUE_PATTERN.matcher(trimmed).matches()) {
                    valueText[0] = trimmed;
                    if (style.getColor() != null) color[0] = style.getColor().getRgb();
                }

                return Optional.empty();
            }, Style.EMPTY);

            if (valueText[0] != null) return new BootModInfo(statName, valueText[0], maxed, color[0]);
        }

        return null;
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
    private void drawBadge(DrawContext context, TextRenderer textRenderer, String text, int iconX, int iconY, BadgeAnchor anchor, int line, int argb, int alpha, double scale, boolean clampToHalfWidth) {
        drawBadge(context, textRenderer, text, iconX, iconY, anchor, line, argb, alpha, scale, clampToHalfWidth, 0);
    }

    /** Same as the plain overload, plus a filled rect behind the text (skipped entirely when backgroundArgb's alpha is 0) - for badges meant to read as the main thing on the icon (e.g. Artifact's exchange rate) rather than a small corner indicator. */
    private void drawBadge(DrawContext context, TextRenderer textRenderer, String text, int iconX, int iconY, BadgeAnchor anchor, int line, int argb, int alpha, double scale, boolean clampToHalfWidth, int backgroundArgb) {
        argb = withAlpha(argb, alpha);

        // Use Custom Font disabled here regardless of the setting: CustomFontRenderer issues its
        // own immediate-mode GL draw call (bindTexture + mesh.render) instead of going through
        // DrawContext's batched vertex consumers. Called from this mixin context (mid vanilla
        // drawItemInSlot), that leaves the wrong texture bound for whatever vanilla draws next
        // when its own batch flushes - not just badges rendering wrong, but the item icon itself
        // (and others after it) going invisible. Safe everywhere else this renderer is used
        // (Meteor's own HUD elements), since those run in their own dedicated end-of-frame render
        // pass instead of interleaved with vanilla's batching.
        boolean rightAligned = anchor == BadgeAnchor.TopRight || anchor == BadgeAnchor.MiddleRight || anchor == BadgeAnchor.BottomRight;
        boolean centerAligned = anchor == BadgeAnchor.TopCenter || anchor == BadgeAnchor.Center || anchor == BadgeAnchor.BottomCenter;
        boolean fromBottom = anchor == BadgeAnchor.BottomLeft || anchor == BadgeAnchor.BottomCenter || anchor == BadgeAnchor.BottomRight;
        boolean fromMiddle = anchor == BadgeAnchor.MiddleLeft || anchor == BadgeAnchor.Center || anchor == BadgeAnchor.MiddleRight;

        // A left-anchored badge (e.g. Quality, TopLeft) and a right-anchored one (e.g. Tier,
        // TopRight) on the same line only ever collide because each grows toward the icon's
        // center with no idea the other exists. Only shrink when the caller's already confirmed
        // something is actually occupying the opposite side of this exact line on THIS item
        // (see hasOppositeOccupant) - shrinking unconditionally made every badge render small on
        // every item, even ones where there was nothing on the other side to ever collide with.
        if (clampToHalfWidth) {
            double halfWidth = 7; // icon is 16px wide - a 2px gap either side of its exact center
            double textWidthPx = textRenderer.getWidth(text) * scale;
            if (textWidthPx > halfWidth) scale *= halfWidth / textWidthPx;
        }

        // +3 beyond vanilla's own 9px line height - at default Scale (0.5) that's a 4.5px gap between
        // adjacent lines, which is barely more than the scaled glyph height itself (shadowed text
        // included) and left two badges sharing a corner (e.g. Ward line 0, Quality line 1, both at
        // default scale) visibly crossing into each other with no breathing room at all.
        double lineHeight = (VANILLA_LINE_HEIGHT + 3) * scale;
        double anchorX = centerAligned ? iconX + 8 : iconX + (rightAligned ? 15 : 1);

        // Middle starts exactly centered on the icon's own vertical midpoint at line 0, then
        // stacks further lines downward from there - same downward direction Top already uses,
        // just starting from the middle instead of the top edge.
        double anchorY;
        if (fromMiddle) anchorY = iconY + 8 - lineHeight / 2.0 + line * lineHeight;
        else if (fromBottom) anchorY = iconY + 15 - lineHeight - line * lineHeight;
        else anchorY = iconY + 1 + line * lineHeight;

        MatrixStack matrices = context.getMatrices();
        int textWidth = textRenderer.getWidth(text);
        int drawX = centerAligned ? -textWidth / 2 : (rightAligned ? -textWidth : 0);

        matrices.push();
        matrices.translate(anchorX, anchorY, 190);
        matrices.scale((float) scale, (float) scale, 1f);

        if ((backgroundArgb >>> 24) != 0) {
            int pad = 1;
            context.fill(drawX - pad, -pad, drawX + textWidth + pad, VANILLA_LINE_HEIGHT - 1 + pad, backgroundArgb);
        }

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
    /**
     * The color of the FIRST styled segment of a lore line that actually has one, not the last -
     * a line like "Cosmic Item [✭✭]" is really 4 separate colored runs ("Cosmic Item " dark_blue,
     * "[" dark_gray, "✭✭" dark_blue, "]" dark_gray), and naively overwriting on every run Text.visit()
     * walks through left this (and findLoreName below) with whichever run happens to come LAST
     * (dark_gray, from the closing "]") instead of the line's own actual, meaningful color.
     */
    private static int firstSegmentColor(Text line) {
        int[] color = {0xFFFFFF};
        boolean[] found = {false};

        line.visit((style, asString) -> {
            if (!found[0] && style.getColor() != null) {
                color[0] = style.getColor().getRgb();
                found[0] = true;
            }
            return Optional.empty();
        }, Style.EMPTY);

        return color[0];
    }

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

            return new NameInfo(name, firstSegmentColor(line));
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

    /** Returns null when the bar override doesn't apply, or the item has no Quality % to show at all - leaves vanilla's own bar untouched either way. */
    public QualityInfo getDurabilityBarOverride(ItemStack stack) {
        if (!shouldShow(durabilityBarDisplay.get(), isKeyActive(durabilityBarKey, durabilityBarKeyMode, durabilityBarToggledOn))) return null;

        QualityInfo info = findQualityInfo(stack);
        if (info == null || info.qualityPercent() == null) return null;
        if (!qualityInRange(info.qualityPercent())) return null;

        return info;
    }

    /** Min Quality (from the item's own lore) or Custom Min Threshold, whichever Use Custom Min Threshold picks - the bar's scale origin, and Show Min Floor's trigger point. */
    private double minFloor(QualityInfo info) {
        if (useCustomMinThreshold.get()) return customMinThreshold.get();
        return info.minQuality() != null ? info.minQuality() : 0;
    }

    /** Max Quality (from the item's own lore) or Custom Max Threshold, whichever Use Custom Max Threshold picks - where the bar's own scale ends (before any Super Enhancer/Ward headroom on top). Never used for "is this item over its own max" detection below - that always reads the item's real info.maxQuality(), regardless of this override. */
    private double maxCeiling(QualityInfo info) {
        if (useCustomMaxThreshold.get()) return customMaxThreshold.get();
        return info.maxQuality() != null ? info.maxQuality() : 100;
    }

    /** Quality % plus any active Ward - Ward is literally extra effective Quality while it lasts, not a separate stat. Only used for the min-floor check below now - see getBarStep's Stacked branch for why the bar's own fill no longer just adds Ward's raw value the same way. */
    private double effectiveQuality(QualityInfo info) {
        return info.qualityPercent() + (info.ward() != null ? info.ward() : 0);
    }

    /** Chest or legs specifically - head/feet aren't worth their own ruleset, and "Armor Type: X" lore (confirmed on this server's leather_chestplate/leather_leggings) doesn't say which slot, so this reads the slot directly off the item instead. */
    private static boolean isChestOrLegsArmor(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armor && (armor.getSlotType() == EquipmentSlot.CHEST || armor.getSlotType() == EquipmentSlot.LEGS);
    }

    /** The floor plus however far above it the Min Floor warning should actually start - see Min Floor Warning Mode. Floor itself (margin 0) is always still the point Min Floor Color locks in at, regardless of this setting. */
    private double minFloorWarningThreshold(QualityInfo info) {
        double floor = minFloor(info);

        double margin = switch (minFloorWarningMode.get()) {
            case None -> 0;
            case Flat -> minFloorWarningFlat.get();
            case Percent -> Math.max(1, maxCeiling(info) - floor) * (minFloorWarningPercent.get() / 100.0);
        };

        return floor + margin;
    }

    public int getBarStep(ItemStack stack, QualityInfo info) {
        int maxQuality = info.maxQuality() != null ? info.maxQuality() : 100;

        if (durabilityBarMode.get() == DurabilityBarMode.Priority) {
            if (info.ward() != null && info.ward() > 0) {
                return MathHelper.clamp(Math.round(13f * info.ward() / maxWard.get()), 0, 13);
            }

            if (info.qualityPercent() > maxQuality) {
                int over = info.qualityPercent() - maxQuality;
                return MathHelper.clamp(Math.round(13f * over / maxBonusPercent.get()), 0, 13);
            }

            double floor = minFloor(info);
            double ceiling = maxCeiling(info);
            double range = Math.max(1, ceiling - floor);
            return MathHelper.clamp((int) Math.round(13f * (info.qualityPercent() - floor) / range), 0, 13);
        }

        // Stacked - Ward gets a guaranteed share of the bar (Ward Headroom Weight) rather than
        // being added as a raw point value into the same scale as Quality, which used to dilute a
        // fixed 10-point Ward down to a fraction of one of the bar's 13 pixel-steps against
        // Quality's own, usually much larger (100+), range - technically "included" but invisible.
        boolean armor = isChestOrLegsArmor(stack);
        boolean includeBonus = armor ? includeSuperEnhancerHeadroomArmor.get() : includeSuperEnhancerHeadroomWeapon.get();
        boolean includeWard = armor ? includeWardHeadroomArmor.get() : includeWardHeadroomWeapon.get();

        double floor = minFloor(info);
        double qualityCeiling = maxCeiling(info) + (includeBonus ? maxBonusPercent.get() : 0);
        double qualityRange = Math.max(1, qualityCeiling - floor);
        double qualityFraction = MathHelper.clamp((info.qualityPercent() - floor) / qualityRange, 0, 1);

        if (!includeWard) {
            return MathHelper.clamp((int) Math.round(13 * qualityFraction), 0, 13);
        }

        double wardFraction = info.ward() != null ? MathHelper.clamp(info.ward() / (double) maxWard.get(), 0, 1) : 0;
        double wardWeight = wardHeadroomWeight.get();

        double combined = qualityFraction * (1 - wardWeight) + wardFraction * wardWeight;
        return MathHelper.clamp((int) Math.round(13 * combined), 0, 13);
    }

    public int getBarColor(QualityInfo info) {
        if (showMinFloor.get() && effectiveQuality(info) <= minFloorWarningThreshold(info)) return minFloorDisplayColor();

        if (durabilityBarMode.get() == DurabilityBarMode.Priority && info.ward() != null && info.ward() > 0) {
            return switch (wardColorMode.get()) {
                case Custom -> wardCustomColor.get().getPacked() | 0xFF000000;
                case ByValue -> wardByValueColor(info.ward()).getPacked() | 0xFF000000;
                case ByValueRanges -> ColorRangeEntry.resolve(wardRanges.get(), info.ward(), Color.WHITE).getPacked() | 0xFF000000;
                case LoreColor -> info.wardColor() | 0xFF000000;
            };
        }

        // Same Quality Color Mode the text badge uses (see showQuality above) - LoreColor already
        // reflects an over-max/enhanced item's own special color (e.g. aqua) the server itself put
        // on it, so Priority mode's "enhancer bonus active" case needs no separate branch here.
        return switch (qualityColorMode.get()) {
            case LoreColor -> info.qualityColor() | 0xFF000000;
            case Custom -> qualityCustomColor.get().getPacked() | 0xFF000000;
            case ByValue -> qualityByValueColor(info).getPacked() | 0xFF000000;
            case ByValueRanges -> ColorRangeEntry.resolve(qualityRanges.get(), info.qualityPercent(), Color.WHITE).getPacked() | 0xFF000000;
        };
    }

    private int minFloorDisplayColor() {
        if (!minFloorFlash.get()) return minFloorColor.get().getPacked() | 0xFF000000;

        double phase = (System.currentTimeMillis() / 1000.0 * minFloorFlashSpeed.get()) % 1.0;
        SettingColor shown = phase < 0.5 ? minFloorColor.get() : minFloorFlashColor.get();
        return shown.getPacked() | 0xFF000000;
    }

    /** "T" + the number, for every tier including 7 - Ventureland's own lore calls tier 7 "Cosmic" instead of "Tier 7" (see COSMIC_TIER_PATTERN above), but the badge stays the plain T# form regardless, same as every other tier. */
    private static String tierBadgeText(int tier) {
        return "T" + tier;
    }

    /** Tier N (plus that lore line's own color) from a "Tier N Unique Item" line (or "Cosmic Item" for tier 7), or null if the item isn't a unique. */
    private TierInfo findTierInfo(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        for (Text line : lore.lines()) {
            String text = line.getString().trim();

            int tier;
            Matcher m = UNIQUE_TIER_PATTERN.matcher(text);
            if (m.find()) {
                tier = Integer.parseInt(m.group(1));
            } else if (COSMIC_TIER_PATTERN.matcher(text).find()) {
                tier = 7;
            } else {
                continue;
            }

            return new TierInfo(tier, firstSegmentColor(line));
        }

        return null;
    }

    public record TierInfo(int tier, int color) {
    }

    private boolean isTierExcluded(int tier) {
        for (String entry : tierExclude.get().split(",")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;

            try {
                if (Integer.parseInt(entry) == tier) return true;
            } catch (NumberFormatException ignored) {}
        }

        return false;
    }

    private boolean passesTierFilters(ItemStack stack) {
        UniqueFilter unique = uniqueFilter.get();
        int min = minTier.get(), max = maxTier.get();
        if (unique == UniqueFilter.Any && min == 0 && max == 0) return true;

        TierInfo tierInfo = findTierInfo(stack);
        int tier = tierInfo != null ? tierInfo.tier() : -1;
        boolean isUnique = tier != -1;

        if (unique == UniqueFilter.UniqueOnly && !isUnique) return false;
        if (unique == UniqueFilter.NonUniqueOnly && isUnique) return false;

        int effectiveTier = Math.max(tier, 0);
        if (min > 0 && effectiveTier < min) return false;
        if (max > 0 && effectiveTier > max) return false;

        return true;
    }

    /** Public entry point for other modules (e.g. NoiseNotif's Quality alert rules) that need an item's Quality/Ward/Min/Max without duplicating this parsing themselves. */
    public QualityInfo getQualityInfo(ItemStack stack) {
        return findQualityInfo(stack);
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

    public enum UniqueFilter {
        Any,
        UniqueOnly,
        NonUniqueOnly;

        @Override
        public String toString() {
            return switch (this) {
                case Any -> "Any";
                case UniqueOnly -> "Unique Only";
                case NonUniqueOnly -> "Non-Unique Only";
            };
        }
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
        Stacked,
        Priority
    }

    public enum MinFloorWarningMode {
        None,
        Flat,
        Percent
    }

    public enum ColorMode {
        LoreColor,
        Custom,
        ByValue,
        ByValueRanges
    }

    public enum QualityColorMode {
        LoreColor,
        Custom,
        ByValue,
        ByValueRanges
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
        TopCenter,
        TopRight,
        MiddleLeft,
        Center,
        MiddleRight,
        BottomLeft,
        BottomCenter,
        BottomRight
    }
}
