/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.sodium;

import meteordevelopment.meteorclient.settings.WeightedColorEntry;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import meteordevelopment.meteorclient.utils.render.SpriteAverageColor;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BiomeTintedBlocks;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.blender.BlendedColorProvider;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.texture.Sprite;
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
    //
    // anchorColor: the color of the entry that's actually being blended (null when there isn't
    // one - see the smoothBiomeTransitions catch-all call below). When a neighbouring SAMPLE has
    // no color of its own configured, it used to fall back to that block's plain vanilla color -
    // opaque white for anything non-biome-tinted, since there's no natural tint to fall back to.
    // Sodium's own blending then averages the anchor color together with a run of samples near it
    // that are all white, which - for a regular (non-biome-tinted) block, surrounded by ordinary
    // unconfigured blocks almost everywhere - diluted the chosen color down to barely-there
    // anywhere except deep in the middle of a large solid cluster of the exact same configured
    // color. Repeating the anchor color itself for an unconfigured neighbour instead keeps it at
    // full strength there, and still blends normally wherever a DIFFERENT color is genuinely
    // configured nearby - matching what "blend into a differently-configured neighbour" is
    // actually meant to mean, instead of also fading into nothing everywhere else.
    private BlendedColorProvider<BlockState> blendedFor(boolean acrossBlocks, SettingColor anchorColor, Block anchorBlock) {
        return new BlendedColorProvider<>() {
            @Override
            protected int getColor(LevelSlice level, BlockState state, BlockPos pos) {
                Ambience ambience = Modules.get().get(Ambience.class);
                Block sampleBlock = acrossBlocks ? blockAt(level, pos) : anchorBlock;

                List<WeightedColorEntry> colors = colorsAt(ambience, level, pos, sampleBlock);
                int base = vanillaColorAt(level, pos, sampleBlock);

                if (colors == null) {
                    return anchorColor != null ? Ambience.mixTint(anchorColor, base) : base;
                }

                return Ambience.mixTint(ambience.pickWeightedEntry(colors, pos).color, base);
            }
        };
    }

    private static Block blockAt(LevelSlice level, BlockPos pos) {
        return ((BlockView) level).getBlockState(pos).getBlock();
    }

    // Same correction as ParticleManagerMixin's Colorize mode: divides the picked tint by the
    // block's own (opacity-weighted) average texture color, PER CHANNEL, so a SATURATED pick (not
    // gray/white) centers on that color instead of darkening toward whatever hue the texture already
    // leans. How much correction applies is scaled by the picked tint's own saturation, down to none
    // at all for white - without that, white still visibly changed the block (every channel that was
    // below the texture's own average got boosted back toward 1.0, which satisfies "average equals
    // the picked color" but isn't what picking white is supposed to mean - confirmed: white was still
    // altering blocks instead of leaving them alone). Zero correction at zero saturation means the
    // tint applies fully uncorrected, and a flat white tint IS the identity multiply against the real
    // texture (texture * 1 = texture) - so white now reproduces the block's real, untouched appearance
    // exactly. Fully saturated tints still get the full per-channel correction, each channel clamped
    // to 1.0 independently rather than rescaled together - that's what fixed the original bug (a
    // bright green emerald block tinted pure white landing on magenta instead of white), though that
    // specific case is now also just moot at saturation 0.
    private static SettingColor colorizeCorrect(SettingColor color, Sprite sprite) {
        float[] avg = SpriteAverageColor.get(sprite);

        float tr = color.r / 255f, tg = color.g / 255f, tb = color.b / 255f;
        float correctedR = Math.min(1f, tr / avg[0]);
        float correctedG = Math.min(1f, tg / avg[1]);
        float correctedB = Math.min(1f, tb / avg[2]);

        float maxC = Math.max(tr, Math.max(tg, tb));
        float minC = Math.min(tr, Math.min(tg, tb));
        float saturation = maxC <= 0f ? 0f : (maxC - minC) / maxC;

        return new SettingColor(
            tr + (correctedR - tr) * saturation,
            tg + (correctedG - tg) * saturation,
            tb + (correctedB - tb) * saturation,
            color.a / 255f
        );
    }

    // Only the PRIMARY entry for the current position/quad gets Colorize-corrected - the quad (and
    // therefore its sprite) is only available here, not inside blendedFor's per-neighbour-sample
    // getColor() override, which has no quad context for whatever block each individual sample
    // lands on. Neighbouring samples pulled in by SameBlock/AnyBlock blending keep using the entry's
    // raw, uncorrected color - a documented limitation, not a bug.
    private static SettingColor effectiveColor(WeightedColorEntry entry, ModelQuadView quad) {
        if (entry.mode != WeightedColorEntry.Mode.Colorize) return entry.color;

        // ModelQuadView.getSprite() isn't implemented for every quad backing - confirmed crash:
        // FRAPI mesh-based quads (QuadViewImpl, used by Continuity's CTM/emissive models and by our
        // own ForceTintedBakedModel for force-tinting an otherwise-untinted block like Fire) throw
        // UnsupportedOperationException unconditionally instead of returning a real sprite. Falling
        // back to the entry's raw color (same as Replace) for those rather than crashing the chunk
        // mesh worker - there's no sprite to correct against there anyway.
        try {
            return colorizeCorrect(entry.color, quad.getSprite());
        } catch (UnsupportedOperationException e) {
            return entry.color;
        }
    }

    // Regions take priority over per-biome colors, over global blocks, over the active season's own
    // blocks (the lowest-priority tier - same relationship Auto Season's sky/cloud/lightning/lava
    // colors have with their own Custom X Color toggles) - checked in that order.
    private List<WeightedColorEntry> colorsAt(Ambience ambience, LevelSlice level, BlockPos pos, Block targetBlock) {
        RegistryEntry<Biome> biome = SodiumBiomeUtil.getBiome(level, pos);

        List<WeightedColorEntry> colors = ambience.getRegionBlockColors(dimensionId(), pos, targetBlock, biome);
        if (colors != null) return colors;

        colors = ambience.getBiomeBlockColors(ambience.biomeBlockColors.get(), biome, targetBlock);
        if (colors != null) return colors;

        colors = ambience.getGlobalBlockColors(targetBlock);
        if (colors != null) return colors;

        return ambience.getSeasonBlockColors(targetBlock);
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
    private SettingColor flatOverride(Ambience ambience, Block targetBlock) {
        if (BiomeTintedBlocks.GRASS.contains(targetBlock)) return ambience.customGrassColor.get() ? ambience.grassColor.get() : null;
        if (BiomeTintedBlocks.FOLIAGE.contains(targetBlock)) return ambience.customFoliageColor.get() ? ambience.foliageColor.get() : null;
        if (BiomeTintedBlocks.WATER.contains(targetBlock)) return ambience.customWaterColor.get() ? ambience.waterColor.get() : null;

        return null;
    }

    // grass_block's bottom and side quads (tintindex -1 in grass_block.json) show their own real
    // dirt texture, never biome-tinted in vanilla - only its top face and a thin side-overlay strip
    // (tintindex 0) are. Sodium normally never even calls getColors() for a tintindex -1 quad at
    // all (see SodiumBlockRendererMixin for why that's no longer true here), so without this,
    // EVERY quad of the block - dirt sides included - would otherwise be forced through whatever's
    // configured for Blocks.GRASS_BLOCK once that mixin makes the call happen. Routing a grass
    // block's own untinted quads through Blocks.DIRT's existing color lists instead reuses the
    // same per-block config system already in place (no new UI/settings needed) and keeps it
    // genuinely separate from the top's color, falling back to DIRT's own vanilla (untouched)
    // appearance when nothing is configured for it, same as any other unconfigured block.
    private Block effectiveBlockFor(ModelQuadView quad) {
        return (block == Blocks.GRASS_BLOCK && quad.getColorIndex() < 0) ? Blocks.DIRT : block;
    }

    // Blocks with no natural color provider (only wrapped when Ambience.allowUntintedBlocks is on)
    // have no fallback to defer to. The colors array comes in freshly allocated (all zeros, i.e.
    // opaque black), not pre-filled by Sodium - leaving it alone here used to multiply the block's
    // texture by black. Fill it opaque white instead so the block renders unmodified until it
    // actually has a color configured.
    private void callFallback(LevelSlice level, BlockPos pos, BlockPos.Mutable posMutable, BlockState state, ModelQuadView quad, int[] colors) {
        if (fallback != null) fallback.getColors(level, pos, posMutable, state, quad, colors);
        else Arrays.fill(colors, 0xFFFFFFFF);
    }

    @Override
    public void getColors(LevelSlice level, BlockPos pos, BlockPos.Mutable posMutable, BlockState state, ModelQuadView quad, int[] colors) {
        Ambience ambience = Modules.get().get(Ambience.class);

        if (!ambience.isActive()) {
            callFallback(level, pos, posMutable, state, quad, colors);
            return;
        }

        Block effectiveBlock = effectiveBlockFor(quad);
        boolean substituted = effectiveBlock != block;

        SettingColor flat = flatOverride(ambience, effectiveBlock);
        if (flat != null) {
            Arrays.fill(colors, Ambience.mixTint(flat, vanillaColorAt(level, pos, effectiveBlock)));
            return;
        }

        List<WeightedColorEntry> list = colorsAt(ambience, level, pos, effectiveBlock);

        if (list == null) {
            if (substituted) {
                // effectiveBlock stands in for a naturally-untinted quad of a DIFFERENT, tinted
                // block (grass_block's dirt sides, routed through Blocks.DIRT above) - with nothing
                // configured for it, the right fallback is "leave this quad's real texture alone",
                // not the ORIGINAL block's own fallback provider (built for grass_block's biome
                // tint, never meant to run on an untinted quad) and not smooth-biome-transitions
                // blending (there's no biome tint on this quad to smooth in the first place).
                Arrays.fill(colors, 0xFFFFFFFF);
            } else if (ambience.smoothBiomeTransitions.get()) {
                // Nothing claims this block at all - region, biome, global, and the active season
                // all came back empty. Normally there's nothing to do but defer to vanilla/Sodium's
                // own blending, which has no idea a neighbouring biome has an override and so won't
                // blend toward it. With smoothBiomeTransitions on, run the same per-sample blending
                // used for configured blocks anyway (same-block mode only, since there's no picked
                // entry here to carry a per-color blend mode) - each sample already falls back to
                // the real vanilla color individually, so this blends both ways.
                blendedFor(false, null, effectiveBlock).getColors(level, pos, posMutable, state, quad, colors);
            } else {
                callFallback(level, pos, posMutable, state, quad, colors);
            }
            return;
        }

        WeightedColorEntry entry = ambience.pickWeightedEntry(list, pos);
        SettingColor effective = effectiveColor(entry, quad);

        switch (entry.blend) {
            case None -> Arrays.fill(colors, Ambience.mixTint(effective, vanillaColorAt(level, pos, effectiveBlock)));
            case SameBlock -> blendedFor(false, effective, effectiveBlock).getColors(level, pos, posMutable, state, quad, colors);
            case AnyBlock -> blendedFor(true, effective, effectiveBlock).getColors(level, pos, posMutable, state, quad, colors);
        }
    }
}
