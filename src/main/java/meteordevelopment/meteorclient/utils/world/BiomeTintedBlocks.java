/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.world;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.Set;

/**
 * Blocks Ambience's per-biome colors can actually affect. Anything not in this set has no
 * grass/foliage/water-style color hook (vanilla or Sodium) to intercept, so picking it in the
 * block picker would silently do nothing. Split by category so the flat "override everywhere"
 * settings (custom-grass-color etc.) know which one, if any, applies to a given block.
 */
public class BiomeTintedBlocks {
    // Grass-tinted, smoothly blended across biomes
    public static final Set<Block> GRASS = Set.of(
        Blocks.GRASS_BLOCK, Blocks.FERN, Blocks.SHORT_GRASS, Blocks.POTTED_FERN,
        Blocks.PINK_PETALS, Blocks.SUGAR_CANE, Blocks.LARGE_FERN, Blocks.TALL_GRASS
    );

    // Foliage-tinted - some smoothly blended across biomes, some a fixed vanilla color
    // (birch/spruce/cherry leaves) that Ambience can still override on top of.
    public static final Set<Block> FOLIAGE = Set.of(
        Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
        Blocks.VINE, Blocks.MANGROVE_LEAVES, Blocks.BIRCH_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.CHERRY_LEAVES
    );

    // Water-tinted, smoothly blended across biomes
    public static final Set<Block> WATER = Set.of(Blocks.WATER, Blocks.BUBBLE_COLUMN);

    public static final Set<Block> BLOCKS = Set.of(
        Blocks.GRASS_BLOCK, Blocks.FERN, Blocks.SHORT_GRASS, Blocks.POTTED_FERN,
        Blocks.PINK_PETALS, Blocks.SUGAR_CANE, Blocks.LARGE_FERN, Blocks.TALL_GRASS,
        Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
        Blocks.VINE, Blocks.MANGROVE_LEAVES, Blocks.BIRCH_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.CHERRY_LEAVES,
        Blocks.WATER, Blocks.BUBBLE_COLUMN
    );

    private BiomeTintedBlocks() {
    }

    public static boolean isTinted(Block block) {
        return BLOCKS.contains(block);
    }
}
