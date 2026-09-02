/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

import java.util.Set;

/**
 * Forces vanilla's F3+B hitbox rendering on (instead of needing the vanilla debug toggle), and can
 * additionally force hitboxes to render on invisible entities - unlike TrueSight (which reveals an
 * invisible entity's full body model), this only ever shows the wireframe hitbox, nothing else.
 */
public class Hitboxes extends Module {
    public enum ListMode {
        Whitelist,
        Blacklist
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgInvisible = settings.createGroup("Invisible Entities");

    private final Setting<Boolean> alwaysOn = sgGeneral.add(new BoolSetting.Builder()
        .name("always-on")
        .description("Keeps vanilla hitboxes forced on continuously - even in the main menu - so nothing (including you pressing F3+B) can turn them off while this module is active.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ListMode> generalListMode = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("general-list-mode")
        .description("Whether the list below is a whitelist or blacklist for which entity types show a hitbox at all.")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    private final Setting<Set<EntityType<?>>> generalList = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("general-list")
        .description("Entity types to include/exclude from hitbox rendering, depending on the mode above. Empty blacklist = no exclusions.")
        .build()
    );

    private final Setting<ListMode> invisListMode = sgInvisible.add(new EnumSetting.Builder<ListMode>()
        .name("invis-list-mode")
        .description("Whether the list below is a whitelist or blacklist for showing hitboxes on invisible entities.")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    private final Setting<Set<EntityType<?>>> invisList = sgInvisible.add(new EntityTypeListSetting.Builder()
        .name("invis-list")
        .description("Which invisible entity types still show a hitbox, depending on the mode above. Empty blacklist (default) = every invisible entity shows a hitbox.")
        .build()
    );

    public Hitboxes() {
        super(Categories.Render, "hitboxes", "Forces vanilla F3+B hitboxes on, and can show them on invisible entities too.");

        runInMainMenu = true;
    }

    @Override
    public void onActivate() {
        mc.getEntityRenderDispatcher().setRenderHitboxes(true);
    }

    @Override
    public void onDeactivate() {
        mc.getEntityRenderDispatcher().setRenderHitboxes(false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (alwaysOn.get() && !mc.getEntityRenderDispatcher().shouldRenderHitboxes()) {
            mc.getEntityRenderDispatcher().setRenderHitboxes(true);
        }
    }

    public boolean showHitbox(Entity entity) {
        return matches(generalListMode.get(), generalList.get(), entity.getType());
    }

    public boolean showInvisibleHitbox(Entity entity) {
        return matches(invisListMode.get(), invisList.get(), entity.getType());
    }

    private static boolean matches(ListMode mode, Set<EntityType<?>> list, EntityType<?> type) {
        return mode == ListMode.Whitelist ? list.contains(type) : !list.contains(type);
    }
}
