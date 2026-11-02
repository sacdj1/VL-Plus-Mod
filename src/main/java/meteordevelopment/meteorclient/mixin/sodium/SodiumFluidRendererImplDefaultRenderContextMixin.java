/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin.sodium;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.WeightedColorEntry;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.sodium.SodiumBiomeUtil;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.quad.blender.BlendedColorProvider;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(targets = "net.caffeinemc.mods.sodium.fabric.render.FluidRendererImpl$DefaultRenderContext", remap = false)
public abstract class SodiumFluidRendererImplDefaultRenderContextMixin {
    @Unique
    private Ambience ambience;

    @Unique
    private final BlendedColorProvider<FluidState> waterColorProvider = new BlendedColorProvider<>() {
        @Override
        protected int getColor(LevelSlice level, FluidState state, BlockPos pos) {
            return waterColorAt(level, pos);
        }
    };

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        ambience = Modules.get().get(Ambience.class);
    }

    @Inject(method = "getColorProvider", at = @At("HEAD"), cancellable = true)
    private void onGetColorProvider(Fluid fluid, CallbackInfoReturnable<ColorProvider<FluidState>> info) {
        if (!ambience.isActive()) return;

        if (ambience.effectiveLavaColor() != null && fluid.getDefaultState().isIn(FluidTags.LAVA)) {
            info.setReturnValue(this::lavaColorProvider);
        }
        // Unconditional (used to only install this when Custom Water Color or any per-biome color
        // was set, missing Region-only water overrides entirely - a Region's own water color was
        // silently never consulted at all, regardless of this check, since waterColorAt() below
        // didn't look at regions either). waterColorAt() already falls through to vanilla's own
        // per-biome water color when nothing is configured, so installing it unconditionally is a
        // no-op in that case - same output, just no longer gated on a condition that can go stale
        // the moment a new color source is added.
        else if (fluid.getDefaultState().isIn(FluidTags.WATER)) {
            info.setReturnValue(waterColorProvider);
        }
    }

    @Unique
    private void lavaColorProvider(LevelSlice level, BlockPos pos, BlockPos.Mutable posMutable, FluidState state, ModelQuadView quads, int[] colors) {
        // Re-resolved rather than reusing whatever getColorProvider() saw - this runs on a chunk
        // meshing worker thread, possibly a little later, so the season/toggle state it gated on
        // could in principle have already moved on by the time this actually runs. Falls back to
        // the plain manual color (always non-null) for that narrow race instead of risking an NPE
        // on a mesh worker thread.
        Color c = ambience.effectiveLavaColor();
        if (c == null) c = ambience.lavaColor.get();

        Arrays.fill(colors, ColorABGR.pack(c.r, c.g, c.b, c.a));
    }

    @Unique
    private int waterColorAt(LevelSlice level, BlockPos pos) {
        RegistryEntry<Biome> biome = SodiumBiomeUtil.getBiome(level, pos);

        // Same priority order as the normal block-color path (AmbienceBlockColorProvider): Regions,
        // then per-biome, then global, then Custom Water Color, then vanilla's own biome water color
        // last of all - Custom Water Color used to short-circuit ahead of everything else, so a
        // Region or per-biome/global entry for water could never win over it even though a Region
        // is supposed to be the more specific, higher-priority override.
        String dimensionId = MeteorClient.mc.world == null ? "" : MeteorClient.mc.world.getRegistryKey().getValue().toString();
        List<WeightedColorEntry> regionColors = ambience.getRegionBlockColors(dimensionId, pos, Blocks.WATER, biome);
        if (regionColors != null) return ambience.pickWeightedEntry(regionColors, pos).color.getPacked();

        SettingColor custom = ambience.getBiomeBlockColor(ambience.biomeBlockColors.get(), biome, Blocks.WATER, pos);
        if (custom == null) custom = ambience.getGlobalBlockColor(Blocks.WATER, pos);
        if (custom != null) return custom.getPacked();

        if (ambience.customWaterColor.get()) {
            return ambience.waterColor.get().getPacked();
        }

        // The active season's own water color (its "Override Water Color" sentinel, or a specific
        // water entry) - lowest priority, same tier as every other block's season fallback in
        // AmbienceBlockColorProvider.colorsAt(). Missing entirely until now, since water's fluid
        // mesh never goes through that class at all - it has always used this separate path.
        List<WeightedColorEntry> seasonColors = ambience.getSeasonBlockColors(Blocks.WATER);
        if (seasonColors != null) return ambience.pickWeightedEntry(seasonColors, pos).color.getPacked();

        // Biome#getWaterColor() is a plain 0xRRGGBB int with no alpha bits set - force full opacity instead of trusting them
        int packed = biome.value().getWaterColor();
        return new Color((packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF, 255).getPacked();
    }
}
