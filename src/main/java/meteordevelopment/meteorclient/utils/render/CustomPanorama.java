/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.render;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.config.Config;
import net.minecraft.client.gui.CubeMapRenderer;
import net.minecraft.client.gui.RotatingCubeMapRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileInputStream;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Replaces the title screen's rotating panorama with 6 images the user drops into
 * .minecraft/vlplus-panoramas/ (panorama_0.png .. panorama_5.png - same 6 faces, same order, as
 * vanilla's own panorama_0..5). Falls back to vanilla automatically whenever those 6 files aren't
 * all present and valid - "none taken yet" and "the files got removed" are the same case (just
 * whatever's on disk right now), so there's nothing extra to track for either.
 *
 * No in-game capture tool yet - the 6 images have to come from somewhere else (an external
 * screenshot/panorama tool facing each direction) for now; capturing one from the client itself
 * (looking in the 6 cube directions and screenshotting each) is a separate, bigger feature.
 */
public class CustomPanorama {
    private static final String[] FACES = {"panorama_0.png", "panorama_1.png", "panorama_2.png", "panorama_3.png", "panorama_4.png", "panorama_5.png"};

    private static File folder;
    private static RotatingCubeMapRenderer cached;
    private static long cachedFilesSignature = Long.MIN_VALUE;

    private CustomPanorama() {
    }

    private static File folder() {
        if (folder == null) folder = new File(mc.runDirectory, "vlplus-panoramas"); // same base as vlplus-recordings
        return folder;
    }

    /** Sum of the 6 files' lastModified() (0 if any is missing) - cheap way to notice "the files on disk changed" without re-reading them every frame. */
    private static long signature() {
        File dir = folder();
        long sum = 0;

        for (String face : FACES) {
            File f = new File(dir, face);
            if (!f.isFile()) return 0;
            sum += f.lastModified();
        }

        return sum;
    }

    /** The renderer to use this frame, or null - null means "no valid custom panorama right now, draw vanilla's own instead". */
    public static RotatingCubeMapRenderer getRenderer() {
        Config config = Config.get();
        if (config == null || !config.customPanorama.get()) return null;

        long sig = signature();
        if (sig == 0) return null; // one or more files missing - vanilla fallback

        if (cached == null || sig != cachedFilesSignature) {
            cached = load();
            cachedFilesSignature = sig;
        }

        return cached;
    }

    private static RotatingCubeMapRenderer load() {
        File dir = folder();

        for (int i = 0; i < FACES.length; i++) {
            try (FileInputStream in = new FileInputStream(new File(dir, FACES[i]))) {
                NativeImage image = NativeImage.read(in);
                Identifier id = Identifier.of("vlplus", "custom_panorama/panorama_" + i);
                mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
            } catch (Exception e) {
                MeteorClient.LOG.warn("Failed to load custom panorama face {} - falling back to vanilla's own.", FACES[i], e);
                return null;
            }
        }

        // CubeMapRenderer builds each face's real path as "<base>_<index>.png" itself - "panorama"
        // (no trailing "_0" etc.) is the base every one of the 6 registered textures above share.
        CubeMapRenderer cubeMap = new CubeMapRenderer(Identifier.of("vlplus", "custom_panorama/panorama"));
        return new RotatingCubeMapRenderer(cubeMap);
    }
}
