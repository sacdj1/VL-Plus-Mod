/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.vlitems;

import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A server-custom item that reuses a vanilla item id but is a functionally different item
 * (e.g. a "Gingerbread Cookie" that's really minecraft:cookie under the hood) - distinguished
 * from other items sharing that id by its exact custom name and lore text. Storing the name/lore
 * as JSON (not a plain string) preserves formatting/colors, so a reconstructed stack round-trips
 * closely enough to the real item for CIT-based resource packs to pick the right texture.
 */
public class VLItem implements ISerializable<VLItem> {
    public Item baseItem = Items.AIR;

    // Cheap plain-text label for UI - decoded once at capture time, never used for matching.
    public String displayLabel = "";

    // Exact JSON-encoded Text for matching + stack reconstruction. Empty nameJson = no custom name.
    public String nameJson = "";
    public final List<String> loreJson = new ArrayList<>();

    public VLItem() {}
    public VLItem(NbtElement tag) {
        fromTag((NbtCompound) tag);
    }

    public static VLItem fromStack(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        VLItem item = new VLItem();
        item.baseItem = stack.getItem();

        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (name != null) {
            item.nameJson = Text.Serialization.toJsonString(name, registries);
            item.displayLabel = name.getString();
        } else {
            item.displayLabel = stack.getName().getString();
        }

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            // Drop the last line - servers commonly append a per-stack weight/total line there that
            // changes with count, which would otherwise make an identical item fail to match itself
            // once it merges into a stack of more than one.
            List<Text> lines = lore.lines();
            for (int i = 0; i < lines.size() - 1; i++) item.loreJson.add(Text.Serialization.toJsonString(lines.get(i), registries));
        }

        return item;
    }

    /** Reconstructs a display stack with the captured name/lore applied, so CIT/CTM texture rules match it. */
    public ItemStack buildStack(RegistryWrapper.WrapperLookup registries) {
        ItemStack stack = baseItem.getDefaultStack();

        if (!nameJson.isEmpty()) stack.set(DataComponentTypes.CUSTOM_NAME, Text.Serialization.fromJson(nameJson, registries));

        if (!loreJson.isEmpty()) {
            List<Text> lines = new ArrayList<>(loreJson.size());
            for (String json : loreJson) lines.add(Text.Serialization.fromJson(json, registries));
            stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
        }

        return stack;
    }

    public boolean matches(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        if (stack.getItem() != baseItem) return false;

        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        String stackNameJson = name != null ? Text.Serialization.toJsonString(name, registries) : "";
        if (!stackNameJson.equals(nameJson)) return false;

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        List<String> stackLoreJson = new ArrayList<>();
        if (lore != null) {
            List<Text> lines = lore.lines();
            for (int i = 0; i < lines.size() - 1; i++) stackLoreJson.add(Text.Serialization.toJsonString(lines.get(i), registries));
        }

        return stackLoreJson.equals(loreJson);
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        Identifier id = Registries.ITEM.getId(baseItem);
        tag.putString("baseItem", id.toString());
        tag.putString("displayLabel", displayLabel);
        tag.putString("nameJson", nameJson);

        NbtList loreList = new NbtList();
        for (String json : loreJson) loreList.add(NbtString.of(json));
        tag.put("lore", loreList);

        return tag;
    }

    @Override
    public VLItem fromTag(NbtCompound tag) {
        baseItem = Registries.ITEM.get(Identifier.of(tag.getString("baseItem")));
        displayLabel = tag.getString("displayLabel");
        nameJson = tag.getString("nameJson");

        loreJson.clear();
        for (NbtElement e : tag.getList("lore", NbtElement.STRING_TYPE)) loreJson.add(e.asString());

        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VLItem other)) return false;
        return baseItem == other.baseItem && nameJson.equals(other.nameJson) && loreJson.equals(other.loreJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseItem, nameJson, loreJson);
    }
}
