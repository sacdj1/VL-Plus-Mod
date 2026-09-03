/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.render;

import meteordevelopment.meteorclient.renderer.DrawMode;
import meteordevelopment.meteorclient.renderer.GL;
import meteordevelopment.meteorclient.renderer.Mesh;
import meteordevelopment.meteorclient.renderer.ShaderMesh;
import meteordevelopment.meteorclient.renderer.Shaders;
import meteordevelopment.meteorclient.renderer.text.CustomTextRenderer;
import meteordevelopment.meteorclient.renderer.text.Font;
import meteordevelopment.meteorclient.renderer.text.FontFace;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders text in a specific, per-caller {@link FontFace} - independent of the single global
 * custom font (Config's Custom Font / {@link meteordevelopment.meteorclient.renderer.Fonts#RENDERER}),
 * which every other Meteor HUD element shares through {@link meteordevelopment.meteorclient.systems.hud.HudRenderer}.
 * Immediate-mode (builds and flushes its own mesh per call) rather than batched across the frame
 * like HudRenderer's own text() - that keeps this usable both from a HudElement's render() and
 * from inside a mixin injection mid vanilla-HUD-render, where there's no shared begin/end batch to
 * hook into.
 */
public class CustomFontRenderer {
    private static final double SCALE_TO_HEIGHT = 1.0 / 18.0;
    private static final Map<Key, Font> FONTS = new HashMap<>();

    private record Key(FontFace face, int height) {
    }

    public static double render(FontFace face, String text, double x, double y, Color color, double scale, boolean shadow) {
        Font font = get(face, scale);
        Mesh mesh = new ShaderMesh(Shaders.TEXT, DrawMode.Triangles, Mesh.Attrib.Vec2, Mesh.Attrib.Vec2, Mesh.Attrib.Color);
        mesh.begin();

        double width;

        if (shadow) {
            int preShadowA = CustomTextRenderer.SHADOW_COLOR.a;
            CustomTextRenderer.SHADOW_COLOR.a = (int) (color.a / 255.0 * preShadowA);

            width = font.render(mesh, text, x + 1, y + 1, CustomTextRenderer.SHADOW_COLOR, scale);
            font.render(mesh, text, x, y, color, scale);

            CustomTextRenderer.SHADOW_COLOR.a = preShadowA;
        }
        else {
            width = font.render(mesh, text, x, y, color, scale);
        }

        GL.bindTexture(font.texture.getGlId());
        mesh.render(null);

        return width;
    }

    public static double width(FontFace face, String text, double scale, boolean shadow) {
        if (text.isEmpty()) return 0;

        double width = get(face, scale).getWidth(text, text.length());
        return (width + (shadow ? 1 : 0)) * scale + (shadow ? 1 : 0);
    }

    public static double height(FontFace face, double scale, boolean shadow) {
        double height = get(face, scale).getHeight() + 1;
        return (height + (shadow ? 1 : 0)) * scale;
    }

    private static Font get(FontFace face, double scale) {
        int height = (int) Math.round(scale / SCALE_TO_HEIGHT);
        return FONTS.computeIfAbsent(new Key(face, height), key -> loadFont(key.face(), key.height()));
    }

    private static Font loadFont(FontFace face, int height) {
        byte[] data = Utils.readBytes(face.toStream());
        ByteBuffer buffer = BufferUtils.createByteBuffer(data.length).put(data).flip();

        return new Font(buffer, height);
    }
}
