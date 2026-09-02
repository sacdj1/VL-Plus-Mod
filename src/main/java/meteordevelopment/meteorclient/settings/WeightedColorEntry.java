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

    public WeightedColorEntry(SettingColor color, int weight) {
        this(color, weight, BlendMode.SameBlock);
    }

    public WeightedColorEntry(SettingColor color, int weight, BlendMode blend) {
        this.color = color;
        this.weight = weight;
        this.blend = blend;
    }

    public NbtCompound toTag() {
        NbtCompound tag = color.toTag();
        tag.putInt("weight", weight);
        tag.putString("blend", blend.name());

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

        return new WeightedColorEntry(color, weight, blend);
    }

    public enum BlendMode {
        // Picked once per block, no blending at all - reads as a crisp, distinct block.
        None,
        // Smoothly blends with neighbouring biomes/regions, but only where the same block type is present.
        SameBlock,
        // Smoothly blends across biome/region AND block-type borders (e.g. into an adjacent, differently-configured block).
        AnyBlock
    }
}
