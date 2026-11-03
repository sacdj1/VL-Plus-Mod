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
import meteordevelopment.meteorclient.utils.render.SpriteAverageColor;
import meteordevelopment.meteorclient.utils.render.VLLightOverrideParticle;
import meteordevelopment.meteorclient.utils.render.VLOutlineParticle;
import meteordevelopment.meteorclient.utils.render.VLRecolorableParticle;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.settings.ParticleRecolor;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.texture.Sprite;
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

        if (particleColor.shouldPermClear(parameters)) {
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

            // Every mode now applies the picked color at full strength (previously the color's own
            // alpha silently scaled this down as a "blend strength" - 0 = untouched, 255 = fully
            // applied - independent of the actual output opacity. That's not how anyone reads an
            // alpha slider on a color picker, and any entry saved with anything under 255 alpha
            // (the common case - most color pickers don't default to fully opaque) came out barely
            // tinted or not visibly tinted at all, on every mode except Replace, which ignored alpha
            // entirely and so looked like the only mode that actually worked). The entry's alpha is
            // now just an opacity multiplier on the final particle, stacking with the separate
            // Overall Alpha setting below - matching what an alpha slider normally means.
            float tr = color.r / 255f, tg = color.g / 255f, tb = color.b / 255f;
            float or = accessor.getRed(), og = accessor.getGreen(), ob = accessor.getBlue();

            ParticleRecolor.Mode mode = particleColor.getRecolorMode(parameters);
            particleColor.noteRecolor(parameters, mode, color, particleColor.isGeneral(parameters));

            float r, g, b;
            switch (mode) {
                case Multiply -> { r = or * tr; g = og * tg; b = ob * tb; }
                // Additive can't visibly brighten a channel that's already near-maximum (most
                // particle textures/tints start close to white) - real limitation of the mode
                // itself, not something to work around here.
                case Add -> { r = Math.min(1f, or + tr); g = Math.min(1f, og + tg); b = Math.min(1f, ob + tb); }
                // Colorize: corrects for the sprite's own average texture color before tinting, so a
                // SATURATED pick (red, green, a specific brand color, ...) centers on that color
                // instead of just darkening toward whatever hue the texture already leans - but how
                // much correction to apply is scaled by the picked tint's OWN saturation, down to none
                // at all for a picked color that's gray/white. Without that scaling, picking white
                // still visibly changed the particle (boosting every channel that was below the
                // texture's own average back up toward 1.0, which is "correct" by the average-matches-
                // picked-color goal but not what anyone expects "white" to mean) - confirmed: picking
                // white was still altering particles, not leaving them alone. Zero saturation now means
                // zero correction, i.e. the tint is applied completely uncorrected (full strength,
                // same as Replace/Blend) - and tinting with flat white IS the identity multiply against
                // the real texture (texture * 1 = texture), so white now reproduces the particle's
                // real, untouched appearance exactly, same as turning Particle Color off for it. Fully
                // saturated tints get the full per-channel correction as before (see history below);
                // partially-saturated tints blend smoothly between the two.
                case Colorize -> {
                    float[] avg = particle instanceof SpriteBillboardParticle spriteParticle
                        ? SpriteAverageColor.get(((SpriteBillboardParticleAccessor) spriteParticle).getSprite())
                        : new float[]{1f, 1f, 1f};

                    // Per-channel correction, clamped independently rather than rescaled together -
                    // scaling all three down together by whichever one overshoots most is what caused
                    // the original version of this mode to send a saturated texture tinted pure white
                    // toward magenta instead of white (every channel needed boosting toward white, and
                    // the shared rescale dragged the ones needing LESS boost down along with the one
                    // needing the most) - independent clamping keeps each channel's own boost up to its
                    // own 1.0 ceiling instead. Moot for white specifically now (saturation 0 skips
                    // correction entirely, see below), but still matters for partially-saturated tints.
                    float correctedR = Math.min(1f, tr / avg[0]);
                    float correctedG = Math.min(1f, tg / avg[1]);
                    float correctedB = Math.min(1f, tb / avg[2]);

                    float maxC = Math.max(tr, Math.max(tg, tb));
                    float minC = Math.min(tr, Math.min(tg, tb));
                    float saturation = maxC <= 0f ? 0f : (maxC - minC) / maxC;

                    r = tr + (correctedR - tr) * saturation;
                    g = tg + (correctedG - tg) * saturation;
                    b = tb + (correctedB - tb) * saturation;
                }
                // Override(Outline): swaps in a neutral white sprite (so there's no texture of its own
                // left to fight the tint) and tints from there, landing on the picked color exactly
                // regardless of what the original texture looked like - the only mode that can
                // actually make a picked color (including white) visibly readable on a non-white
                // particle, since every OTHER mode keeps the real texture and a vertex-color multiply
                // can only ever darken it, never brighten past what's already there (confirmed: white
                // is the brightest possible multiply, i.e. exactly the identity - it reproduces the
                // original texture unchanged, on any mode that doesn't discard the texture).
                //
                // The swap-to-neutral trick only works when the particle's sprite is on the SAME
                // texture sheet as the neutral sprite (the block atlas, since WHITE_CONCRETE's model
                // particle sprite lives there) - used to gate this on the SPAWNING ParticleEffect's own
                // type (BlockStateParticleEffect/ItemStackParticleEffect), which is a proxy for "this
                // probably renders via the block atlas" rather than the real thing. Checking the
                // particle's actual ParticleTextureSheet (TERRAIN_SHEET = block atlas) directly is both
                // more accurate and broader - still false (correctly) for genuinely particle-atlas-
                // sheet particles (most environmental ones - smoke, flame, portal, soul, etc), which
                // have no safe neutral sprite to swap to in this codebase today (the particle atlas
                // doesn't have one registered the way the block atlas has WHITE_CONCRETE) - those fall
                // through to the plain tint below, same ceiling as every texture-preserving mode.
                // HueShift: same Colorize correction as above, but the target color continuously
                // swings its hue over time (this entry's own color is the swing's starting point,
                // its own Hue Shift Speed/Range settings pick how it moves) instead of staying
                // fixed - same shared math ItemInfo's own Enchant/Attachment Hue Shift color mode
                // uses. Subject to the exact same correction/saturation behavior as Colorize right
                // above (including: a gray/white starting color has no hue to shift, so it's still
                // a no-op on those, same reasoning as Colorize's own white-is-identity case).
                // A real hue shift rotates each PIXEL's own hue while keeping its own detail - a
                // vertex tint can't do that (same hard ceiling as true grayscale-then-colorize, see
                // Colorize's own history above). Earlier version of this ran the rotating color
                // through Colorize's average-divide correction anyway - but that correction runs at
                // FULL strength whenever the tint is saturated, which a rotating hue always is, so
                // every cycle through red/green/blue/cyan/magenta/yellow divided one or two whole
                // channels toward zero across the ENTIRE sprite (not per-pixel), which read as the
                // particle going black, repeatedly, not just a one-off glitch. Plain flat tint
                // instead - same as Replace/Blend already do for a fixed color, just animated - lets
                // the real texture multiply against it normally instead of fighting it.
                case HueShift -> {
                    ParticleRecolor recolor = particleColor.getRecolorEntry(parameters);
                    double speed = recolor != null ? recolor.hueShiftSpeed : 1.0;
                    double range = recolor != null ? recolor.hueShiftRange : 60.0;

                    float[] hsb = java.awt.Color.RGBtoHSB(Math.round(tr * 255), Math.round(tg * 255), Math.round(tb * 255), null);
                    double time = System.currentTimeMillis() / 1000.0 * speed;
                    double offset = Math.sin(time) * range;
                    float hue = (float) (((hsb[0] * 360.0 + offset) % 360.0 + 360.0) % 360.0);

                    int shifted = java.awt.Color.HSBtoRGB(hue / 360f, hsb[1], hsb[2]);
                    r = (shifted >> 16 & 0xFF) / 255f;
                    g = (shifted >> 8 & 0xFF) / 255f;
                    b = (shifted & 0xFF) / 255f;
                }
                case Override, OverrideOutline -> {
                    if (particle instanceof SpriteBillboardParticle && particle.getType() == ParticleTextureSheet.TERRAIN_SHEET) {
                        Sprite neutral = MinecraftClient.getInstance().getBlockRenderManager().getModels().getModelParticleSprite(Blocks.WHITE_CONCRETE.getDefaultState());
                        ((SpriteBillboardParticleAccessor) particle).setSpriteDirect(neutral);
                    }

                    r = tr; g = tg; b = tb;
                }
                default -> { r = tr; g = tg; b = tb; } // Blend / Replace
            }

            // Pushes the final color away from (or toward) mid-gray, every mode - mainly meant for
            // Colorize, where this is the only lever left to make the tint read stronger once the
            // sprite-average correction above has already done what it can; a flat multiply can't
            // brighten past the texture's own max, but THIS runs after that, on the already-resolved
            // final color, so it can still push it further from gray in either direction.
            float contrast = particleColor.getContrast();
            if (contrast != 1f) {
                r = Math.min(1f, Math.max(0f, 0.5f + (r - 0.5f) * contrast));
                g = Math.min(1f, Math.max(0f, 0.5f + (g - 0.5f) * contrast));
                b = Math.min(1f, Math.max(0f, 0.5f + (b - 0.5f) * contrast));
            }

            particle.setColor(r, g, b);

            if (mode == ParticleRecolor.Mode.OverrideOutline && particle instanceof VLOutlineParticle outlined) {
                outlined.vlplus$setOutline(1f - r, 1f - g, 1f - b, color.a / 255f);
            } else if (particle instanceof VLOutlineParticle outlined) {
                outlined.vlplus$clearOutline();
            }
            // Some particle types (DamageParticle/"redstone" hit particles included) animate their
            // own red/green/blue every tick and would otherwise silently undo the line above within
            // a few ticks - see DamageParticleMixin. Stash the target color so it gets reasserted
            // every tick, for every particle type that implements the hook (currently just that one;
            // more can opt in the same way if the same bug turns up elsewhere).
            if (particle instanceof VLRecolorableParticle recolorable) recolorable.vlplus$setRecolor(r, g, b);

            // Used to also multiply by the particle's OWN current alpha (accessor.getAlpha()), on
            // top of these two - the idea being a particle that's already semi-transparent for its
            // own reasons (a soft/faded sprite) keeps that relative transparency instead of getting
            // flattened to one flat opacity. In practice that's a THIRD independent dimming factor
            // stacking multiplicatively with Overall Alpha and this entry's own Alpha - for a
            // particle type whose own native alpha is already well under 1 (soft/translucent
            // particles, not full-opacity ones), the three multiplied together collapsed toward
            // zero, rendering as a barely-there, near-black smudge regardless of what color/alpha
            // was actually picked here - affecting semi-transparent particle types specifically,
            // never the fully-opaque ones. Alpha is now set directly from just these two settings,
            // same as every other channel here - full, direct control over the result instead of it
            // being at the mercy of whatever alpha the particle happened to spawn with.
            accessor.invokeSetAlpha(particleColor.getOverallAlpha() * (color.a / 255f));

            if (particleColor.overrideLighting() && particle instanceof VLLightOverrideParticle lit) {
                lit.vlplus$setLightOverride(particleColor.getPackedLightOverride());
            }
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
