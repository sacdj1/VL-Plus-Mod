/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.world;

import java.util.HashSet;
import java.util.Set;

/**
 * Modern (1.21.1) block registry paths that trace back to a block which already existed in 1.8.9.
 * Best-effort mapping - not sourced from an official version-history dataset, just knowledge of
 * roughly when each block/feature was added, so treat as approximate.
 */
public class LegacyBlocks {
    private static final String[] DYE_COLORS = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
        "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    private static final String[] BASE = {
        "stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite",
        "grass_block", "dirt", "coarse_dirt", "podzol",
        "cobblestone", "bedrock",
        "water", "lava",
        "sand", "red_sand", "gravel",
        "gold_ore", "iron_ore", "coal_ore",
        "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log",
        "oak_wood", "spruce_wood", "birch_wood", "jungle_wood", "acacia_wood", "dark_oak_wood",
        "oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves",
        "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks",
        "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "dark_oak_sapling",
        "sponge", "wet_sponge", "glass",
        "lapis_ore", "lapis_block",
        "dispenser",
        "sandstone", "chiseled_sandstone", "smooth_sandstone", "cut_sandstone",
        "note_block",
        "powered_rail", "detector_rail",
        "sticky_piston", "cobweb",
        "short_grass", "fern", "dead_bush",
        "piston", "piston_head", "moving_piston",
        "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
        "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy",
        "brown_mushroom", "red_mushroom",
        "gold_block", "iron_block",
        "smooth_stone_slab", "sandstone_slab", "petrified_oak_slab", "cobblestone_slab", "brick_slab", "stone_brick_slab", "nether_brick_slab", "quartz_slab",
        "oak_slab", "spruce_slab", "birch_slab", "jungle_slab", "acacia_slab", "dark_oak_slab",
        "bricks", "tnt", "bookshelf", "mossy_cobblestone", "obsidian",
        "torch", "wall_torch", "fire", "spawner",
        "oak_stairs", "chest",
        "redstone_wire",
        "diamond_ore", "diamond_block",
        "crafting_table", "wheat", "farmland", "furnace",
        "oak_door", "spruce_door", "birch_door", "jungle_door", "acacia_door", "dark_oak_door",
        "ladder", "rail", "cobblestone_stairs",
        "oak_sign", "oak_wall_sign",
        "lever", "stone_pressure_plate", "iron_door",
        "oak_pressure_plate", "spruce_pressure_plate", "birch_pressure_plate", "jungle_pressure_plate", "acacia_pressure_plate", "dark_oak_pressure_plate",
        "redstone_ore", "redstone_torch", "redstone_wall_torch",
        "stone_button", "snow", "ice", "snow_block",
        "cactus", "clay", "sugar_cane", "jukebox",
        "oak_fence", "pumpkin", "netherrack", "soul_sand", "glowstone",
        "nether_portal", "carved_pumpkin", "jack_o_lantern", "cake",
        "repeater",
        "oak_trapdoor", "spruce_trapdoor", "birch_trapdoor", "jungle_trapdoor", "acacia_trapdoor", "dark_oak_trapdoor",
        "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks",
        "brown_mushroom_block", "red_mushroom_block", "mushroom_stem",
        "iron_bars", "glass_pane", "melon",
        "attached_pumpkin_stem", "attached_melon_stem", "pumpkin_stem", "melon_stem",
        "vine", "oak_fence_gate", "brick_stairs", "stone_brick_stairs",
        "mycelium", "lily_pad",
        "nether_bricks", "nether_brick_fence", "nether_brick_stairs", "nether_wart",
        "enchanting_table", "brewing_stand", "cauldron",
        "end_portal", "end_portal_frame", "end_stone", "dragon_egg",
        "redstone_lamp", "cocoa",
        "sandstone_stairs", "ender_chest",
        "tripwire_hook", "tripwire",
        "emerald_ore", "emerald_block",
        "spruce_stairs", "birch_stairs", "jungle_stairs",
        "command_block", "beacon",
        "cobblestone_wall", "mossy_cobblestone_wall",
        "oak_button",
        "anvil", "chipped_anvil", "damaged_anvil",
        "trapped_chest",
        "light_weighted_pressure_plate", "heavy_weighted_pressure_plate",
        "comparator", "daylight_detector",
        "redstone_block", "nether_quartz_ore", "hopper",
        "quartz_block", "chiseled_quartz_block", "quartz_pillar", "quartz_stairs",
        "activator_rail", "dropper",
        "acacia_stairs", "dark_oak_stairs",
        "slime_block", "barrier",
        "iron_trapdoor",
        "prismarine", "prismarine_bricks", "dark_prismarine",
        "prismarine_stairs", "prismarine_brick_stairs", "dark_prismarine_stairs",
        "sea_lantern", "hay_block", "terracotta", "coal_block", "packed_ice",
        "sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony",
        "red_sandstone", "chiseled_red_sandstone", "smooth_red_sandstone", "red_sandstone_stairs", "red_sandstone_slab",
    };

    public static final Set<String> PATHS = new HashSet<>();

    static {
        for (String path : BASE) PATHS.add(path);

        for (String color : DYE_COLORS) {
            PATHS.add(color + "_wool");
            PATHS.add(color + "_carpet");
            PATHS.add(color + "_stained_glass");
            PATHS.add(color + "_stained_glass_pane");
            PATHS.add(color + "_terracotta");
            PATHS.add(color + "_banner");
            PATHS.add(color + "_wall_banner");
        }
    }

    private LegacyBlocks() {
    }

    public static boolean isLegacy(String path) {
        return PATHS.contains(path);
    }
}
