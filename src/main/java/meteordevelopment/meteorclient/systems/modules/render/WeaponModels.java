/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.screens.settings.WeaponModelListScreen;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Swaps a held weapon's resource-pack model (via CustomModelData, same mechanism vanilla's own
 * item model "overrides" predicates already use) based on which of four states its ability is
 * currently in - Idle, Activate (one-shot, right after triggering), Active (looping, for as long
 * as the ability is unavailable again), Deactivate (one-shot, right as it becomes available again).
 * Each state is just a different CustomModelData value; the resource pack decides what that value
 * actually looks like - a plain flat icon, a full custom-elements 3D model (Blockbench-exported),
 * animated or not, none of that is this module's concern.
 *
 * State is read off the same repurposed-XP-bar cooldown signal AbilityCooldownHud/NoiseNotif
 * already rely on: mc.player.experienceProgress sits at 0 while ready, jumps up the instant an
 * ability is used, and drains back to 0 as it recharges. The up-edge is Activate, the down-edge
 * (back to ready) is Deactivate - both one-shot states that auto-advance into Active/Idle after
 * their configured duration.
 */
public class WeaponModels extends Module {
    public enum State {
        Idle, Activate, Active, Deactivate
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<WeaponModelEntry>> weapons = sgGeneral.add(new WeaponModelEntryListSetting.Builder()
        .name("weapons")
        .description("Which weapons get model overrides, and which CustomModelData value each of their four states uses - see Manage Weapon Models.")
        .build()
    );

    private final Setting<Void> manageWeapons = sgGeneral.add(new ButtonSetting.Builder()
        .name("manage-weapon-models")
        .description("Add/edit/remove weapon model entries.")
        .buttonText("Manage Weapon Models")
        .screen(theme -> new WeaponModelListScreen(theme, weapons))
        .build()
    );

    private final Setting<Integer> activateDuration = sgGeneral.add(new IntSetting.Builder()
        .name("activate-duration")
        .description("How long (ms) the Activate model stays up before switching to Active.")
        .defaultValue(250)
        .min(0)
        .sliderRange(0, 2000)
        .build()
    );

    private final Setting<Integer> deactivateDuration = sgGeneral.add(new IntSetting.Builder()
        .name("deactivate-duration")
        .description("How long (ms) the Deactivate model stays up before switching to Idle.")
        .defaultValue(250)
        .min(0)
        .sliderRange(0, 2000)
        .build()
    );

    private static final float READY_EPSILON = 0.001f;

    private float lastProgress = -1;
    private State state = State.Idle;
    private long stateChangedAt;

    public WeaponModels() {
        super(Categories.Render, "weapon-models", "Swaps a held weapon's resource-pack model (CustomModelData) based on its ability's idle/activate/active/deactivate state.", "weapon-animations-2", "ability-models");
    }

    @Override
    public void onActivate() {
        lastProgress = -1;
        state = State.Idle;
        stateChangedAt = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        float progress = mc.player.experienceProgress;
        long now = System.currentTimeMillis();

        if (lastProgress != -1) {
            boolean wasReady = lastProgress <= READY_EPSILON;
            boolean isReady = progress <= READY_EPSILON;

            if (wasReady && !isReady) {
                state = State.Activate;
                stateChangedAt = now;
            } else if (!wasReady && isReady) {
                state = State.Deactivate;
                stateChangedAt = now;
            }
        }
        lastProgress = progress;

        if (state == State.Activate && now - stateChangedAt >= activateDuration.get()) state = State.Active;
        if (state == State.Deactivate && now - stateChangedAt >= deactivateDuration.get()) state = State.Idle;
    }

    /** -1 = no override for this entry/state (render however it normally would). */
    private int baseModelDataFor(WeaponModelEntry entry) {
        return switch (state) {
            case Idle -> entry.idle;
            case Activate -> entry.activate;
            case Active -> entry.active;
            case Deactivate -> entry.deactivate;
        };
    }

    private int tierModelDataFor(WeaponModelEntry.TierOverride override) {
        return switch (state) {
            case Idle -> override.idle;
            case Activate -> override.activate;
            case Active -> override.active;
            case Deactivate -> override.deactivate;
        };
    }

    /** Tier override's own value for the current state if it has one, else falls back to the entry's base value, else -1 (no override at all). Matches on tier AND unique together - a Tier 4 Unique item only matches a tier-4-unique override, never a plain tier-4 one, since Unique is a separate rarity overlay, not just a recolor of the same tier. */
    private int modelDataFor(WeaponModelEntry entry, int tier, boolean unique) {
        if (tier >= 0) {
            for (WeaponModelEntry.TierOverride override : entry.tierOverrides) {
                if (override.tier != tier || override.unique != unique) continue;

                int value = tierModelDataFor(override);
                if (value >= 0) return value;

                break;
            }
        }

        return baseModelDataFor(entry);
    }

    private WeaponModelEntry findEntry(ItemStack stack) {
        if (stack.isEmpty()) return null;

        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        String name = stack.getName().getString().replaceAll("§.", "").toLowerCase(Locale.ROOT).trim();

        for (WeaponModelEntry entry : weapons.get()) {
            String key = entry.weapon.trim().toLowerCase(Locale.ROOT);
            boolean matches = key.startsWith("name:") ? name.contains(key.substring(5).trim()) : key.equals(itemId.toString());

            if (matches) return entry;
        }

        return null;
    }

    /** The stack to actually render in place of the real one, or the same stack unchanged if there's no override for its current state - called from the held-item render hook, main hand only. */
    public ItemStack applyOverride(ItemStack stack, Hand hand) {
        if (!isActive() || hand != Hand.MAIN_HAND) return stack;

        WeaponModelEntry entry = findEntry(stack);
        if (entry == null) return stack;

        ItemInfo itemInfo = Modules.get().get(ItemInfo.class);
        ItemInfo.WeaponTierInfo tierInfo = itemInfo != null ? itemInfo.findWeaponTierInfo(stack) : null;
        int tier = tierInfo != null ? tierInfo.tier() : -1;
        boolean unique = tierInfo != null && tierInfo.unique();

        int modelData = modelDataFor(entry, tier, unique);
        if (modelData < 0) return stack;

        ItemStack overridden = stack.copy();
        overridden.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(modelData));

        return overridden;
    }
}
