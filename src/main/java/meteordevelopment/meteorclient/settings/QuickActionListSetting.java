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

public class QuickActionListSetting extends Setting<List<QuickAction>> {
    public QuickActionListSetting(String name, String description, List<QuickAction> defaultValue, Consumer<List<QuickAction>> onChanged, Consumer<Setting<List<QuickAction>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected List<QuickAction> parseImpl(String str) {
        return new ArrayList<>();
    }

    @Override
    protected boolean isValueValid(List<QuickAction> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList list = new NbtList();
        for (QuickAction action : get()) list.add(action.toTag());

        tag.put("value", list);

        return tag;
    }

    @Override
    protected List<QuickAction> load(NbtCompound tag) {
        get().clear();

        for (NbtElement e : tag.getList("value", NbtElement.COMPOUND_TYPE)) {
            get().add(QuickAction.fromTag((NbtCompound) e));
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<QuickAction>, QuickActionListSetting> {
        public Builder() {
            super(new ArrayList<>(0));
        }

        @Override
        public QuickActionListSetting build() {
            return new QuickActionListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
