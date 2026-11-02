/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.texture.Sprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteBillboardParticle.class)
public interface SpriteBillboardParticleAccessor {
    @Accessor("sprite")
    void setSpriteDirect(Sprite sprite);

    @Accessor("sprite")
    Sprite getSprite();
}
