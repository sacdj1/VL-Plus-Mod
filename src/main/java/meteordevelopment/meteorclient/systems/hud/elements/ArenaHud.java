/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.Arena;
import meteordevelopment.meteorclient.utils.player.ArenaPerks;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Shows the perks you're currently holding - name, level, and rarity - read off the "Current
 * Upgrades" book's own lore (see ArenaPerks), plus (if the Arena module is on and has anything)
 * your party members' perks underneath their own name. Each lore line is either just "<Name>
 * <Level>" (bold, colored by rarity) or that plus a dark_gray " (max)" suffix - which means that
 * PERK is at its own max level, not that it's Mythic-tier (max rarity is a completely separate
 * thing, shown via color: red glass / "Mythic" Upgrade lore is the top tier).
 */
public class ArenaHud extends HudElement {
    public static final HudElementInfo<ArenaHud> INFO = new HudElementInfo<>(Hud.GROUP, "arena", "Shows your (and, with the Arena module, your party's) currently-held perks from the Current Upgrades book.", ArenaHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTierNames = settings.createGroup("Tier Names");

    private final Setting<String> bookName = sgGeneral.add(new StringSetting.Builder()
        .name("book-name")
        .description("The exact display name (case-insensitive) of the book to read your own perks from.")
        .defaultValue("Current Upgrades")
        .build()
    );

    private final Setting<Boolean> showPartyMembers = sgGeneral.add(new BoolSetting.Builder()
        .name("show-party-members")
        .description("Also shows party members' perks below your own, read from the Arena module - that module needs to actually be on (with Show Party Perks enabled) for there to be anything here.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showMaxTag = sgGeneral.add(new BoolSetting.Builder()
        .name("show-max-tag")
        .description("Appends \"(MAX)\" to a perk that's at its own max level - independent of its rarity, which is shown by color regardless.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showRarityLabel = sgGeneral.add(new BoolSetting.Builder()
        .name("show-rarity-label")
        .description("Prefixes each perk with its rarity tier name (e.g. \"Epic\"), in addition to coloring it - turn off for a cleaner color-only look matching the book's own styling.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sortByRarity = sgGeneral.add(new BoolSetting.Builder()
        .name("sort-by-rarity")
        .description("Shows the highest-rarity perks first, instead of in whatever order the book (or party broadcast) lists them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text scale.")
        .defaultValue(2.0)
        .min(0.3)
        .sliderRange(0.3, 6)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Renders the text with a shadow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> maxTagColor = sgGeneral.add(new ColorSetting.Builder()
        .name("max-tag-color")
        .description("Color of the \"(MAX)\" tag.")
        .defaultValue(new SettingColor(140, 140, 140))
        .build()
    );

    private final Setting<SettingColor> partyNameColor = sgGeneral.add(new ColorSetting.Builder()
        .name("party-name-color")
        .description("Color of a party member's name header, above their own perks.")
        .defaultValue(new SettingColor(255, 230, 120))
        .build()
    );

    // Only two of these are directly confirmed from real item lore ("Rare Upgrade"/"Epic Upgrade")
    // plus Mythic (explicitly confirmed, red glass) - Common/Uncommon/Legendary are this mod's own
    // best-guess slotting into the usual 6-tier ladder (white/green/blue/dark_purple/gold/red), not
    // yet confirmed from this server's own lore text. Retune freely if any of these are wrong.

    private final Setting<String> commonName = sgTierNames.add(new StringSetting.Builder().name("common-name").description("Display name for white-colored perks.").defaultValue("Common").build());
    private final Setting<String> uncommonName = sgTierNames.add(new StringSetting.Builder().name("uncommon-name").description("Display name for green-colored perks.").defaultValue("Uncommon").build());
    private final Setting<String> rareName = sgTierNames.add(new StringSetting.Builder().name("rare-name").description("Display name for blue-colored perks - confirmed from this server's own \"Rare Upgrade\" lore text.").defaultValue("Rare").build());
    private final Setting<String> epicName = sgTierNames.add(new StringSetting.Builder().name("epic-name").description("Display name for dark_purple-colored perks - confirmed from this server's own \"Epic Upgrade\" lore text.").defaultValue("Epic").build());
    private final Setting<String> legendaryName = sgTierNames.add(new StringSetting.Builder().name("legendary-name").description("Display name for gold-colored perks.").defaultValue("Legendary").build());
    private final Setting<String> mythicName = sgTierNames.add(new StringSetting.Builder().name("mythic-name").description("Display name for red-colored perks - the top tier, confirmed.").defaultValue("Mythic").build());

    private final List<ArenaPerks.PerkEntry> perks = new ArrayList<>();
    private final Map<String, List<ArenaPerks.PerkEntry>> partyPerks = new LinkedHashMap<>();

    public ArenaHud() {
        super(INFO);
        setSize(140, 20);
    }

    @Override
    public void tick(HudRenderer renderer) {
        perks.clear();
        partyPerks.clear();

        if (mc.player != null) {
            perks.addAll(ArenaPerks.readLocal(bookName.get()));
            if (sortByRarity.get()) perks.sort(Comparator.comparingInt(ArenaPerks.PerkEntry::rarityRank).reversed());
        }

        if (showPartyMembers.get()) {
            Arena arena = Modules.get().get(Arena.class);
            if (arena != null && arena.isActive()) {
                for (Map.Entry<String, List<ArenaPerks.PerkEntry>> entry : arena.getPartyPerks().entrySet()) {
                    List<ArenaPerks.PerkEntry> list = new ArrayList<>(entry.getValue());
                    if (sortByRarity.get()) list.sort(Comparator.comparingInt(ArenaPerks.PerkEntry::rarityRank).reversed());
                    partyPerks.put(entry.getKey(), list);
                }
            }
        }

        boolean placeholder = isInEditor() && perks.isEmpty() && partyPerks.isEmpty();

        double width = 0;
        double height = 0;

        if (placeholder) {
            String sample = "Epic Example Perk III";
            width = renderer.textWidth(sample, shadow.get(), scale.get());
            height = renderer.textHeight(shadow.get(), scale.get());
        } else {
            for (ArenaPerks.PerkEntry perk : perks) {
                width = Math.max(width, renderer.textWidth(displayText(perk), shadow.get(), scale.get()));
                height += renderer.textHeight(shadow.get(), scale.get());
            }

            for (Map.Entry<String, List<ArenaPerks.PerkEntry>> entry : partyPerks.entrySet()) {
                width = Math.max(width, renderer.textWidth(entry.getKey(), shadow.get(), scale.get()));
                height += renderer.textHeight(shadow.get(), scale.get());

                for (ArenaPerks.PerkEntry perk : entry.getValue()) {
                    width = Math.max(width, renderer.textWidth(displayText(perk), shadow.get(), scale.get()));
                    height += renderer.textHeight(shadow.get(), scale.get());
                }
            }
        }

        setSize(width, height);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (isInEditor() && perks.isEmpty() && partyPerks.isEmpty()) {
            renderer.text("Epic Example Perk III", x, y, new Color(170, 0, 170), shadow.get(), scale.get());
            return;
        }

        double curY = y;

        for (ArenaPerks.PerkEntry perk : perks) {
            curY = renderPerk(renderer, perk, curY);
        }

        for (Map.Entry<String, List<ArenaPerks.PerkEntry>> entry : partyPerks.entrySet()) {
            renderer.text(entry.getKey(), x, curY, partyNameColor.get(), shadow.get(), scale.get());
            curY += renderer.textHeight(shadow.get(), scale.get());

            for (ArenaPerks.PerkEntry perk : entry.getValue()) {
                curY = renderPerk(renderer, perk, curY);
            }
        }
    }

    /** Name (+ rarity label) stays colored by rarity regardless of max status - only the "(MAX)" tag itself gets its own color, same split the real book's own lore uses. */
    private double renderPerk(HudRenderer renderer, ArenaPerks.PerkEntry perk, double curY) {
        String nameText = nameText(perk);
        renderer.text(nameText, x, curY, new Color(perk.rgb()), shadow.get(), scale.get());

        if (showMaxTag.get() && perk.isMax()) {
            double tagX = x + renderer.textWidth(nameText, shadow.get(), scale.get());
            renderer.text(" (MAX)", tagX, curY, maxTagColor.get(), shadow.get(), scale.get());
        }

        return curY + renderer.textHeight(shadow.get(), scale.get());
    }

    private String nameText(ArenaPerks.PerkEntry perk) {
        if (!showRarityLabel.get()) return perk.nameAndLevel();

        String tier = tierName(perk.rarityRank());
        return tier != null ? tier + " " + perk.nameAndLevel() : perk.nameAndLevel();
    }

    private String displayText(ArenaPerks.PerkEntry perk) {
        return nameText(perk) + (showMaxTag.get() && perk.isMax() ? " (MAX)" : "");
    }

    private String tierName(int rank) {
        return switch (rank) {
            case 5 -> mythicName.get();
            case 4 -> legendaryName.get();
            case 3 -> epicName.get();
            case 2 -> rareName.get();
            case 1 -> uncommonName.get();
            case 0 -> commonName.get();
            default -> null;
        };
    }
}
