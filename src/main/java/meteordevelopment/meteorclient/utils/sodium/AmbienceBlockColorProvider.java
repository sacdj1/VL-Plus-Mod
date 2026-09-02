/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.sodium;

import meteordevelopment.meteorclient.settings.WeightedColorEntry;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BiomeTintedBlocks;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.blender.BlendedColorProvider;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.BlockView;
import net.minecraft.world.biome.Biome;

import java.util.Arrays;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Wraps whatever color provider Sodium/vanilla would normally use for a block (biome-blended
 * grass/foliage, or a fixed constant for things like birch leaves) so Ambience's per-biome colors
 * can override it. Falls straight through to the original provider whenever there's no override
 * for the biome the block is actually in, so vanilla/Sodium behaviour is unchanged by default.
 */
public class AmbienceBlockColorProvider implements ColorProvider<BlockState> {
    private final Block block;
    private final ColorProvider<BlockState> fallback;

    public AmbienceBlockColorProvider(Block block, ColorProvider<BlockState> fallback) {
        this.block = block;
        this.fallback = fallback;
    }

    // A fresh instance per call rather than a shared field - Sodium's chunk meshing runs on
    // multiple worker threads that can all be resolving colors for this same block/provider
    // instance concurrently, so there's nowhere safe to stash "which mode is this sample for"
    // other than capturing it in a per-call closure.
    private BlendedColorProvider<BlockState> blendedFor(boolean acrossBlocks) {
        return new BlendedColorProvider<>() {
            @Override
            protected int getColor(LevelSlice level, BlockState state, BlockPos pos) {
                Ambience ambience = Modules.get().get(Ambience.class);
                Block sampleBlock = acrossBlocks ? blockAt(level, pos) : block;

                List<WeightedColorEntry> colors = colorsAt(ambience, level, pos, sampleBlock);
                int base = vanillaColorAt(level, pos, sampleBlock);
                if (colors == null) return base;

                return Ambience.mixTint(ambience.pickWeightedEntry(colors, pos).color, base);
            }
        };
    }

    private static Block blockAt(LevelSlice level, BlockPos pos) {
        return ((BlockView) level).getBlockState(pos).getBlock();
    }

    // Regions take priority over per-biome colors - checked first.
    private List<WeightedColorEntry> colorsAt(Ambience ambience, LevelSlice level, BlockPos pos, Block targetBlock) {
        RegistryEntry<Biome> biome = SodiumBiomeUtil.getBiome(level, pos);

        List<WeightedColorEntry> colors = ambience.getRegionBlockColors(dimensionId(), pos, targetBlock, biome);
        if (colors != null) return colors;

        return ambience.getBiomeBlockColors(ambience.biomeBlockColors.get(), biome, targetBlock);
    }

    private static String dimensionId() {
        return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
    }

    private int vanillaColorAt(LevelSlice level, BlockPos pos, Block targetBlock) {
        BlockRenderView view = (BlockRenderView) level;

        int packed;
        if (BiomeTintedBlocks.GRASS.contains(targetBlock)) packed = BiomeColors.getGrassColor(view, pos);
        else if (BiomeTintedBlocks.FOLIAGE.contains(targetBlock)) packed = BiomeColors.getFoliageColor(view, pos);
        else if (BiomeTintedBlocks.WATER.contains(targetBlock)) packed = BiomeColors.getWaterColor(view, pos);
        else return -1; // opaque white - no vanilla color source for force-mode blocks with no override

        // BiomeColors' packed ints are plain 0xRRGGBB with no alpha bits set - force full opacity
        // instead of trusting them (same fix as the earlier water-invisible bug).
        return 0xFF000000 | packed;
    }

    // The flat "override everywhere" settings take priority over everything else - checked first.
    private SettingColor flatOverride(Ambience ambience) {
        if (BiomeTintedBlocks.GRASS.contains(block)) return ambience.customGrassColor.get() ? ambience.grassColor.get() : null;
        if (BiomeTintedBlocks.FOLIAGE.contains(block)) return ambience.customFoliageColor.get() ? ambience.foliageColor.get() : null;
        if (BiomeTintedBlocks.WATER.contains(block)) return ambience.customWaterColor.get() ? ambience.waterColor.get() : null;

        return null;
    }

    // Blocks with no natural color provider (only wrapped when Ambience.allowUntintedBlocks is on)
    // have no fallback to defer to - just leave the vertex colors as Sodium already set them.
    private void callFallback(LevelSlice level, BlockPos pos, BlockPos.Mutable posMutable, BlockState state, ModelQuadView quad, int[] colors) {
        if (fallback != null) fallback.getColors(level, pos, posMutable, state, quad, colors);
    }

    @Override
    public void getColors(LevelSlice level, BlockPos pos, BlockPos.Mutable posMutable, BlockState state, ModelQuadView quad, int[] colors) {
        Ambience ambience = Modules.get().get(Ambience.class);

        if (!ambience.isActive()) {
            callFallback(level, pos, posMutable, state, quad, colors);
            return;
        }

        SettingColor flat = flatOverride(ambience);
        if (flat != null) {
            Arrays.fill(colors, Ambience.mixTint(flat, vanillaColorAt(level, pos, block)));
            return;
        }

        if (ambience.biomeBlockColors.get().isEmpty() && ambience.regions.get().isEmpty()) {
            callFallback(level, pos, posMutable, state, quad, colors);
            return;
        }

        List<WeightedColorEntry> list = colorsAt(ambience, level, pos, block);

        if (list == null) {
            // This block's own biome isn't configured - normally there's nothing to do but defer
            // to vanilla/Sodium's own blending, which has no idea a neighbouring biome has an
            // override and so won't blend toward it. With smoothBiomeTransitions on, run the same
            // per-sample blending used for configured blocks anyway (same-block mode only, since
            // there's no picked entry here to carry a per-color blend mode) - each sample already
            // falls back to the real vanilla color individually, so this blends both ways.
            if (ambience.smoothBiomeTransitions.get()) {
                blendedFor(false).getColors(level, pos, posMutable, state, quad, colors);
            } else {
                callFallback(level, pos, posMutable, state, quad, colors);
            }
            return;
        }

        WeightedColorEntry entry = ambience.pickWeightedEntry(list, pos);

        switch (entry.blend) {
            case None -> Arrays.fill(colors, Ambience.mixTint(entry.color, vanillaColorAt(level, pos, block)));
            case SameBlock -> blendedFor(false).getColors(level, pos, posMutable, state, quad, colors);
            case AnyBlock -> blendedFor(true).getColors(level, pos, posMutable, state, quad, colors);
        }
    }
}
