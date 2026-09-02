/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.joml.Vector3d;

import java.util.Set;

public class EntityInspector extends Module {
    private static final Color TEXT = new Color(255, 255, 255);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Max distance to show entity names at.")
        .defaultValue(16)
        .range(1, 256)
        .sliderMax(128)
        .build()
    );

    private final Setting<Boolean> customName = sgGeneral.add(new BoolSetting.Builder()
        .name("show-custom-name")
        .description("Also shows the entity's custom name, if it has one, below its exact type.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The scale of the nameplate.")
        .defaultValue(1)
        .range(0.1, 5)
        .sliderMax(3)
        .build()
    );

    private final Setting<Set<EntityType<?>>> blacklist = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("blacklist")
        .description("Entity types to never show the name of.")
        .build()
    );

    public EntityInspector() {
        super(Categories.Debug, "entity-inspector", "Shows the exact entity type of nearby entities above their heads.");

        hidden = true;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;

        TextRenderer text = TextRenderer.get();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (blacklist.get().contains(entity.getType())) continue;

            double dist = PlayerUtils.distanceToCamera(entity.getX(), entity.getY(), entity.getZ());
            if (dist > range.get()) continue;

            if (!mc.player.canSee(entity)) continue;

            Vector3d pos = new Vector3d(entity.getX(), entity.getY() + entity.getHeight() + 0.5, entity.getZ());
            if (!NametagUtils.to2D(pos, scale.get())) continue;

            Identifier id = EntityType.getId(entity.getType());
            String typeName = id != null ? id.toString() : entity.getType().toString();

            NametagUtils.begin(pos);
            text.begin();

            text.render(typeName, -text.getWidth(typeName) / 2, -text.getHeight(), TEXT, true);

            if (customName.get() && entity.hasCustomName() && entity.getCustomName() != null) {
                String name = entity.getCustomName().getString();
                text.render(name, -text.getWidth(name) / 2, 2, TEXT, true);
            }

            text.end();
            NametagUtils.end();
        }
    }
}
