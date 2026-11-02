/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.render;

/**
 * Implemented by particle-specific mixins (see DamageParticleMixin) whose vanilla tick() method
 * animates its own red/green/blue every tick, which would otherwise silently undo a one-time
 * Particle.setColor() call made right after spawning (see ParticleManagerMixin.onAddParticleColor).
 * Stashes the target color so it can be reasserted every tick, after vanilla's own animation runs.
 *
 * Deliberately NOT in the mixin package (meteordevelopment.meteorclient.mixin) despite being
 * implemented by a mixin - that package is reserved for @Mixin classes themselves, and Mixin refuses
 * to let anything else load a class from it directly. Other mods that walk a merged target class's
 * interfaces via reflection (observed: Figura's doc generator, scanning Particle's fields/interfaces
 * on startup) trigger exactly that load path and crash the whole game if this interface lived there.
 */
public interface VLRecolorableParticle {
    void vlplus$setRecolor(float r, float g, float b);
}
