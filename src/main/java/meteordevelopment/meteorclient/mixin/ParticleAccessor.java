/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Particle.class)
public interface ParticleAccessor {
    @Invoker("setAlpha")
    void invokeSetAlpha(float alpha);

    @Accessor("red")
    float getRed();

    @Accessor("green")
    float getGreen();

    @Accessor("blue")
    float getBlue();

    @Accessor("alpha")
    float getAlpha();
}
