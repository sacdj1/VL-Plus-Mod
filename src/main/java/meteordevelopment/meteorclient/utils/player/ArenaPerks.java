/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.player;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Reads perks off the "Current Upgrades" book's lore, and encodes/decodes them into a compact
 * chat-transmittable form - shared between ArenaHud (renders your own perks, read locally) and
 * the Arena module (broadcasts your own perks to party chat, and decodes party members' own
 * broadcasts for the HUD to show alongside yours).
 */
public final class ArenaPerks {
    public record PerkEntry(String nameAndLevel, boolean isMax, int rarityRank, int rgb) {
    }

    private ArenaPerks() {
    }

    public static List<PerkEntry> readLocal(String bookName) {
        List<PerkEntry> perks = new ArrayList<>();
        if (mc.player == null) return perks;

        ItemStack book = findBook(bookName);
        if (book == null) return perks;

        LoreComponent lore = book.get(DataComponentTypes.LORE);
        if (lore == null) return perks;

        for (Text line : lore.lines()) {
            PerkEntry entry = parseLine(line);
            if (entry != null) perks.add(entry);
        }

        return perks;
    }

    private static ItemStack findBook(String bookName) {
        String target = bookName.toLowerCase(Locale.ROOT);
        if (target.isEmpty()) return null;

        var inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.getName().getString().toLowerCase(Locale.ROOT).equals(target)) return stack;
        }

        return null;
    }

    private static PerkEntry parseLine(Text line) {
        String[] mainText = {null};
        int[] rgb = {0xFFFFFF};
        boolean[] isMax = {false};

        line.visit((style, asString) -> {
            if (asString.isEmpty()) return Optional.empty();

            if (mainText[0] == null) {
                mainText[0] = asString;
                if (style.getColor() != null) rgb[0] = style.getColor().getRgb();
            } else if (asString.contains("(max)") || asString.contains("(MAX)")) {
                isMax[0] = true;
            }

            return Optional.empty();
        }, Style.EMPTY);

        if (mainText[0] == null || mainText[0].isBlank()) return null;

        return new PerkEntry(mainText[0].trim(), isMax[0], rarityRank(rgb[0]), rgb[0]);
    }

    /** Higher = rarer. -1 for any color not one of the 6 recognized tiers. */
    public static int rarityRank(int rgb) {
        if (sameColor(rgb, Formatting.RED)) return 5;
        if (sameColor(rgb, Formatting.GOLD)) return 4;
        if (sameColor(rgb, Formatting.DARK_PURPLE)) return 3;
        if (sameColor(rgb, Formatting.BLUE)) return 2;
        if (sameColor(rgb, Formatting.GREEN)) return 1;
        if (sameColor(rgb, Formatting.WHITE)) return 0;
        return -1;
    }

    private static boolean sameColor(int rgb, Formatting formatting) {
        return Objects.equals(formatting.getColorValue(), rgb);
    }

    // Wire format for party chat: one perk per "name,rank,max" triple, perks joined by ";" - sends
    // the rarity RANK, not the raw color, so the receiving end re-derives its own tier name/color
    // from ITS OWN settings rather than trusting whatever this client's lore color literally was.

    public static String encode(List<PerkEntry> perks) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < perks.size(); i++) {
            if (i > 0) sb.append(';');

            PerkEntry perk = perks.get(i);
            sb.append(perk.nameAndLevel().replace(",", "").replace(";", ""))
                .append(',').append(perk.rarityRank())
                .append(',').append(perk.isMax() ? '1' : '0');
        }

        return sb.toString();
    }

    public static List<PerkEntry> decode(String payload) {
        List<PerkEntry> perks = new ArrayList<>();
        if (payload.isBlank()) return perks;

        for (String part : payload.split(";")) {
            String[] fields = part.split(",");
            if (fields.length != 3) continue;

            try {
                int rank = Integer.parseInt(fields[1]);
                boolean isMax = "1".equals(fields[2]);
                perks.add(new PerkEntry(fields[0], isMax, rank, rankColor(rank)));
            } catch (NumberFormatException ignored) {}
        }

        return perks;
    }

    private static int rankColor(int rank) {
        Integer color = switch (rank) {
            case 5 -> Formatting.RED.getColorValue();
            case 4 -> Formatting.GOLD.getColorValue();
            case 3 -> Formatting.DARK_PURPLE.getColorValue();
            case 2 -> Formatting.BLUE.getColorValue();
            case 1 -> Formatting.GREEN.getColorValue();
            case 0 -> Formatting.WHITE.getColorValue();
            default -> 0xFFFFFF;
        };

        return color != null ? color : 0xFFFFFF;
    }
}
