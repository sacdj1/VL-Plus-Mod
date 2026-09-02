/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;

import java.util.Set;

public class TrueSight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> whitelist = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("whitelist")
        .description("Only reveal these entity types. Leave empty to affect every living entity.")
        .onlyAttackable()
        .build()
    );

    public TrueSight() {
        super(Categories.Render, "truesight", "Reveals the body model of invisible entities that have armor or a held item equipped, instead of leaving just floating gear.");
    }

    public boolean shouldReveal(LivingEntity entity) {
        if (!isActive()) return false;
        if (!whitelist.get().isEmpty() && !whitelist.get().contains(entity.getType())) return false;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!entity.getEquippedStack(slot).isEmpty()) return true;
        }

        return false;
    }
}
