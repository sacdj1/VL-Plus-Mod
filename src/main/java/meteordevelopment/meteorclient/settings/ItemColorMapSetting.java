/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ItemColorMapSetting extends Setting<Map<Item, SettingColor>> {
    public ItemColorMapSetting(String name, String description, Map<Item, SettingColor> defaultValue, Consumer<Map<Item, SettingColor>> onChanged, Consumer<Setting<Map<Item, SettingColor>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected Map<Item, SettingColor> parseImpl(String str) {
        Map<Item, SettingColor> map = new LinkedHashMap<>();

        try {
            for (String entry : str.split(";")) {
                String[] parts = entry.trim().split(" ");

                Item item = parseId(Registries.ITEM, parts[0]);
                if (item == null) continue;

                String[] rgba = parts[1].split(",");
                SettingColor color = new SettingColor(
                    Integer.parseInt(rgba[0]),
                    Integer.parseInt(rgba[1]),
                    Integer.parseInt(rgba[2]),
                    rgba.length > 3 ? Integer.parseInt(rgba[3]) : 255
                );

                map.put(item, color);
            }
        } catch (Exception ignored) {}

        return map;
    }

    @Override
    protected boolean isValueValid(Map<Item, SettingColor> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new LinkedHashMap<>();

        for (Map.Entry<Item, SettingColor> entry : defaultValue.entrySet()) {
            value.put(entry.getKey(), new SettingColor(entry.getValue()));
        }
    }

    @Override
    public Iterable<Identifier> getIdentifierSuggestions() {
        return Registries.ITEM.getIds();
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtCompound valueTag = new NbtCompound();

        for (Map.Entry<Item, SettingColor> entry : get().entrySet()) {
            Identifier id = Registries.ITEM.getId(entry.getKey());
            if (id != null) valueTag.put(id.toString(), entry.getValue().toTag());
        }

        tag.put("value", valueTag);

        return tag;
    }

    @Override
    protected Map<Item, SettingColor> load(NbtCompound tag) {
        get().clear();

        NbtCompound valueTag = tag.getCompound("value");
        for (String key : valueTag.getKeys()) {
            Item item = Registries.ITEM.get(Identifier.of(key));
            if (item != null) get().put(item, new SettingColor().fromTag(valueTag.getCompound(key)));
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, Map<Item, SettingColor>, ItemColorMapSetting> {
        public Builder() {
            super(new LinkedHashMap<>(0));
        }

        @Override
        public ItemColorMapSetting build() {
            return new ItemColorMapSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
