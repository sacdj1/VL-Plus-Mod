/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.NbtCompound;

/** A color paired with how many game ticks it should be held for - used by Flashing color modes. */
public class TimedColorEntry {
    public SettingColor color;
    public int ticks;

    public TimedColorEntry(SettingColor color, int ticks) {
        this.color = color;
        this.ticks = ticks;
    }

    public NbtCompound toTag() {
        NbtCompound tag = color.toTag();
        tag.putInt("ticks", ticks);

        return tag;
    }

    public static TimedColorEntry fromTag(NbtCompound tag) {
        SettingColor color = new SettingColor().fromTag(tag);
        int ticks = tag.contains("ticks") ? tag.getInt("ticks") : 10;

        return new TimedColorEntry(color, ticks);
    }
}
