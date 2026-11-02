/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.effect.StatusEffects;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Per-status-effect fill color overrides shared by the Health/Stamina Bar Adjust modules and HUD
 * elements. Each effect gets its own toggle + color (off by default, so nothing changes until one
 * is turned on). Entries are checked in the order they're added below, first active one wins -
 * vanilla's own heart precedence (Wither, then Poison, then Frozen) first, then the rest.
 *
 * Each effect also picks how its color animates: Fixed, Flashing (hard alternation between the two
 * colors), Gradient (smooth back-and-forth blend between them) or Rainbow (cycles the whole hue
 * wheel, ignoring the colors) - all at a per-effect speed.
 */
public class StatusColorOverrides {
    public enum EffectColorMode {
        Fixed,
        Flashing,
        Gradient,
        Rainbow
    }

    private record Entry(Setting<Boolean> enabled, Setting<SettingColor> color, Setting<EffectColorMode> mode, Setting<SettingColor> color2, Setting<Double> speed, BooleanSupplier active) {}

    private final List<Entry> entries = new ArrayList<>();

    public StatusColorOverrides(SettingGroup group) {
        add(group, "wither", "Wither", new SettingColor(45, 45, 45), () -> hasEffect(StatusEffects.WITHER));
        add(group, "poison", "Poison", new SettingColor(140, 160, 30), () -> hasEffect(StatusEffects.POISON));
        add(group, "freezing", "Freezing", new SettingColor(140, 210, 255), () -> mc.player != null && mc.player.isFrozen());
        add(group, "hunger", "Hunger", new SettingColor(150, 130, 60), () -> hasEffect(StatusEffects.HUNGER));
        add(group, "regeneration", "Regeneration", new SettingColor(255, 105, 180), () -> hasEffect(StatusEffects.REGENERATION));
    }

    private void add(SettingGroup group, String id, String label, SettingColor defaultColor, BooleanSupplier active) {
        Setting<Boolean> enabled = group.add(new BoolSetting.Builder()
            .name(id + "-color")
            .description("Overrides the fill color while you have " + label + ", taking priority over Fill Mode and Max/Low colors.")
            .defaultValue(false)
            .build()
        );

        Setting<SettingColor> color = group.add(new ColorSetting.Builder()
            .name(id + "-color-value")
            .description("Fill color used while " + label + " is active, when " + label + " Color above is on.")
            .defaultValue(defaultColor)
            .visible(enabled::get)
            .build()
        );

        Setting<EffectColorMode> mode = group.add(new EnumSetting.Builder<EffectColorMode>()
            .name(id + "-color-mode")
            .description("How the " + label + " color animates: Fixed stays one color, Flashing alternates hard between the two colors, Gradient blends smoothly back and forth between them, Rainbow cycles through every hue.")
            .defaultValue(EffectColorMode.Fixed)
            .visible(enabled::get)
            .build()
        );

        Setting<SettingColor> color2 = group.add(new ColorSetting.Builder()
            .name(id + "-color-second")
            .description("Second color for " + label + "'s Flashing/Gradient modes.")
            .defaultValue(new SettingColor(255, 255, 255))
            .visible(() -> enabled.get() && (mode.get() == EffectColorMode.Flashing || mode.get() == EffectColorMode.Gradient))
            .build()
        );

        Setting<Double> speed = group.add(new DoubleSetting.Builder()
            .name(id + "-color-speed")
            .description("Animation speed for " + label + "'s color, in full cycles per second.")
            .defaultValue(1.0)
            .min(0.05)
            .sliderRange(0.1, 5)
            .visible(() -> enabled.get() && mode.get() != EffectColorMode.Fixed)
            .build()
        );

        entries.add(new Entry(enabled, color, mode, color2, speed, active));
    }

    private static boolean hasEffect(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect) {
        return mc.player != null && mc.player.hasStatusEffect(effect);
    }

    /** The color of the first enabled override whose effect is currently active (animated per its mode), or null if none apply. */
    public Color get() {
        for (Entry entry : entries) {
            if (entry.enabled().get() && entry.active().getAsBoolean()) return resolve(entry);
        }

        return null;
    }

    private static Color resolve(Entry entry) {
        SettingColor a = entry.color().get();
        double phase = (System.currentTimeMillis() / 1000.0 * entry.speed().get()) % 1.0;

        return switch (entry.mode().get()) {
            case Fixed -> a;
            case Flashing -> phase < 0.5 ? a : entry.color2().get();
            case Gradient -> {
                SettingColor b = entry.color2().get();
                double t = 0.5 - 0.5 * Math.cos(phase * 2 * Math.PI);
                yield new Color(
                    (int) Math.round(a.r + (b.r - a.r) * t),
                    (int) Math.round(a.g + (b.g - a.g) * t),
                    (int) Math.round(a.b + (b.b - a.b) * t),
                    (int) Math.round(a.a + (b.a - a.a) * t)
                );
            }
            case Rainbow -> Color.fromHsv(phase * 360.0, 1, 1);
        };
    }
}
