/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BiomeBlockColorSetting extends Setting<Map<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>>> {
    public BiomeBlockColorSetting(String name, String description, Map<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>> defaultValue, Consumer<Map<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>>> onChanged, Consumer<Setting<Map<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected Map<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>> parseImpl(String str) {
        return new LinkedHashMap<>();
    }

    @Override
    protected boolean isValueValid(Map<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new LinkedHashMap<>();

        for (Map.Entry<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>> biomeEntry : defaultValue.entrySet()) {
            Map<Block, List<WeightedColorEntry>> blocks = new LinkedHashMap<>();

            for (Map.Entry<Block, List<WeightedColorEntry>> blockEntry : biomeEntry.getValue().entrySet()) {
                List<WeightedColorEntry> colors = new ArrayList<>();
                for (WeightedColorEntry entry : blockEntry.getValue()) colors.add(new WeightedColorEntry(entry.color, entry.weight, entry.blend));

                blocks.put(blockEntry.getKey(), colors);
            }

            value.put(biomeEntry.getKey(), blocks);
        }
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtCompound valueTag = new NbtCompound();

        for (Map.Entry<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>> biomeEntry : get().entrySet()) {
            NbtCompound biomeTag = new NbtCompound();

            for (Map.Entry<Block, List<WeightedColorEntry>> blockEntry : biomeEntry.getValue().entrySet()) {
                Identifier blockId = Registries.BLOCK.getId(blockEntry.getKey());

                NbtList colorsTag = new NbtList();
                for (WeightedColorEntry entry : blockEntry.getValue()) colorsTag.add(entry.toTag());

                biomeTag.put(blockId.toString(), colorsTag);
            }

            valueTag.put(biomeEntry.getKey().getValue().toString(), biomeTag);
        }

        tag.put("value", valueTag);

        return tag;
    }

    @Override
    protected Map<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>> load(NbtCompound tag) {
        get().clear();

        NbtCompound valueTag = tag.getCompound("value");
        for (String biomeKeyStr : valueTag.getKeys()) {
            RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, Identifier.of(biomeKeyStr));
            NbtCompound biomeTag = valueTag.getCompound(biomeKeyStr);

            Map<Block, List<WeightedColorEntry>> blocks = new LinkedHashMap<>();

            for (String blockIdStr : biomeTag.getKeys()) {
                Block block = Registries.BLOCK.get(Identifier.of(blockIdStr));
                if (block == null) continue;

                List<WeightedColorEntry> colors = new ArrayList<>();
                for (NbtElement e : biomeTag.getList(blockIdStr, NbtElement.COMPOUND_TYPE)) {
                    colors.add(WeightedColorEntry.fromTag((NbtCompound) e));
                }

                blocks.put(block, colors);
            }

            get().put(biomeKey, blocks);
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, Map<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>>, BiomeBlockColorSetting> {
        public Builder() {
            super(new LinkedHashMap<>(0));
        }

        @Override
        public BiomeBlockColorSetting build() {
            return new BiomeBlockColorSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
