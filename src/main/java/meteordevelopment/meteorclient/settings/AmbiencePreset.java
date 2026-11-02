/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import net.minecraft.nbt.NbtCompound;

/**
 * A named snapshot of Ambience's own override settings (sky/world colors, per-biome colors,
 * regions, global block colors) - captured by calling each relevant {@link Setting#toTag()} and
 * keying the result by that setting's own name, so restoring later just walks the same settings
 * and calls {@link Setting#fromTag(NbtCompound)} on whichever of them a tag exists for. Tolerant
 * of the exact setting list changing between versions - an entry with no matching tag (or a tag
 * with no matching setting) is just skipped.
 */
public class AmbiencePreset {
    public String name = "Preset";
    public final NbtCompound data;

    public AmbiencePreset() {
        this.data = new NbtCompound();
    }

    public AmbiencePreset(String name, NbtCompound data) {
        this.name = name;
        this.data = data;
    }

    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("name", name);
        tag.put("data", data);

        return tag;
    }

    public static AmbiencePreset fromTag(NbtCompound tag) {
        return new AmbiencePreset(tag.getString("name"), tag.getCompound("data"));
    }
}
