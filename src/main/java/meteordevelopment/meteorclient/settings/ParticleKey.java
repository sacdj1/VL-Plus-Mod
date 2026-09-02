/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Objects;

/**
 * Identifies a particle type for matching/selection purposes - for most particle types this is
 * just the {@link ParticleType} itself, but "block" and "block_marker" particles carry a specific
 * {@link Block} and "item" particles carry a specific {@link Item}, so those need the extra field
 * to be distinguishable (otherwise every block/item particle in the game would be one entry).
 */
public final class ParticleKey {
    public final ParticleType<?> type;
    public final Block block;
    public final Item item;

    private ParticleKey(ParticleType<?> type, Block block, Item item) {
        this.type = type;
        this.block = block;
        this.item = item;
    }

    public static ParticleKey of(ParticleType<?> type) {
        return new ParticleKey(type, null, null);
    }

    public static ParticleKey ofBlock(ParticleType<?> type, Block block) {
        return new ParticleKey(type, block, null);
    }

    public static ParticleKey ofItem(ParticleType<?> type, Item item) {
        return new ParticleKey(type, null, item);
    }

    public static ParticleKey from(ParticleEffect effect) {
        if (effect instanceof BlockStateParticleEffect bs) return ofBlock(effect.getType(), bs.getBlockState().getBlock());
        if (effect instanceof ItemStackParticleEffect is) return ofItem(effect.getType(), is.getItemStack().getItem());

        return of(effect.getType());
    }

    public boolean isBlockKind() {
        return type == ParticleTypes.BLOCK || type == ParticleTypes.BLOCK_MARKER;
    }

    public boolean isItemKind() {
        return type == ParticleTypes.ITEM;
    }

    /** "minecraft:block/minecraft:stone", "minecraft:item/minecraft:cookie", or just "minecraft:crit". */
    public String toSaveString() {
        Identifier typeId = Registries.PARTICLE_TYPE.getId(type);
        if (typeId == null) return "";

        if (block != null) return typeId + "/" + Registries.BLOCK.getId(block);
        if (item != null) return typeId + "/" + Registries.ITEM.getId(item);

        return typeId.toString();
    }

    public static ParticleKey parse(String saveString) {
        String[] parts = saveString.split("/", 2);

        ParticleType<?> type = Registries.PARTICLE_TYPE.get(Identifier.of(parts[0]));
        if (type == null) return null;

        if (parts.length < 2) return of(type);

        Identifier extraId = Identifier.of(parts[1]);
        if (type == ParticleTypes.BLOCK || type == ParticleTypes.BLOCK_MARKER) {
            Block block = Registries.BLOCK.get(extraId);
            return ofBlock(type, block);
        }
        if (type == ParticleTypes.ITEM) {
            Item item = Registries.ITEM.get(extraId);
            return ofItem(type, item);
        }

        return of(type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParticleKey other)) return false;

        return type == other.type && block == other.block && item == other.item;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, block, item);
    }
}
