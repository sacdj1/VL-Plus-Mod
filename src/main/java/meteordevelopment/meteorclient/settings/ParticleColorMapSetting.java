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
    /**
     * Whether a picked entry's own color/mode are actually used anywhere, or this is really just a
     * particle-type selection list (e.g. Hit Detection's own Hit Particles, Radius Clear's Listed
     * particles) that happens to reuse this same map-shaped setting for convenience. When false, the
     * settings screen hides the per-entry color/mode editor entirely instead of showing a second,
     * functionally dead color picker next to whichever ACTUAL color control governs this case
     * (confirmed real confusion: "hit detect particle recolouring still has 2 places of choosing a
     * color" - one was this list's own per-entry editor, silently ignored for color and, worse,
     * secretly NOT ignored for mode until getRecolorMode() was fixed to stop reading it).
     */
    public final boolean colorsMatter;

    public ParticleColorMapSetting(String name, String description, Map<ParticleKey, SettingColor> defaultValue, Consumer<Map<ParticleKey, SettingColor>> onChanged, Consumer<Setting<Map<ParticleKey, SettingColor>>> onModuleActivated, IVisible visible, boolean colorsMatter) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);

        this.colorsMatter = colorsMatter;
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
                SettingColor color = new ParticleRecolor(
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
            value.put(entry.getKey(), new ParticleRecolor(entry.getValue()));
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
            if (key.isEmpty()) continue;

            NbtCompound colorTag = entry.getValue().toTag();
            if (entry.getValue() instanceof ParticleRecolor recolor) colorTag.putString("recolor-mode", recolor.mode.name());
            valueTag.put(key, colorTag);
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
            if (key == null) continue;

            NbtCompound colorTag = valueTag.getCompound(saveString);
            ParticleRecolor recolor = new ParticleRecolor(new SettingColor().fromTag(colorTag));

            if (colorTag.contains("recolor-mode")) {
                try {
                    recolor.mode = ParticleRecolor.Mode.valueOf(colorTag.getString("recolor-mode"));
                } catch (IllegalArgumentException ignored) {}
            }
            // Entries saved before modes existed: block/item particles never actually changed color
            // under the old blend-toward-tint behavior (a colored texture times a tint just goes
            // dark), so they get Colorize; everything else keeps the old blend behavior so nothing
            // that already worked shifts underneath the user.
            else recolor.mode = key.isBlockKind() || key.isItemKind() ? ParticleRecolor.Mode.Colorize : ParticleRecolor.Mode.Blend;

            // Blend can't ever produce the picked color on a block/item particle (a red texture times a green tint is near
            // black) - an entry stuck on Blend from before modes existed just looked like "recoloring doesn't work", so
            // those load as Colorize. Pick Replace/Multiply/Add for a different look.
            if (recolor.mode == ParticleRecolor.Mode.Blend && (key.isBlockKind() || key.isItemKind())) recolor.mode = ParticleRecolor.Mode.Colorize;

            get().put(key, recolor);
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, Map<ParticleKey, SettingColor>, ParticleColorMapSetting> {
        private boolean colorsMatter = true;

        public Builder() {
            super(new LinkedHashMap<>(0));
        }

        /** False for a setting that's really just a particle-type selection list - see ParticleColorMapSetting.colorsMatter. */
        public Builder colorsMatter(boolean colorsMatter) {
            this.colorsMatter = colorsMatter;
            return this;
        }

        @Override
        public ParticleColorMapSetting build() {
            return new ParticleColorMapSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, colorsMatter);
        }
    }
}
