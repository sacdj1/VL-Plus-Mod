/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.vlitems;

import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class VLItems extends System<VLItems> implements Iterable<VLItem> {
    private final List<VLItem> items = new ArrayList<>();

    public VLItems() {
        super("vl-items");
    }

    public static VLItems get() {
        return Systems.get(VLItems.class);
    }

    public boolean add(VLItem item) {
        if (items.contains(item)) return false;

        items.add(item);
        save();
        return true;
    }

    public boolean remove(VLItem item) {
        if (items.remove(item)) {
            save();
            return true;
        }

        return false;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int count() {
        return items.size();
    }

    public VLItem find(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        for (VLItem item : items) {
            if (item.matches(stack, registries)) return item;
        }

        return null;
    }

    @Override
    public @NotNull Iterator<VLItem> iterator() {
        return items.iterator();
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.put("items", NbtUtils.listToTag(items));

        return tag;
    }

    @Override
    public VLItems fromTag(NbtCompound tag) {
        items.clear();

        if (tag.contains("items")) {
            items.addAll(NbtUtils.listFromTag(tag.getList("items", 10), VLItem::new));
        }

        return this;
    }
}
