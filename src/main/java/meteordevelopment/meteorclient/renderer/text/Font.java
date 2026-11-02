/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.renderer.text;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.Mesh;
import meteordevelopment.meteorclient.utils.render.ByteTexture;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.texture.AbstractTexture;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.*;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class Font {
    public AbstractTexture texture;
    private final int height;
    private final float scale;
    private final float ascent;
    private final Int2ObjectOpenHashMap<CharData> charMap = new Int2ObjectOpenHashMap<>();
    // Atlas side length for stb_truetype's rect packer, shared across all 6 ranges packed below in
    // one call. At larger font heights, the combined area of ~720 glyphs can get close enough to
    // this budget that the packer fails for a handful of individual glyphs (their exact packed
    // rect just doesn't fit), which silently renders as an invisible/blank glyph that still advances
    // the cursor normally - looking exactly like a missing letter, not a crash or an obvious tofu
    // box. Which specific glyph(s) fail depends on the packer's internal sort order for that exact
    // set of dimensions, not simply "first/last processed" - so this can affect any character,
    // intermittently, as new setting/element names introduce new text at large sizes. Doubling the
    // atlas side length quadruples the available area, giving enough headroom that this shouldn't
    // happen in practice.
    private final static int size = 4096;

    public Font(ByteBuffer buffer, int height) {
        this.height = height;

        // Initialize font
        STBTTFontinfo fontInfo = STBTTFontinfo.create();
        STBTruetype.stbtt_InitFont(fontInfo, buffer);

        // Allocate buffers
        ByteBuffer bitmap = BufferUtils.createByteBuffer(size * size);
        STBTTPackedchar.Buffer[] cdata = {
            STBTTPackedchar.create(95), // Basic Latin
            STBTTPackedchar.create(96), // Latin 1 Supplement
            STBTTPackedchar.create(128), // Latin Extended-A
            STBTTPackedchar.create(144), // Greek and Coptic
            STBTTPackedchar.create(256), // Cyrillic
            STBTTPackedchar.create(1) // infinity symbol
        };

        // create and initialise packing context
        STBTTPackContext packContext = STBTTPackContext.create();
        STBTruetype.stbtt_PackBegin(packContext, bitmap, size, size, 0 ,1);

        // create the pack range, populate with the specific packing ranges
        STBTTPackRange.Buffer packRange = STBTTPackRange.create(cdata.length);
        packRange.put(STBTTPackRange.create().set(height, 32, null, 95, cdata[0], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 160, null, 96, cdata[1], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 256, null, 128, cdata[2], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 880, null, 144, cdata[3], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 1024, null, 256, cdata[4], (byte) 2, (byte) 2));
        packRange.put(STBTTPackRange.create().set(height, 8734, null, 1, cdata[5], (byte) 2, (byte) 2)); // lol
        packRange.flip();

        // write and finish - return value is whether EVERY glyph in EVERY range packed successfully;
        // a false here means at least one glyph didn't fit the atlas and will render as an invisible
        // blank space (see charMap fallback in getWidth/render below) instead of its real letter.
        // Logged rather than silently ignored so a recurrence is diagnosable from latest.log instead
        // of needing another round of screenshot-hunting for "some letter is missing again".
        boolean packedAll = STBTruetype.stbtt_PackFontRanges(packContext, buffer, 0, packRange);
        if (!packedAll) {
            MeteorClient.LOG.warn("Font atlas ({}px, {}x{}) failed to pack at least one glyph - it will render as blank space. Consider increasing Font.size.", height, size, size);
        }
        STBTruetype.stbtt_PackEnd(packContext);

        // Create texture object and get font scale
        texture = new ByteTexture(size, size, bitmap, ByteTexture.Format.A, ByteTexture.Filter.Linear, ByteTexture.Filter.Linear);
        scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, height);

        // Get font vertical ascent
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ascent = stack.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascent, null, null);
            this.ascent = ascent.get(0);
        }

        for (int i = 0; i < cdata.length; i++) {
            STBTTPackedchar.Buffer cbuf = cdata[i];
            int offset = packRange.get(i).first_unicode_codepoint_in_range();

            for (int j = 0; j < cbuf.capacity(); j++) {
                STBTTPackedchar packedChar = cbuf.get(j);

                float ipw = 1f / size; // pixel width and height
                float iph = 1f / size;

                charMap.put(j + offset, new CharData(
                    packedChar.xoff(),
                    packedChar.yoff(),
                    packedChar.xoff2(),
                    packedChar.yoff2(),
                    packedChar.x0() * ipw,
                    packedChar.y0() * iph,
                    packedChar.x1() * ipw,
                    packedChar.y1() * iph,
                    packedChar.xadvance()
                ));
            }
        }
    }

    public double getWidth(String string, int length) {
        double width = 0;

        for (int i = 0; i < length; i++) {
            int cp = string.charAt(i);
            CharData c = charMap.get(cp);
            if (c == null) c = charMap.get(32);

            width += c.xAdvance;
        }

        return width;
    }

    public int getHeight() {
        return height;
    }

    public double render(Mesh mesh, String string, double x, double y, Color color, double scale) {
        y += ascent * this.scale * scale;

        for (int i = 0; i < string.length(); i++) {
            int cp = string.charAt(i);
            CharData c = charMap.get(cp);
            if (c == null) c = charMap.get(32);

            mesh.quad(
                mesh.vec2(x + c.x0 * scale, y + c.y0 * scale).vec2(c.u0, c.v0).color(color).next(),
                mesh.vec2(x + c.x0 * scale, y + c.y1 * scale).vec2(c.u0, c.v1).color(color).next(),
                mesh.vec2(x + c.x1 * scale, y + c.y1 * scale).vec2(c.u1, c.v1).color(color).next(),
                mesh.vec2(x + c.x1 * scale, y + c.y0 * scale).vec2(c.u1, c.v0).color(color).next()
            );

            x += c.xAdvance * scale;
        }

        return x;
    }

    private record CharData(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, float xAdvance) {}
}
