/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** A flat block -> colors map, not tied to any biome/region - used for Ambience's global block override list. */
public class BlockColorMapSetting extends Setting<Map<Block, List<WeightedColorEntry>>> {
    public BlockColorMapSetting(String name, String description, Map<Block, List<WeightedColorEntry>> defaultValue, Consumer<Map<Block, List<WeightedColorEntry>>> onChanged, Consumer<Setting<Map<Block, List<WeightedColorEntry>>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected Map<Block, List<WeightedColorEntry>> parseImpl(String str) {
        return new LinkedHashMap<>();
    }

    @Override
    protected boolean isValueValid(Map<Block, List<WeightedColorEntry>> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new LinkedHashMap<>();

        for (Map.Entry<Block, List<WeightedColorEntry>> entry : defaultValue.entrySet()) {
            value.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        tag.put("value", BlockColorMapNbt.toTag(get()));

        return tag;
    }

    @Override
    protected Map<Block, List<WeightedColorEntry>> load(NbtCompound tag) {
        value = BlockColorMapNbt.fromTag(tag.getCompound("value"));

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, Map<Block, List<WeightedColorEntry>>, BlockColorMapSetting> {
        public Builder() {
            super(new LinkedHashMap<>(0));
        }

        @Override
        public BlockColorMapSetting build() {
            return new BlockColorMapSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
