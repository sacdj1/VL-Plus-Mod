/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.world;

import java.util.Set;

/**
 * Modern (1.21.1) entity registry paths that trace back to an entity which already existed in
 * 1.8.9 - mobs as well as non-living entities (item frames, armor stands, etc.), since this list
 * is shared by every entity type picker, not just mob-only ones. Best-effort mapping - not sourced
 * from an official version-history dataset, just knowledge of roughly when each entity was added,
 * so treat as approximate. Some entities were renamed since (e.g. "zombie_pigman" ->
 * "zombified_piglin", "snowman" -> "snow_golem") - this uses the current registry path.
 */
public class LegacyMobs {
    public static final Set<String> PATHS = Set.of(
        // Passive
        "bat", "chicken", "cow", "mooshroom", "ocelot", "pig", "rabbit", "sheep", "squid",
        "snow_golem", "villager", "iron_golem",

        // Neutral
        "wolf", "horse", "donkey", "mule", "skeleton_horse", "zombie_horse", "enderman",

        // Hostile
        "blaze", "cave_spider", "creeper", "endermite", "ghast", "guardian", "elder_guardian",
        "magma_cube", "silverfish", "skeleton", "wither_skeleton", "slime", "spider", "witch",
        "zombie", "zombie_villager", "zombified_piglin",

        // Bosses
        "ender_dragon", "wither",

        // Player-adjacent
        "player",

        // Non-living
        "item_frame", "armor_stand", "giant"
    );

    private LegacyMobs() {
    }

    public static boolean isLegacy(String path) {
        return PATHS.contains(path);
    }
}
