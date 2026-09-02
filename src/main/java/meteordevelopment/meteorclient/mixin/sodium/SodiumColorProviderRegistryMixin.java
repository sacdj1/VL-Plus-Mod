/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin.sodium;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import meteordevelopment.meteorclient.utils.sodium.AmbienceBlockColorProvider;
import meteordevelopment.meteorclient.utils.world.BiomeTintedBlocks;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.color.ColorProviderRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wraps the color provider Sodium resolves for every block Ambience can potentially recolor, so
 * per-biome overrides work regardless of whether the block normally goes through a biome-blended
 * provider (grass/foliage) or a fixed constant one (e.g. birch leaves). Also wraps blocks with NO
 * natural color provider at all when Ambience.allowUntintedBlocks is on, so SodiumBlockRendererMixin
 * has something to call once it forces a quad's colorIndex on for those blocks.
 */
@Mixin(value = ColorProviderRegistry.class, remap = false)
public abstract class SodiumColorProviderRegistryMixin {
    // remap=false means this descriptor is taken literally - net.minecraft.block.Block must be
    // given by its runtime (intermediary) name, class_2248, not the Yarn dev-mapped name.
    @Inject(method = "getColorProvider(Lnet/minecraft/class_2248;)Lnet/caffeinemc/mods/sodium/client/model/color/ColorProvider;", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetColorProvider(Block block, CallbackInfoReturnable<ColorProvider<BlockState>> info) {
        ColorProvider<BlockState> original = info.getReturnValue();
        if (BiomeTintedBlocks.isTinted(block)) {
            if (original != null) info.setReturnValue(new AmbienceBlockColorProvider(block, original));
            return;
        }

        Ambience ambience = Modules.get() != null ? Modules.get().get(Ambience.class) : null;
        if (ambience != null && ambience.allowUntintedBlocks.get()) {
            info.setReturnValue(new AmbienceBlockColorProvider(block, original));
        }
    }
}
