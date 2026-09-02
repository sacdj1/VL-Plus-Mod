/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.TrueSight;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla always renders equipment (armor/held items) on invisible entities, just not the body
 * underneath - so an invisible mob wearing armor normally looks like floating gear. This reveals
 * the body too for entities the TrueSight module allows, so a custom entity model (e.g. from EMF)
 * renders on them like normal instead.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class TrueSightMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Inject(method = "isVisible", at = @At("RETURN"), cancellable = true)
    private void onIsVisible(T entity, CallbackInfoReturnable<Boolean> info) {
        if (info.getReturnValue()) return;

        if (Modules.get().get(TrueSight.class).shouldReveal(entity)) {
            info.setReturnValue(true);
        }
    }
}
