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

public class AmbienceRegionListSetting extends Setting<List<AmbienceRegion>> {
    public AmbienceRegionListSetting(String name, String description, List<AmbienceRegion> defaultValue, Consumer<List<AmbienceRegion>> onChanged, Consumer<Setting<List<AmbienceRegion>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected List<AmbienceRegion> parseImpl(String str) {
        return new ArrayList<>();
    }

    @Override
    protected boolean isValueValid(List<AmbienceRegion> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList list = new NbtList();
        for (AmbienceRegion region : get()) list.add(region.toTag());

        tag.put("value", list);

        return tag;
    }

    @Override
    protected List<AmbienceRegion> load(NbtCompound tag) {
        get().clear();

        for (NbtElement e : tag.getList("value", NbtElement.COMPOUND_TYPE)) {
            get().add(AmbienceRegion.fromTag((NbtCompound) e));
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<AmbienceRegion>, AmbienceRegionListSetting> {
        public Builder() {
            super(new ArrayList<>(0));
        }

        @Override
        public AmbienceRegionListSetting build() {
            return new AmbienceRegionListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
