/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.world;

import java.util.Set;

/**
 * Modern (1.21.1) biome registry paths that trace back to a biome which already existed in 1.8.9.
 * Best-effort mapping - biomes removed since 1.8.9 (like the old "hills" variants) aren't listed
 * since they no longer exist in the current registry anyway.
 */
public class LegacyBiomes {
    public static final Set<String> PATHS = Set.of(
        "ocean", "deep_ocean", "frozen_ocean",
        "plains", "sunflower_plains",
        "desert",
        "windswept_hills", "windswept_gravelly_hills", "windswept_forest",
        "forest", "flower_forest",
        "birch_forest", "old_growth_birch_forest",
        "dark_forest",
        "taiga", "snowy_taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga",
        "savanna", "savanna_plateau", "windswept_savanna",
        "jungle", "sparse_jungle",
        "badlands", "wooded_badlands",
        "swamp",
        "river", "frozen_river",
        "beach", "snowy_beach", "stony_shore",
        "snowy_plains", "ice_spikes",
        "mushroom_fields",
        "nether_wastes",
        "the_end"
    );

    private LegacyBiomes() {
    }

    public static boolean isLegacy(String path) {
        return PATHS.contains(path);
    }
}
