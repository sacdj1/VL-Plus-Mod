/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Hitboxes;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Shadow public Camera camera;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void render(E entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo info) {
        if (entity instanceof FakePlayerEntity player && player.hideWhenInsideCamera) {
            int cX = MathHelper.floor(this.camera.getPos().x);
            int cY = MathHelper.floor(this.camera.getPos().y);
            int cZ = MathHelper.floor(this.camera.getPos().z);

            if (cX == entity.getBlockX() && cZ == entity.getBlockZ() && (cY == entity.getBlockY() || cY == entity.getBlockY() + 1)) info.cancel();
        }
    }

    // Hitboxes module: general on/off list for hitbox rendering, gating the whole
    // "this.renderHitboxes && ..." condition per-entity.
    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;renderHitboxes:Z"))
    private <E extends Entity> boolean onRenderHitboxesFlag(boolean original, E entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (!original || Modules.get() == null) return original;

        Hitboxes hitboxes = Modules.get().get(Hitboxes.class);
        if (!hitboxes.isActive()) return original;

        return hitboxes.showHitbox(entity);
    }

    // Hitboxes module: force the hitbox-visibility check to ignore isInvisible() for entity types
    // on the invisible-entity list - the "isInvisible()" call at ordinal 1 in render() is
    // specifically the one gating hitbox rendering (ordinal 0 gates the shadow, left untouched).
    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isInvisible()Z", ordinal = 1))
    private <E extends Entity> boolean onIsInvisibleForHitbox(boolean original, E entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (!original || Modules.get() == null) return original;

        Hitboxes hitboxes = Modules.get().get(Hitboxes.class);
        if (!hitboxes.isActive()) return original;

        return !hitboxes.showInvisibleHitbox(entity);
    }

    // Player model rendering in main menu

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void onRenderShadow(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Entity entity, float opacity, float tickDelta, WorldView world, float radius, CallbackInfo info) {
        if (Modules.get().get(NoRender.class).noDeadEntities() && entity instanceof LivingEntity && ((LivingEntity) entity).isDead()) info.cancel();
    }

    @Inject(method = "getSquaredDistanceToCamera(Lnet/minecraft/entity/Entity;)D", at = @At("HEAD"), cancellable = true)
    private void onGetSquaredDistanceToCameraEntity(Entity entity, CallbackInfoReturnable<Double> info) {
        if (camera == null) info.setReturnValue(0.0);
    }

    @Inject(method = "getSquaredDistanceToCamera(DDD)D", at = @At("HEAD"), cancellable = true)
    private void onGetSquaredDistanceToCameraXYZ(double x, double y, double z, CallbackInfoReturnable<Double> info) {
        if (camera == null) info.setReturnValue(0.0);
    }
}
