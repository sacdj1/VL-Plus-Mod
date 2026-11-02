/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.render;

/**
 * Implemented by ParticleMixin - lets ParticleColor force a particle to render at a fixed light
 * level instead of sampling the world at its position.
 *
 * Deliberately NOT in the mixin package - see VLRecolorableParticle's javadoc for why; same reason
 * applies here (Particle.class is the mixin target, and other mods' reflection over it can trigger
 * a direct load of this interface if it lived in the mixin-only package).
 */
public interface VLLightOverrideParticle {
    void vlplus$setLightOverride(int packedLight);
}
