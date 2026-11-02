/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import net.minecraft.nbt.NbtCompound;

/**
 * A named, coordinate-bounded area (X/Z box, any Y, any dimension) - used by Discord Presence's
 * zone.current to show a human-readable area name instead of raw coordinates. Deliberately simpler
 * than AmbienceRegion (no Y range, no dimension, no block colors, no point/cylinder modes) - this
 * is purely "what's this box of the map called", nothing else.
 */
public class NamedArea {
    public String name = "New Area";
    public int x1, z1, x2, z2;

    public boolean contains(int x, int z) {
        return x >= Math.min(x1, x2) && x <= Math.max(x1, x2) && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
    }

    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("name", name);
        tag.putInt("x1", x1);
        tag.putInt("z1", z1);
        tag.putInt("x2", x2);
        tag.putInt("z2", z2);

        return tag;
    }

    public static NamedArea fromTag(NbtCompound tag) {
        NamedArea area = new NamedArea();

        area.name = tag.getString("name");
        area.x1 = tag.getInt("x1");
        area.z1 = tag.getInt("z1");
        area.x2 = tag.getInt("x2");
        area.z2 = tag.getInt("z2");

        return area;
    }
}
