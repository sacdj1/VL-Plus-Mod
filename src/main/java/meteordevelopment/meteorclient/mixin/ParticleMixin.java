/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.utils.render.VLLightOverrideParticle;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Particle.getBrightness(tickDelta) samples the real light level at the particle's own position
 * every frame, generically for every particle type - lets ParticleColor force a fixed packed light
 * value instead, so a recolored particle isn't dimmed/tinted dark by low light wherever it spawns.
 */
@Mixin(Particle.class)
public abstract class ParticleMixin implements VLLightOverrideParticle {
    @Unique
    private boolean vlplus$overrideLight;
    @Unique
    private int vlplus$packedLight;

    @Override
    public void vlplus$setLightOverride(int packedLight) {
        vlplus$overrideLight = true;
        vlplus$packedLight = packedLight;
    }

    @Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true)
    private void vlplus$onGetBrightness(float tickDelta, CallbackInfoReturnable<Integer> cir) {
        if (vlplus$overrideLight) cir.setReturnValue(vlplus$packedLight);
    }
}
