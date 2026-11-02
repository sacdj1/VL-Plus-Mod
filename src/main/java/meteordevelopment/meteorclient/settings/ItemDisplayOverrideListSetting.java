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

public class ItemDisplayOverrideListSetting extends Setting<List<ItemDisplayOverride>> {
    public ItemDisplayOverrideListSetting(String name, String description, List<ItemDisplayOverride> defaultValue, Consumer<List<ItemDisplayOverride>> onChanged, Consumer<Setting<List<ItemDisplayOverride>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected List<ItemDisplayOverride> parseImpl(String str) {
        return new ArrayList<>();
    }

    @Override
    protected boolean isValueValid(List<ItemDisplayOverride> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList list = new NbtList();
        for (ItemDisplayOverride o : get()) list.add(o.toTag());

        tag.put("value", list);

        return tag;
    }

    @Override
    protected List<ItemDisplayOverride> load(NbtCompound tag) {
        get().clear();

        for (NbtElement e : tag.getList("value", NbtElement.COMPOUND_TYPE)) {
            get().add(ItemDisplayOverride.fromTag((NbtCompound) e));
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<ItemDisplayOverride>, ItemDisplayOverrideListSetting> {
        public Builder() {
            super(new ArrayList<>(0));
        }

        @Override
        public ItemDisplayOverrideListSetting build() {
            return new ItemDisplayOverrideListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
