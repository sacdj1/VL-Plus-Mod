/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

/**
 * A named area for Discord Presence's zone.current - one or more X/Z boxes (any Y, any dimension)
 * sharing a single name, same reasoning as AmbienceRegion letting you build an irregular/split-up
 * shape out of several simple pieces (a spawn platform plus a separate nearby island, say) instead
 * of being stuck with exactly one rectangle per name. Deliberately simpler than AmbienceRegion
 * otherwise (no Y range, no dimension, no block colors, no point/cylinder modes) - this is purely
 * "what's this box (or set of boxes) of the map called", nothing else.
 */
public class NamedArea {
    public String name = "New Area";
    public final List<Box> boxes = new ArrayList<>();
    // Separate from nothing else here (there's no per-area enable toggle) - lets "Visualize Zones"
    // show only a chosen subset of your areas, same as AmbienceRegion.visualize.
    public boolean visualize = true;

    public static class Box {
        public int x1, z1, x2, z2;

        public Box() {
        }

        public Box(int x1, int z1, int x2, int z2) {
            this.x1 = x1;
            this.z1 = z1;
            this.x2 = x2;
            this.z2 = z2;
        }

        public boolean contains(int x, int z) {
            return x >= Math.min(x1, x2) && x <= Math.max(x1, x2) && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
        }

        public NbtCompound toTag() {
            NbtCompound tag = new NbtCompound();

            tag.putInt("x1", x1);
            tag.putInt("z1", z1);
            tag.putInt("x2", x2);
            tag.putInt("z2", z2);

            return tag;
        }

        public static Box fromTag(NbtCompound tag) {
            Box box = new Box();

            box.x1 = tag.getInt("x1");
            box.z1 = tag.getInt("z1");
            box.x2 = tag.getInt("x2");
            box.z2 = tag.getInt("z2");

            return box;
        }
    }

    public boolean contains(int x, int z) {
        for (Box box : boxes) {
            if (box.contains(x, z)) return true;
        }

        return false;
    }

    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("name", name);
        tag.putBoolean("visualize", visualize);

        NbtList boxesTag = new NbtList();
        for (Box box : boxes) boxesTag.add(box.toTag());
        tag.put("boxes", boxesTag);

        return tag;
    }

    public static NamedArea fromTag(NbtCompound tag) {
        NamedArea area = new NamedArea();

        area.name = tag.getString("name");
        area.visualize = !tag.contains("visualize") || tag.getBoolean("visualize");

        if (tag.contains("boxes", NbtElement.LIST_TYPE)) {
            NbtList boxesTag = tag.getList("boxes", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < boxesTag.size(); i++) area.boxes.add(Box.fromTag(boxesTag.getCompound(i)));
        }
        // Pre-multi-box format: a single flat x1/z1/x2/z2 directly on the area itself - migrate it
        // into a one-entry box list instead of losing it.
        else if (tag.contains("x1")) {
            area.boxes.add(new Box(tag.getInt("x1"), tag.getInt("z1"), tag.getInt("x2"), tag.getInt("z2")));
        }

        return area;
    }
}
