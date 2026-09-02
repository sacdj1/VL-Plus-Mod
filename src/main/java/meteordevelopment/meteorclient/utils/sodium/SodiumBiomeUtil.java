/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.sodium;

import meteordevelopment.meteorclient.mixin.sodium.LevelSliceAccessor;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

public class SodiumBiomeUtil {
    private SodiumBiomeUtil() {
    }

    public static RegistryEntry<Biome> getBiome(LevelSlice level, BlockPos pos) {
        return ((LevelSliceAccessor) (Object) level).getBiomeSlice().getBiome(pos.getX(), pos.getY(), pos.getZ());
    }
}
