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
import java.util.function.Consumer;

public class TimedColorListSetting extends Setting<List<TimedColorEntry>> {
    public TimedColorListSetting(String name, String description, List<TimedColorEntry> defaultValue, Consumer<List<TimedColorEntry>> onChanged, Consumer<Setting<List<TimedColorEntry>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected List<TimedColorEntry> parseImpl(String str) {
        return new ArrayList<>();
    }

    @Override
    protected boolean isValueValid(List<TimedColorEntry> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>(defaultValue.size());

        for (TimedColorEntry entry : defaultValue) {
            value.add(new TimedColorEntry(new SettingColor(entry.color), entry.ticks));
        }
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList list = new NbtList();
        for (TimedColorEntry entry : get()) list.add(entry.toTag());

        tag.put("value", list);

        return tag;
    }

    @Override
    protected List<TimedColorEntry> load(NbtCompound tag) {
        get().clear();

        for (NbtElement e : tag.getList("value", NbtElement.COMPOUND_TYPE)) {
            get().add(TimedColorEntry.fromTag((NbtCompound) e));
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<TimedColorEntry>, TimedColorListSetting> {
        public Builder() {
            super(new ArrayList<>());
        }

        @Override
        public TimedColorListSetting build() {
            return new TimedColorListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
