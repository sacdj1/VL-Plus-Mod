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
import java.util.function.Consumer;

public class ColorRangeListSetting extends Setting<List<ColorRangeEntry>> {
    public ColorRangeListSetting(String name, String description, List<ColorRangeEntry> defaultValue, Consumer<List<ColorRangeEntry>> onChanged, Consumer<Setting<List<ColorRangeEntry>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected List<ColorRangeEntry> parseImpl(String str) {
        return new ArrayList<>();
    }

    @Override
    protected boolean isValueValid(List<ColorRangeEntry> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList list = new NbtList();
        for (ColorRangeEntry entry : get()) list.add(entry.toTag());

        tag.put("value", list);

        return tag;
    }

    @Override
    protected List<ColorRangeEntry> load(NbtCompound tag) {
        get().clear();

        for (NbtElement e : tag.getList("value", NbtElement.COMPOUND_TYPE)) {
            get().add(ColorRangeEntry.fromTag((NbtCompound) e));
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<ColorRangeEntry>, ColorRangeListSetting> {
        public Builder() {
            super(new ArrayList<>(0));
        }

        @Override
        public ColorRangeListSetting build() {
            return new ColorRangeListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
