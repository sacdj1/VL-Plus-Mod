/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.utils.render.VLOutlineParticle;
import meteordevelopment.meteorclient.utils.render.VLRecolorableParticle;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BillboardParticle.buildGeometry() is the concrete, unoverridden render path shared by almost every
 * particle type (DamageParticle/"redstone" hit particles, dust, block/item crumbs, etc. - anything
 * that doesn't hand-roll its own rendering) - it reads this particle's red/green/blue/alpha fields
 * to build the quad's vertex colors every frame. Several particle types (DamageParticle confirmed,
 * likely others) animate those same fields every TICK as part of their own look (DamageParticle
 * fades green/blue toward zero to look like it's "aging toward red"), which silently undid
 * ParticleManagerMixin's one-time Particle.setColor() call made right after spawning within a few
 * frames. Reasserting the override right here, at the START of the one method that actually reads
 * these fields for rendering, wins regardless of what any particle type's own tick() does to them in
 * between frames - one hook, every particle type, instead of one mixin per particle class.
 */
@Mixin(BillboardParticle.class)
public abstract class BillboardParticleMixin implements VLRecolorableParticle, VLOutlineParticle {
    // x/y/z/prevPos*/getBrightness are declared on Particle, not BillboardParticle itself - @Shadow
    // here (targeting BillboardParticle) can't see them, confirmed the hard way (a silent "cannot
    // find target" warning at compile time that would have been a runtime mixin-apply failure).
    // ParticleAccessor already targets Particle.class directly for exactly this reason - reused here
    // instead of adding a second, differently-targeted set of accessors for the same fields.
    @Shadow public abstract BillboardParticle.Rotator getRotator();
    @Shadow protected abstract float getMinU();
    @Shadow protected abstract float getMaxU();
    @Shadow protected abstract float getMinV();
    @Shadow protected abstract float getMaxV();

    @Unique
    private boolean vlplus$recolor;
    @Unique
    private float vlplus$r, vlplus$g, vlplus$b;

    @Unique
    private boolean vlplus$outline;
    @Unique
    private float vlplus$outlineR, vlplus$outlineG, vlplus$outlineB, vlplus$outlineA;

    @Override
    public void vlplus$setRecolor(float r, float g, float b) {
        vlplus$recolor = true;
        vlplus$r = r;
        vlplus$g = g;
        vlplus$b = b;
    }

    @Override
    public void vlplus$setOutline(float r, float g, float b, float a) {
        vlplus$outline = true;
        vlplus$outlineR = r;
        vlplus$outlineG = g;
        vlplus$outlineB = b;
        vlplus$outlineA = a;
    }

    @Override
    public void vlplus$clearOutline() {
        vlplus$outline = false;
    }

    @Inject(method = "buildGeometry", at = @At("HEAD"))
    private void vlplus$onBuildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta, CallbackInfo ci) {
        if (vlplus$recolor) ((Particle) (Object) this).setColor(vlplus$r, vlplus$g, vlplus$b);

        // Drawn first, at HEAD, so the real (smaller) quad the rest of buildGeometry() goes on to
        // build right after this draws on top of it - what's left visible is just the border where
        // this enlarged duplicate pokes out past the real one. Rebuilt from scratch here (same
        // quaternion/interpolated-position/UV math BillboardParticle's own private quad builder
        // uses, confirmed via decompile - there's no way to just call that builder a second time
        // with a different size/color from outside it) rather than reusing any of BillboardParticle's
        // own private helper methods, which Mixin can't target reliably (unnamed/obfuscated). Skips
        // the particle's own per-instance angle/prevAngle Z-rotation refinement that the real quad
        // builder applies (most particle types never set a nonzero angle anyway, and this is already
        // an approximate "border", not a precise one).
        if (vlplus$outline) vlplus$buildOutlineQuad(vertexConsumer, camera, tickDelta);
    }

    @Unique
    private void vlplus$buildOutlineQuad(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        ParticleAccessor accessor = (ParticleAccessor) this;

        Quaternionf q = new Quaternionf();
        getRotator().setRotation(q, camera, tickDelta);

        Vec3d camPos = camera.getPos();
        float ix = (float) (MathHelper.lerp(tickDelta, accessor.getPrevPosX(), accessor.getX()) - camPos.getX());
        float iy = (float) (MathHelper.lerp(tickDelta, accessor.getPrevPosY(), accessor.getY()) - camPos.getY());
        float iz = (float) (MathHelper.lerp(tickDelta, accessor.getPrevPosZ(), accessor.getZ()) - camPos.getZ());

        // A touch bigger than the real quad (built with this same scale formula, size = getSize()
        // with no multiplier) - how much bigger is a visual compromise, not a true fixed screen-pixel
        // width (the engine has no cheap way to do that for a billboarded quad at every distance).
        float size = ((BillboardParticle) (Object) this).getSize(tickDelta) * 1.15f;

        float minU = getMinU(), maxU = getMaxU(), minV = getMinV(), maxV = getMaxV();
        int light = accessor.invokeGetBrightness(tickDelta);

        vlplus$outlineVertex(vertexConsumer, q, ix, iy, iz, size, 1, -1, maxU, maxV, light);
        vlplus$outlineVertex(vertexConsumer, q, ix, iy, iz, size, 1, 1, maxU, minV, light);
        vlplus$outlineVertex(vertexConsumer, q, ix, iy, iz, size, -1, 1, minU, minV, light);
        vlplus$outlineVertex(vertexConsumer, q, ix, iy, iz, size, -1, -1, minU, maxV, light);
    }

    @Unique
    private void vlplus$outlineVertex(VertexConsumer vertexConsumer, Quaternionf q, float ix, float iy, float iz, float size, float localX, float localY, float u, float v, int light) {
        Vector3f pos = new Vector3f(localX, localY, 0f).rotate(q).mul(size).add(ix, iy, iz);

        vertexConsumer.vertex(pos.x(), pos.y(), pos.z())
            .texture(u, v)
            .color(vlplus$outlineR, vlplus$outlineG, vlplus$outlineB, vlplus$outlineA)
            .light(light);
    }
}
