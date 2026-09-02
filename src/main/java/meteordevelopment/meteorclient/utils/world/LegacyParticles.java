/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.world;

import java.util.Set;

/**
 * Modern (1.21.1) particle registry paths that trace back to a particle which already existed in
 * 1.8.9, under a different name (pre-1.13 "flattening" rename). Best-effort mapping - not sourced
 * from an official version-history dataset, just knowledge of roughly when each particle was added
 * or renamed - treat as approximate, same caveat as LegacyMobs.
 */
public class LegacyParticles {
    public static final Set<String> PATHS = Set.of(
        "poof", "explosion", "explosion_emitter", "firework", "bubble", "splash", "fishing",
        "underwater", "crit", "enchanted_hit", "smoke", "large_smoke", "effect", "instant_effect",
        "entity_effect", "witch", "note", "portal", "enchant", "flame", "lava", "cloud", "dust",
        "item_snowball", "item_slime", "heart", "angry_villager", "happy_villager", "mycelium",
        "item", "block", "elder_guardian"
    );

    private LegacyParticles() {
    }

    public static boolean isLegacy(String path) {
        return PATHS.contains(path);
    }
}
