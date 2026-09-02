/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.world;

import java.util.HashSet;
import java.util.Set;

/**
 * Modern (1.21.1) item registry paths that trace back to an item which already existed in 1.8.9.
 * Best-effort mapping - not sourced from an official version-history dataset, just knowledge of
 * roughly when each item/feature was added, so treat as approximate. Item ids that also name a
 * block (e.g. "stone", "oak_log") are intentionally left to LegacyBlocks - this only covers
 * pure-item registrations (tools, food, drops, etc.) plus a handful of block items whose block
 * counterpart isn't itself legacy-relevant (e.g. "bed").
 */
public class LegacyItems {
    private static final String[] DYE_COLORS = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
        "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    private static final String[] TOOL_MATERIALS = {"wooden", "stone", "iron", "golden", "diamond"};
    private static final String[] TOOL_TYPES = {"sword", "shovel", "pickaxe", "axe", "hoe"};
    private static final String[] ARMOR_MATERIALS = {"leather", "chainmail", "iron", "golden", "diamond"};
    private static final String[] ARMOR_PIECES = {"helmet", "chestplate", "leggings", "boots"};

    private static final String[] BASE = {
        // Food
        "apple", "golden_apple", "enchanted_golden_apple", "bread", "porkchop", "cooked_porkchop",
        "beef", "cooked_beef", "chicken", "cooked_chicken", "rotten_flesh", "spider_eye", "carrot",
        "golden_carrot", "potato", "baked_potato", "poisonous_potato", "pumpkin_pie", "melon_slice",
        "cookie", "cake", "mushroom_stew", "rabbit", "cooked_rabbit", "rabbit_stew", "rabbit_foot",
        "rabbit_hide", "milk_bucket", "beetroot", "beetroot_soup",

        // Materials / drops
        "coal", "charcoal", "diamond", "emerald", "iron_ingot", "gold_ingot", "iron_nugget",
        "gold_nugget", "quartz", "flint", "clay_ball", "brick", "netherbrick", "gunpowder",
        "string", "feather", "leather", "bone", "bone_meal", "ink_sac", "glowstone_dust",
        "redstone", "slime_ball", "blaze_rod", "blaze_powder", "magma_cream", "ghast_tear",
        "nether_wart", "nether_star", "prismarine_shard", "prismarine_crystals",
        "glistering_melon_slice", "fermented_spider_eye", "egg", "sugar", "wheat", "wheat_seeds",
        "melon_seeds", "pumpkin_seeds", "paper", "book", "enchanted_book",

        // Dyes (as their own damage-value-derived items pre-flattening)
        "bone_meal", "lapis_lazuli", "cocoa_beans", "ink_sac",

        // Tools / utility
        "flint_and_steel", "shears", "fishing_rod", "carrot_on_a_stick", "bow", "arrow",
        "tipped_arrow", "shield", "compass", "clock", "map", "filled_map", "empty_map",
        "name_tag", "lead", "saddle", "bucket", "water_bucket", "lava_bucket",
        "glass_bottle", "potion", "splash_potion", "brewing_stand", "cauldron",
        "writable_book", "written_book",

        // Horse armor
        "iron_horse_armor", "golden_horse_armor", "diamond_horse_armor",

        // Vehicles
        "minecart", "chest_minecart", "furnace_minecart", "tnt_minecart", "hopper_minecart",
        "oak_boat",

        // Combat / misc
        "tnt", "snowball", "ender_pearl", "ender_eye", "experience_bottle",
        "firework_rocket", "firework_star", "totem_of_undying",

        // Spawn eggs for legacy mobs
        "bat_spawn_egg", "blaze_spawn_egg", "cave_spider_spawn_egg", "chicken_spawn_egg",
        "cow_spawn_egg", "creeper_spawn_egg", "donkey_spawn_egg", "elder_guardian_spawn_egg",
        "enderman_spawn_egg", "endermite_spawn_egg", "ghast_spawn_egg", "guardian_spawn_egg",
        "horse_spawn_egg", "iron_golem_spawn_egg", "magma_cube_spawn_egg", "mooshroom_spawn_egg",
        "mule_spawn_egg", "ocelot_spawn_egg", "pig_spawn_egg", "rabbit_spawn_egg",
        "sheep_spawn_egg", "silverfish_spawn_egg", "skeleton_spawn_egg", "skeleton_horse_spawn_egg",
        "slime_spawn_egg", "snow_golem_spawn_egg", "spider_spawn_egg", "squid_spawn_egg",
        "villager_spawn_egg", "witch_spawn_egg", "wolf_spawn_egg", "zombie_spawn_egg",
        "zombie_horse_spawn_egg", "zombie_villager_spawn_egg", "zombified_piglin_spawn_egg",
        "wither_skeleton_spawn_egg",
    };

    public static final Set<String> PATHS = new HashSet<>();

    static {
        for (String path : BASE) PATHS.add(path);

        for (String material : TOOL_MATERIALS) {
            for (String type : TOOL_TYPES) PATHS.add(material + "_" + type);
        }

        for (String material : ARMOR_MATERIALS) {
            for (String piece : ARMOR_PIECES) PATHS.add(material + "_" + piece);
        }

        for (String color : DYE_COLORS) {
            PATHS.add(color + "_dye");
        }

        // Music discs (renamed "record_*" -> "music_disc_*" in the Flattening)
        for (String disc : new String[]{"13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad", "ward", "11", "wait"}) {
            PATHS.add("music_disc_" + disc);
        }
    }

    private LegacyItems() {
    }

    public static boolean isLegacy(String path) {
        return PATHS.contains(path) || LegacyBlocks.isLegacy(path);
    }
}
