/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.VenturelandGate;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TrueSight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> whitelist = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("whitelist")
        .description("Only reveal these entity types. Leave empty to affect every living entity.")
        .onlyAttackable()
        .build()
    );

    private final Setting<List<String>> nameWhitelist = sgGeneral.add(new StringListSetting.Builder()
        .name("name-whitelist")
        .description("Only reveal entities whose name (case-insensitive, anywhere in it) contains one of these - one per line. Combines with the entity type whitelist above (both have to match if both are set); leave empty to not filter by name at all. For picking out one specific named mob, e.g. a boss, by name rather than by its whole entity type.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    public TrueSight() {
        super(Categories.Render, "truesight", "Reveals the body model of invisible entities that have armor or a held item equipped, instead of leaving just floating gear.");
    }

    public boolean shouldReveal(LivingEntity entity) {
        if (!isActive()) return false;
        if (!VenturelandGate.isAllowed()) return false;
        if (!whitelist.get().isEmpty() && !whitelist.get().contains(entity.getType())) return false;

        List<String> names = nameWhitelist.get();
        if (!names.isEmpty()) {
            String entityName = entity.getName().getString().toLowerCase(Locale.ROOT);

            boolean matches = false;
            for (String name : names) {
                String needle = name.trim().toLowerCase(Locale.ROOT);
                if (!needle.isEmpty() && entityName.contains(needle)) { matches = true; break; }
            }

            if (!matches) return false;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!entity.getEquippedStack(slot).isEmpty()) return true;
        }

        return false;
    }
}
