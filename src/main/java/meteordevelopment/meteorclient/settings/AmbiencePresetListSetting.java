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

public class AmbiencePresetListSetting extends Setting<List<AmbiencePreset>> {
    public AmbiencePresetListSetting(String name, String description, List<AmbiencePreset> defaultValue, Consumer<List<AmbiencePreset>> onChanged, Consumer<Setting<List<AmbiencePreset>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected List<AmbiencePreset> parseImpl(String str) {
        return new ArrayList<>();
    }

    @Override
    protected boolean isValueValid(List<AmbiencePreset> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList list = new NbtList();
        for (AmbiencePreset preset : get()) list.add(preset.toTag());

        tag.put("value", list);

        return tag;
    }

    @Override
    protected List<AmbiencePreset> load(NbtCompound tag) {
        get().clear();

        for (NbtElement e : tag.getList("value", NbtElement.COMPOUND_TYPE)) {
            get().add(AmbiencePreset.fromTag((NbtCompound) e));
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<AmbiencePreset>, AmbiencePresetListSetting> {
        public Builder() {
            super(new ArrayList<>(0));
        }

        @Override
        public AmbiencePresetListSetting build() {
            return new AmbiencePresetListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
