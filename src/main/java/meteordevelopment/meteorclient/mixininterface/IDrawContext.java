/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixininterface;

import net.minecraft.client.texture.Sprite;

/**
 * Lets InGameHudMixin call the colored-sprite draw helpers DrawContextMixin adds to DrawContext -
 * casting straight to a mixin class from a DIFFERENT mixin doesn't work (Mixin's transformer can't
 * resolve it, since it's not actually a real type in the target class's hierarchy at transform
 * time); casting to a plain interface the mixin class declares itself as implementing does.
 */
public interface IDrawContext {
    void meteor$vlPlusDrawColoredSprite(Sprite sprite, int x, int y, int z, int width, int height, float red, float green, float blue, float alpha);

    void meteor$vlPlusDrawColoredFrameSprite(Sprite sprite, int fullWidth, int fullHeight, int frameX, int frameY, int x, int y, int z, int width, int height, float red, float green, float blue, float alpha);
}
