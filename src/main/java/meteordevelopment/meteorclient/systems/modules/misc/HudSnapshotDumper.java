/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.ButtonSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Dumps everything the vanilla HUD/F3 screen would show (health, hunger, xp, armor, held items,
 * effects, gamemode, ping) plus full location info (position, dimension, facing) to a text file -
 * a fixed point-in-time reference for comparing against later, same idea as EntityDataDumper/ItemDataDumper.
 */
public class HudSnapshotDumper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> captureKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("capture-key")
        .description("Saves a snapshot of the vanilla HUD state and your location to a file, even while a menu is open.")
        .defaultValue(Keybind.none())
        .action(this::capture)
        .build()
    );

    private final Setting<Void> captureButton = sgGeneral.add(new ButtonSetting.Builder()
        .name("capture-now")
        .description("Saves a snapshot immediately.")
        .buttonText("Capture")
        .action(this::capture)
        .build()
    );

    public HudSnapshotDumper() {
        super(Categories.Debug, "hud-snapshot-dumper", "Saves a snapshot of the vanilla HUD state and your location to a file for later analysis.");

        hidden = true;
        runInMainMenu = true;
    }

    private void capture() {
        ClientPlayerEntity player = mc.player;

        if (player == null || mc.world == null) {
            error("Not in a world.");
            return;
        }

        StringBuilder sb = new StringBuilder();

        // Location
        BlockPos pos = player.getBlockPos();
        sb.append("=== Location ===\n");
        sb.append("Dimension: ").append(mc.world.getRegistryKey().getValue()).append('\n');
        sb.append("Position: ").append(String.format("%.3f, %.3f, %.3f", player.getX(), player.getY(), player.getZ())).append('\n');
        sb.append("Block Pos: ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ()).append('\n');
        sb.append("Yaw / Pitch: ").append(String.format("%.2f / %.2f", player.getYaw(), player.getPitch())).append('\n');
        sb.append("Facing: ").append(player.getHorizontalFacing()).append('\n');
        if (mc.getCurrentServerEntry() != null) sb.append("Server: ").append(mc.getCurrentServerEntry().address).append('\n');
        sb.append("Ping: ").append(getPing(player)).append("ms\n");

        // Vanilla HUD
        sb.append("\n=== HUD ===\n");
        sb.append("Health: ").append(player.getHealth()).append(" / ").append(player.getMaxHealth()).append('\n');
        sb.append("Absorption: ").append(player.getAbsorptionAmount()).append('\n');
        sb.append("Armor: ").append(player.getArmor()).append('\n');
        sb.append("Hunger: ").append(player.getHungerManager().getFoodLevel()).append(" / 20\n");
        sb.append("Saturation: ").append(player.getHungerManager().getSaturationLevel()).append('\n');
        sb.append("Air: ").append(player.getAir()).append(" / ").append(player.getMaxAir()).append('\n');
        sb.append("XP Level: ").append(player.experienceLevel).append('\n');
        sb.append("XP Progress: ").append(String.format("%.2f", player.experienceProgress)).append('\n');
        sb.append("Total XP: ").append(player.totalExperience).append('\n');
        sb.append("Game Mode: ").append(mc.interactionManager != null ? mc.interactionManager.getCurrentGameMode() : "Unknown").append('\n');

        sb.append("\n=== Hotbar / Equipment ===\n");
        for (int i = 0; i < 9; i++) sb.append("Hotbar ").append(i).append(": ").append(describe(player.getInventory().getStack(i))).append('\n');
        sb.append("Off Hand: ").append(describe(player.getOffHandStack())).append('\n');
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            sb.append(slot.getName()).append(": ").append(describe(player.getEquippedStack(slot))).append('\n');
        }

        sb.append("\n=== Status Effects ===\n");
        if (player.getStatusEffects().isEmpty()) sb.append("None\n");
        else {
            for (StatusEffectInstance effect : player.getStatusEffects()) {
                sb.append(effect.getEffectType().value().getName().getString())
                    .append(" (Amplifier ").append(effect.getAmplifier())
                    .append(", ").append(effect.getDuration()).append(" ticks left)\n");
            }
        }

        File folder = new File(MeteorClient.FOLDER, "hud-dumps");
        folder.mkdirs();

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File file = new File(folder, "hud_" + timestamp + ".txt");

        try {
            Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            error("Failed to save HUD snapshot: %s", e.getMessage());
            return;
        }

        info("Saved HUD snapshot to hud-dumps/%s", file.getName());
    }

    private static String describe(ItemStack stack) {
        if (stack.isEmpty()) return "Empty";
        return stack.getCount() + "x " + stack.getItem().toString();
    }

    private int getPing(ClientPlayerEntity player) {
        if (mc.getNetworkHandler() == null) return -1;
        var entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        return entry != null ? entry.getLatency() : -1;
    }
}
