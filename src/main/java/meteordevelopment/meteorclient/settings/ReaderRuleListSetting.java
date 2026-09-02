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

public class ReaderRuleListSetting extends Setting<List<ReaderRule>> {
    public ReaderRuleListSetting(String name, String description, List<ReaderRule> defaultValue, Consumer<List<ReaderRule>> onChanged, Consumer<Setting<List<ReaderRule>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected List<ReaderRule> parseImpl(String str) {
        return new ArrayList<>();
    }

    @Override
    protected boolean isValueValid(List<ReaderRule> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList list = new NbtList();
        for (ReaderRule rule : get()) list.add(rule.toTag());

        tag.put("value", list);

        return tag;
    }

    @Override
    protected List<ReaderRule> load(NbtCompound tag) {
        get().clear();

        for (NbtElement e : tag.getList("value", NbtElement.COMPOUND_TYPE)) {
            get().add(ReaderRule.fromTag((NbtCompound) e));
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<ReaderRule>, ReaderRuleListSetting> {
        public Builder() {
            super(new ArrayList<>(0));
        }

        @Override
        public ReaderRuleListSetting build() {
            return new ReaderRuleListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
