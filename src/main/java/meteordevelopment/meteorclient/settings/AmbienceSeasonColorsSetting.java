/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import net.minecraft.nbt.NbtCompound;

import java.util.function.Consumer;

/** A single season's fixed {@link AmbienceSeasonColors} slot. */
public class AmbienceSeasonColorsSetting extends Setting<AmbienceSeasonColors> {
    public AmbienceSeasonColorsSetting(String name, String description, AmbienceSeasonColors defaultValue, Consumer<AmbienceSeasonColors> onChanged, Consumer<Setting<AmbienceSeasonColors>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected AmbienceSeasonColors parseImpl(String str) {
        return null;
    }

    @Override
    protected boolean isValueValid(AmbienceSeasonColors value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new AmbienceSeasonColors();
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        tag.put("value", get().toTag());

        return tag;
    }

    @Override
    protected AmbienceSeasonColors load(NbtCompound tag) {
        if (tag.contains("value")) value = AmbienceSeasonColors.fromTag(tag.getCompound("value"));

        return value;
    }

    public static class Builder extends SettingBuilder<Builder, AmbienceSeasonColors, AmbienceSeasonColorsSetting> {
        public Builder() {
            super(new AmbienceSeasonColors());
        }

        @Override
        public AmbienceSeasonColorsSetting build() {
            return new AmbienceSeasonColorsSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
