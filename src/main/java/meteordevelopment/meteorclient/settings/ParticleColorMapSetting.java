/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ParticleColorMapSetting extends Setting<Map<ParticleKey, SettingColor>> {
    public ParticleColorMapSetting(String name, String description, Map<ParticleKey, SettingColor> defaultValue, Consumer<Map<ParticleKey, SettingColor>> onChanged, Consumer<Setting<Map<ParticleKey, SettingColor>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected Map<ParticleKey, SettingColor> parseImpl(String str) {
        Map<ParticleKey, SettingColor> map = new LinkedHashMap<>();

        try {
            for (String entry : str.split(";")) {
                String[] parts = entry.trim().split(" ");

                ParticleKey key = ParticleKey.parse(parts[0]);
                if (key == null) continue;

                String[] rgba = parts[1].split(",");
                SettingColor color = new SettingColor(
                    Integer.parseInt(rgba[0]),
                    Integer.parseInt(rgba[1]),
                    Integer.parseInt(rgba[2]),
                    rgba.length > 3 ? Integer.parseInt(rgba[3]) : 255
                );

                map.put(key, color);
            }
        } catch (Exception ignored) {}

        return map;
    }

    @Override
    protected boolean isValueValid(Map<ParticleKey, SettingColor> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new LinkedHashMap<>();

        for (Map.Entry<ParticleKey, SettingColor> entry : defaultValue.entrySet()) {
            value.put(entry.getKey(), new SettingColor(entry.getValue()));
        }
    }

    @Override
    public Iterable<Identifier> getIdentifierSuggestions() {
        return Registries.PARTICLE_TYPE.getIds();
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtCompound valueTag = new NbtCompound();

        for (Map.Entry<ParticleKey, SettingColor> entry : get().entrySet()) {
            String key = entry.getKey().toSaveString();
            if (!key.isEmpty()) valueTag.put(key, entry.getValue().toTag());
        }

        tag.put("value", valueTag);

        return tag;
    }

    @Override
    protected Map<ParticleKey, SettingColor> load(NbtCompound tag) {
        get().clear();

        NbtCompound valueTag = tag.getCompound("value");
        for (String saveString : valueTag.getKeys()) {
            ParticleKey key = ParticleKey.parse(saveString);
            if (key != null) get().put(key, new SettingColor().fromTag(valueTag.getCompound(saveString)));
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, Map<ParticleKey, SettingColor>, ParticleColorMapSetting> {
        public Builder() {
            super(new LinkedHashMap<>(0));
        }

        @Override
        public ParticleColorMapSetting build() {
            return new ParticleColorMapSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
