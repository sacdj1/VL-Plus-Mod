/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.systems.vlitems.VLItem;
import meteordevelopment.meteorclient.systems.vlitems.VLItemChoice;
import meteordevelopment.meteorclient.systems.vlitems.VLItems;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Like {@link ItemSetting}, but the picker also offers captured VL items ({@link VLItems}) alongside vanilla items. */
public class VLItemSetting extends Setting<VLItemChoice> {
    public final Predicate<Item> filter;

    public VLItemSetting(String name, String description, VLItemChoice defaultValue, Consumer<VLItemChoice> onChanged, Consumer<Setting<VLItemChoice>> onModuleActivated, IVisible visible, Predicate<Item> filter) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);

        this.filter = filter;
    }

    @Override
    protected VLItemChoice parseImpl(String str) {
        Item item = parseId(Registries.ITEM, str);
        return item == null ? null : VLItemChoice.of(item);
    }

    @Override
    protected boolean isValueValid(VLItemChoice value) {
        return value != null && (filter == null || filter.test(value.getBaseItem()));
    }

    @Override
    public Iterable<Identifier> getIdentifierSuggestions() {
        return Registries.ITEM.getIds();
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        if (get().vlItem != null) {
            tag.putBoolean("isVLItem", true);
            tag.put("vlItem", get().vlItem.toTag());
        } else {
            tag.putBoolean("isVLItem", false);
            tag.putString("item", Registries.ITEM.getId(get().item).toString());
        }

        return tag;
    }

    @Override
    protected VLItemChoice load(NbtCompound tag) {
        if (tag.getBoolean("isVLItem")) {
            VLItem loaded = new VLItem(tag.getCompound("vlItem"));

            VLItem match = null;
            for (VLItem item : VLItems.get()) {
                if (item.equals(loaded)) {
                    match = item;
                    break;
                }
            }

            value = VLItemChoice.of(match != null ? match : loaded);
        } else {
            Item item = Registries.ITEM.get(Identifier.of(tag.getString("item")));
            value = VLItemChoice.of(item);
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, VLItemChoice, VLItemSetting> {
        private Predicate<Item> filter;

        public Builder() {
            super(null);
        }

        public Builder filter(Predicate<Item> filter) {
            this.filter = filter;
            return this;
        }

        @Override
        public VLItemSetting build() {
            return new VLItemSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, filter);
        }
    }
}
