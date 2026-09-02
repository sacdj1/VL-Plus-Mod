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
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes a whole block -> colors map (a biome's or a region's) as one unit, so it can be
 * copied from one and pasted onto another - e.g. copy a biome's setup from the per-biome list and
 * paste it straight onto a region.
 */
public class BlockColorMapNbt {
    private BlockColorMapNbt() {
    }

    public static NbtCompound toTag(Map<Block, List<WeightedColorEntry>> blocks) {
        NbtCompound tag = new NbtCompound();

        NbtCompound blocksTag = new NbtCompound();
        for (Map.Entry<Block, List<WeightedColorEntry>> entry : blocks.entrySet()) {
            Identifier blockId = Registries.BLOCK.getId(entry.getKey());

            NbtList colorsTag = new NbtList();
            for (WeightedColorEntry color : entry.getValue()) colorsTag.add(color.toTag());

            blocksTag.put(blockId.toString(), colorsTag);
        }
        tag.put("blocks", blocksTag);

        return tag;
    }

    public static Map<Block, List<WeightedColorEntry>> fromTag(NbtCompound tag) {
        Map<Block, List<WeightedColorEntry>> blocks = new LinkedHashMap<>();

        NbtCompound blocksTag = tag.getCompound("blocks");
        for (String blockIdStr : blocksTag.getKeys()) {
            Block block = Registries.BLOCK.get(Identifier.of(blockIdStr));
            if (block == null) continue;

            List<WeightedColorEntry> colors = new ArrayList<>();
            for (NbtElement e : blocksTag.getList(blockIdStr, NbtElement.COMPOUND_TYPE)) {
                colors.add(WeightedColorEntry.fromTag((NbtCompound) e));
            }

            blocks.put(block, colors);
        }

        return blocks;
    }
}
