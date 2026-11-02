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

public class NamedAreaListSetting extends Setting<List<NamedArea>> {
    public NamedAreaListSetting(String name, String description, List<NamedArea> defaultValue, Consumer<List<NamedArea>> onChanged, Consumer<Setting<List<NamedArea>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected List<NamedArea> parseImpl(String str) {
        return new ArrayList<>();
    }

    @Override
    protected boolean isValueValid(List<NamedArea> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList list = new NbtList();
        for (NamedArea area : get()) list.add(area.toTag());

        tag.put("value", list);

        return tag;
    }

    @Override
    protected List<NamedArea> load(NbtCompound tag) {
        get().clear();

        for (NbtElement e : tag.getList("value", NbtElement.COMPOUND_TYPE)) {
            get().add(NamedArea.fromTag((NbtCompound) e));
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<NamedArea>, NamedAreaListSetting> {
        public Builder() {
            super(new ArrayList<>(0));
        }

        @Override
        public NamedAreaListSetting build() {
            return new NamedAreaListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
