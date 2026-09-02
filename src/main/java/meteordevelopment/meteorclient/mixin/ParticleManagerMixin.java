/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.ParticleEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.systems.modules.render.ParticleColor;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
    @Shadow
    @Nullable
    protected abstract <T extends ParticleEffect> Particle createParticle(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ);

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
    private void onAddParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> info) {
        ParticleEvent event = MeteorClient.EVENT_BUS.post(ParticleEvent.get(parameters));

        if (event.isCancelled()) {
            if (parameters.getType() == ParticleTypes.FLASH) info.setReturnValue(createParticle(parameters, x, y, z, velocityX, velocityY, velocityZ));
            else info.cancel();
        }
    }

    // ParticleColor module: hide or recolor particles after creation, since red/green/blue/alpha
    // are set per-particle-type inside createParticle() and need to be overridden afterward.
    @ModifyReturnValue(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("RETURN"))
    private Particle onAddParticleColor(Particle particle, ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        if (particle == null || Modules.get() == null) return particle;

        ParticleColor particleColor = Modules.get().get(ParticleColor.class);
        if (!particleColor.isActive()) return particle;

        if (particleColor.shouldClearInRadius(parameters, x, y, z)) {
            ((ParticleAccessor) particle).invokeSetAlpha(0f);
            return particle;
        }

        if (particleColor.shouldHide(parameters, x, y, z)) {
            ((ParticleAccessor) particle).invokeSetAlpha(0f);
            return particle;
        }

        if (particleColor.shouldRecolor(parameters, x, y, z)) {
            Color color = particleColor.getColor(parameters);
            ParticleAccessor accessor = (ParticleAccessor) particle;

            // The color's own alpha is the blend strength against the particle's original color
            // (0 = untouched, 255 = fully replaced), not the output alpha - overall opacity is a
            // separate, independent setting.
            float blend = color.a / 255f;
            float r = accessor.getRed() + (color.r / 255f - accessor.getRed()) * blend;
            float g = accessor.getGreen() + (color.g / 255f - accessor.getGreen()) * blend;
            float b = accessor.getBlue() + (color.b / 255f - accessor.getBlue()) * blend;

            particle.setColor(r, g, b);
            accessor.invokeSetAlpha(particleColor.getOverallAlpha());
        }

        return particle;
    }

    @Inject(method = "addBlockBreakParticles", at = @At("HEAD"), cancellable = true)
    private void onAddBlockBreakParticles(BlockPos blockPos, BlockState state, CallbackInfo info) {
        if (Modules.get().get(NoRender.class).noBlockBreakParticles()) info.cancel();
    }

    @Inject(method = "addBlockBreakingParticles", at = @At("HEAD"), cancellable = true)
    private void onAddBlockBreakingParticles(BlockPos blockPos, Direction direction, CallbackInfo info) {
        if (Modules.get().get(NoRender.class).noBlockBreakParticles()) info.cancel();
    }
}
