/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

public class WeightedColorEntry {
    public SettingColor color;
    public int weight;
    public BlendMode blend = BlendMode.SameBlock;
    public Mode mode = Mode.Replace;

    public WeightedColorEntry(SettingColor color, int weight) {
        this(color, weight, BlendMode.SameBlock, Mode.Replace);
    }

    public WeightedColorEntry(SettingColor color, int weight, BlendMode blend) {
        this(color, weight, blend, Mode.Replace);
    }

    public WeightedColorEntry(SettingColor color, int weight, BlendMode blend, Mode mode) {
        this.color = color;
        this.weight = weight;
        this.blend = blend;
        this.mode = mode;
    }

    public NbtCompound toTag() {
        NbtCompound tag = color.toTag();
        tag.putInt("weight", weight);
        tag.putString("blend", blend.name());
        tag.putString("mode", mode.name());

        return tag;
    }

    public static WeightedColorEntry fromTag(NbtCompound tag) {
        SettingColor color = new SettingColor().fromTag(tag);
        int weight = tag.contains("weight") ? tag.getInt("weight") : 100;

        BlendMode blend = BlendMode.SameBlock;
        if (tag.contains("blend", NbtElement.STRING_TYPE)) {
            try {
                blend = BlendMode.valueOf(tag.getString("blend"));
            } catch (IllegalArgumentException ignored) {
            }
        }

        Mode mode = Mode.Replace;
        if (tag.contains("mode", NbtElement.STRING_TYPE)) {
            try {
                mode = Mode.valueOf(tag.getString("mode"));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return new WeightedColorEntry(color, weight, blend, mode);
    }

    public enum BlendMode {
        // Picked once per block, no blending at all - reads as a crisp, distinct block.
        None,
        // Smoothly blends with neighbouring biomes/regions, but only where the same block type is present.
        SameBlock,
        // Smoothly blends across biome/region AND block-type borders (e.g. into an adjacent, differently-configured block).
        AnyBlock
    }

    public enum Mode {
        // The picked color IS the tint, full stop - still gets multiplied against the block's own
        // texture during actual rendering (same as vanilla biome tinting always has), so a block
        // with a strongly-colored (non-grayscale) texture of its own pulls the result away from
        // the color you actually picked, the more "textured" that block's own real art is.
        Replace,
        // Corrects for the block's own average texture color first (same technique Particle
        // Color's own Colorize mode uses, see its class doc) so the result still lands close to
        // the picked color regardless of what the block's real texture looks like, instead of
        // drifting toward it. Only ever darkens relative to Replace at the same alpha - a GPU
        // multiply can't brighten past the texture's own max.
        Colorize
    }
}
