/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.render;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Same technique XPBarAdjust's own Colorize mode uses, generalized to any sprite identifier -
 * tinting an already-colored texture (multiplicatively, whether via a shader uniform or a vertex
 * color) can only ever darken toward the tint, never reach hues the original texture doesn't have
 * (a mostly-red heart texture times yellow just stays reddish, since there's no green to
 * preserve). Reading the sprite once, normalizing it to grayscale (brightest pixel -> 255, keeping
 * relative shading/shape), and registering that as its own texture lets a tint multiply reach its
 * true target color at full strength while still keeping the original texture's shape/shading/
 * border, instead of flattening into a solid block.
 *
 * Cached per source sprite identifier - generated once, reused for the rest of the session (holds
 * as long as the resource pack doesn't change mid-session; not invalidated on pack reload since
 * that's a rare, deliberate action and this is a small, easily-reproduced cache either way).
 */
public class GrayscaleSpriteCache {
    private static final Map<Identifier, Identifier> cache = new HashMap<>();

    private GrayscaleSpriteCache() {
    }

    /** Returns a grayscale-normalized copy of the given sprite's texture, generating and registering it on first use. Falls back to the original identifier if the source can't be read. */
    public static Identifier get(Identifier spriteId) {
        return cache.computeIfAbsent(spriteId, GrayscaleSpriteCache::generate);
    }

    private static Identifier generate(Identifier spriteId) {
        Identifier resourceId = Identifier.of(spriteId.getNamespace(), "textures/gui/sprites/" + spriteId.getPath() + ".png");
        MinecraftClient mc = MinecraftClient.getInstance();

        try (InputStream input = mc.getResourceManager().open(resourceId)) {
            NativeImage original = NativeImage.read(input);
            NativeImage gray = toGrayscale(original);
            original.close();

            Identifier id = Identifier.of(MeteorClient.MOD_ID, "gray/" + spriteId.getNamespace() + "/" + spriteId.getPath().replace('/', '_'));
            mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(gray));
            return id;
        } catch (IOException e) {
            return spriteId;
        }
    }

    private static NativeImage toGrayscale(NativeImage src) {
        int w = src.getWidth(), h = src.getHeight();

        int maxLum = 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = src.getColor(x, y);
                if (ColorHelper.Abgr.getAlpha(pixel) == 0) continue;

                int lum = luminance(pixel);
                if (lum > maxLum) maxLum = lum;
            }
        }

        float scale = 255f / maxLum;

        return src.applyToCopy(pixel -> {
            int a = ColorHelper.Abgr.getAlpha(pixel);
            int v = Math.min(255, Math.round(luminance(pixel) * scale));

            return ColorHelper.Abgr.getAbgr(a, v, v, v);
        });
    }

    private static int luminance(int abgrPixel) {
        return Math.round(0.299f * ColorHelper.Abgr.getRed(abgrPixel) + 0.587f * ColorHelper.Abgr.getGreen(abgrPixel) + 0.114f * ColorHelper.Abgr.getBlue(abgrPixel));
    }
}
