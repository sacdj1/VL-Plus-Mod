/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.render;

import meteordevelopment.meteorclient.mixin.SpriteContentsAccessor;
import meteordevelopment.meteorclient.systems.modules.misc.DebugLog;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opacity-weighted average color of a particle sprite's own pixels, cached per {@link Sprite}
 * instance (sprites are stitched once per resource pack load and reused, so this is a one-time
 * cost per sprite actually recolored, not a per-particle one). Used by {@link
 * meteordevelopment.meteorclient.settings.ParticleRecolor.Mode#Colorize} to land on the picked
 * color without discarding the particle's own real texture - see the mode's own doc comment for
 * why a plain tint multiply can't do that on its own.
 */
public class SpriteAverageColor {
    private static final Map<Sprite, float[]> CACHE = new ConcurrentHashMap<>();

    // Each channel never goes below this - a sprite that's almost entirely one primary color (e.g.
    // a pure-red texture, green/blue near 0) would otherwise produce a near-infinite correction
    // factor for its other channels once divided into downstream, blowing out to solid white there
    // instead of a readable tint.
    private static final float MIN_CHANNEL = 0.12f;

    public static float[] get(Sprite sprite) {
        return CACHE.computeIfAbsent(sprite, SpriteAverageColor::compute);
    }

    private static float[] compute(Sprite sprite) {
        NativeImage image;
        try {
            image = ((SpriteContentsAccessor) sprite.getContents()).getImage();
        } catch (Exception e) {
            logDiagnostic(sprite, "accessor threw " + e, null, 0, 0);
            return new float[]{1f, 1f, 1f};
        }

        if (image == null) {
            logDiagnostic(sprite, "image was null", null, 0, 0);
            return new float[]{1f, 1f, 1f};
        }

        int w = image.getWidth(), h = image.getHeight();
        long r = 0, g = 0, b = 0, weight = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = image.getOpacity(x, y) & 0xFF;
                if (a < 8) continue;

                r += (image.getRed(x, y) & 0xFF) * a;
                g += (image.getGreen(x, y) & 0xFF) * a;
                b += (image.getBlue(x, y) & 0xFF) * a;
                weight += a;
            }
        }

        if (weight == 0) {
            logDiagnostic(sprite, "every pixel under opacity threshold (all transparent, or all read as 0)", null, w, h);
            return new float[]{1f, 1f, 1f};
        }

        float[] result = {
            Math.max(MIN_CHANNEL, (r / (float) weight) / 255f),
            Math.max(MIN_CHANNEL, (g / (float) weight) / 255f),
            Math.max(MIN_CHANNEL, (b / (float) weight) / 255f)
        };

        logDiagnostic(sprite, "ok", result, w, h);
        return result;
    }

    // Fires at most once per unique Sprite instance ever recolored (this method is only ever
    // reached via CACHE.computeIfAbsent), so it's safe to leave gated behind DebugLog instead of
    // needing its own separate rate limit - this is nowhere near a per-tick/per-particle path.
    private static void logDiagnostic(Sprite sprite, String outcome, float[] result, int w, int h) {
        if (!DebugLog.particleRecolorEnabled()) return;

        String resultStr = result == null ? "n/a" : String.format("%.3f,%.3f,%.3f", result[0], result[1], result[2]);
        DebugLog.log("SpriteAverageColor %s (%dx%d) -> %s [%s]", sprite.getContents().getId(), w, h, resultStr, outcome);
    }
}
