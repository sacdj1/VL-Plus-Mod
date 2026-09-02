/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin.sodium;

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

        if (ambience.customLavaColor.get() && fluid.getDefaultState().isIn(FluidTags.LAVA)) {
            info.setReturnValue(this::lavaColorProvider);
        }
        else if (fluid.getDefaultState().isIn(FluidTags.WATER) && (ambience.customWaterColor.get() || !ambience.biomeBlockColors.get().isEmpty())) {
            info.setReturnValue(waterColorProvider);
        }
    }

    @Unique
    private void lavaColorProvider(LevelSlice level, BlockPos pos, BlockPos.Mutable posMutable, FluidState state, ModelQuadView quads, int[] colors) {
        Color c = ambience.lavaColor.get();
        Arrays.fill(colors, ColorABGR.pack(c.r, c.g, c.b, c.a));
    }

    @Unique
    private int waterColorAt(LevelSlice level, BlockPos pos) {
        if (ambience.customWaterColor.get()) {
            return ambience.waterColor.get().getPacked();
        }

        RegistryEntry<Biome> biome = SodiumBiomeUtil.getBiome(level, pos);
        SettingColor custom = ambience.getBiomeBlockColor(ambience.biomeBlockColors.get(), biome, Blocks.WATER, pos);

        if (custom != null) return custom.getPacked();

        // Biome#getWaterColor() is a plain 0xRRGGBB int with no alpha bits set - force full opacity instead of trusting them
        int packed = biome.value().getWaterColor();
        return new Color((packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF, 255).getPacked();
    }
}
