/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import meteordevelopment.meteorclient.utils.render.ForceTintedBakedModel;
import meteordevelopment.meteorclient.utils.world.BiomeTintedBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Both Sodium and vanilla read a block's quads straight from its baked model, so wrapping the
 * model here (rather than hooking either renderer separately) is what makes
 * Ambience.allowUntintedBlocks work identically with or without Sodium - see
 * ForceTintedBakedModel for why this needs to happen at the model level at all.
 */
@Mixin(BlockModels.class)
public abstract class BlockModelsMixin {
    @Unique
    private final Map<BakedModel, ForceTintedBakedModel> vlplus$wrapped = new IdentityHashMap<>();

    @Inject(method = "getModel", at = @At("RETURN"), cancellable = true)
    private void onGetModel(BlockState state, CallbackInfoReturnable<BakedModel> info) {
        Ambience ambience = Modules.get() != null ? Modules.get().get(Ambience.class) : null;
        if (ambience == null || !ambience.allowUntintedBlocks.get()) return;
        if (BiomeTintedBlocks.isTinted(state.getBlock())) return;

        BakedModel original = info.getReturnValue();
        if (original == null || original instanceof ForceTintedBakedModel) return;

        info.setReturnValue(vlplus$wrapped.computeIfAbsent(original, ForceTintedBakedModel::new));
    }
}
