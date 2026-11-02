/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.render;

/**
 * Implemented by BillboardParticleMixin - stashes a 1px-ish outline color (the inverse of
 * ParticleRecolor.Mode.OverrideOutline's own tint) so buildGeometry() can draw an enlarged,
 * differently-colored duplicate quad behind the particle's own, read as a border where it pokes out
 * past the real (smaller) quad on top of it.
 *
 * Deliberately NOT in the mixin package - see VLRecolorableParticle's own doc comment for why.
 */
public interface VLOutlineParticle {
    void vlplus$setOutline(float r, float g, float b, float a);

    void vlplus$clearOutline();
}
