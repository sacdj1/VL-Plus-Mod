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

public class PingEntryListSetting extends Setting<List<PingEntry>> {
    public PingEntryListSetting(String name, String description, List<PingEntry> defaultValue, Consumer<List<PingEntry>> onChanged, Consumer<Setting<List<PingEntry>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected List<PingEntry> parseImpl(String str) {
        return new ArrayList<>();
    }

    @Override
    protected boolean isValueValid(List<PingEntry> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList list = new NbtList();
        for (PingEntry entry : get()) list.add(entry.toTag());

        tag.put("value", list);

        return tag;
    }

    @Override
    protected List<PingEntry> load(NbtCompound tag) {
        get().clear();

        for (NbtElement e : tag.getList("value", NbtElement.COMPOUND_TYPE)) {
            get().add(PingEntry.fromTag((NbtCompound) e));
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<PingEntry>, PingEntryListSetting> {
        public Builder() {
            super(new ArrayList<>(0));
        }

        @Override
        public PingEntryListSetting build() {
            return new PingEntryListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
