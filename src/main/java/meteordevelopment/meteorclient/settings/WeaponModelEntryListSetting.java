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

public class WeaponModelEntryListSetting extends Setting<List<WeaponModelEntry>> {
    public WeaponModelEntryListSetting(String name, String description, List<WeaponModelEntry> defaultValue, Consumer<List<WeaponModelEntry>> onChanged, Consumer<Setting<List<WeaponModelEntry>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected List<WeaponModelEntry> parseImpl(String str) {
        return new ArrayList<>();
    }

    @Override
    protected boolean isValueValid(List<WeaponModelEntry> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList list = new NbtList();
        for (WeaponModelEntry entry : get()) list.add(entry.toTag());

        tag.put("value", list);

        return tag;
    }

    @Override
    protected List<WeaponModelEntry> load(NbtCompound tag) {
        get().clear();

        for (NbtElement e : tag.getList("value", NbtElement.COMPOUND_TYPE)) {
            get().add(WeaponModelEntry.fromTag((NbtCompound) e));
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<WeaponModelEntry>, WeaponModelEntryListSetting> {
        public Builder() {
            super(new ArrayList<>(0));
        }

        @Override
        public WeaponModelEntryListSetting build() {
            return new WeaponModelEntryListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
