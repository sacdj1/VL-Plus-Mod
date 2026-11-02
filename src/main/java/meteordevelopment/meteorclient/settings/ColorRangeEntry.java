/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.NbtCompound;

import java.util.Comparator;
import java.util.List;

/**
 * One band of a "By Value Ranges" color mode - this color applies to any value at or below
 * {@link #upTo}, picked by scanning every entry in ascending {@link #upTo} order and taking the
 * first one that fits. A value above every entry's upTo just uses the highest (last) entry's color,
 * so there's always a color for the open-ended top end without needing its own infinity entry.
 */
public class ColorRangeEntry {
    public double upTo;
    public SettingColor color;

    public ColorRangeEntry(double upTo, SettingColor color) {
        this.upTo = upTo;
        this.color = color;
    }

    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putDouble("upTo", upTo);
        tag.put("color", color.toTag());

        return tag;
    }

    public static ColorRangeEntry fromTag(NbtCompound tag) {
        return new ColorRangeEntry(tag.getDouble("upTo"), new SettingColor().fromTag(tag.getCompound("color")));
    }

    /** Picks the color for a given value from an (unsorted) list of ranges, or fallback if the list is empty. */
    public static Color resolve(List<ColorRangeEntry> ranges, double value, Color fallback) {
        if (ranges.isEmpty()) return fallback;

        ColorRangeEntry best = null;
        ColorRangeEntry highest = ranges.get(0);

        for (ColorRangeEntry entry : ranges) {
            if (entry.upTo > highest.upTo) highest = entry;
            if (value <= entry.upTo && (best == null || entry.upTo < best.upTo)) best = entry;
        }

        return (best != null ? best : highest).color;
    }

    public static List<ColorRangeEntry> sorted(List<ColorRangeEntry> ranges) {
        return ranges.stream().sorted(Comparator.comparingDouble(e -> e.upTo)).toList();
    }
}
