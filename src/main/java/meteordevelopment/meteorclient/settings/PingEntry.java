/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

/**
 * One custom ping definition for the Pings module - this is the main way pings are made: pick a
 * name (for picking it in Action Wheel/Ping Wheel), optional text and/or symbol shown at the ping
 * location, and one or more colors. More than one color flashes/cycles between them (same idea as
 * the Flashing color mode elsewhere in this pack) at Flash Speed; a single color is just static.
 * Duration is this ping's own lifetime once fired, independent of every other entry's.
 */
public class PingEntry {
    public String name = "Ping";
    public String text = "";
    public String symbol = "";

    public List<SettingColor> colors = new ArrayList<>(List.of(new SettingColor(255, 255, 255)));
    public double flashSpeed = 2.0;
    public double duration = 5.0;

    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("name", name);
        tag.putString("text", text);
        tag.putString("symbol", symbol);
        tag.putDouble("flashSpeed", flashSpeed);
        tag.putDouble("duration", duration);

        NbtList colorsTag = new NbtList();
        for (SettingColor color : colors) colorsTag.add(color.toTag());
        tag.put("colors", colorsTag);

        return tag;
    }

    public static PingEntry fromTag(NbtCompound tag) {
        PingEntry entry = new PingEntry();

        entry.name = tag.getString("name");
        entry.text = tag.getString("text");
        entry.symbol = tag.getString("symbol");
        entry.flashSpeed = tag.contains("flashSpeed") ? tag.getDouble("flashSpeed") : 2.0;
        entry.duration = tag.contains("duration") ? tag.getDouble("duration") : 5.0;

        if (tag.contains("colors")) {
            entry.colors.clear();
            for (NbtElement e : tag.getList("colors", NbtElement.COMPOUND_TYPE)) {
                SettingColor color = new SettingColor();
                color.fromTag((NbtCompound) e);
                entry.colors.add(color);
            }
            if (entry.colors.isEmpty()) entry.colors.add(new SettingColor(255, 255, 255));
        }

        return entry;
    }
}
