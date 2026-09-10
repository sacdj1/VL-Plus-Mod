/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixin.HandledScreenAccessor;
import meteordevelopment.meteorclient.settings.ButtonSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ItemDataDumper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> captureKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("capture-key")
        .description("Saves the targeted item's full NBT/component data to a file, even while a menu is open.")
        .defaultValue(Keybind.none())
        .action(this::capture)
        .build()
    );

    private final Setting<Void> captureButton = sgGeneral.add(new ButtonSetting.Builder()
        .name("capture-now")
        .description("Saves the currently targeted item's data immediately.")
        .buttonText("Capture")
        .action(this::capture)
        .build()
    );

    private final Setting<Keybind> captureAllKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("capture-all-key")
        .description("Saves every non-empty item currently in the open GUI (or your own inventory/hotbar/armor/offhand if no GUI is open) to its own file, in one go.")
        .defaultValue(Keybind.none())
        .action(this::captureAll)
        .build()
    );

    private final Setting<Void> captureAllButton = sgGeneral.add(new ButtonSetting.Builder()
        .name("capture-all-now")
        .description("Saves every non-empty item currently in the open GUI (or your own inventory/hotbar/armor/offhand if no GUI is open) immediately.")
        .buttonText("Capture All")
        .action(this::captureAll)
        .build()
    );

    public ItemDataDumper() {
        super(Categories.Debug, "item-data-dumper", "Saves item full NBT/component data to a file for later analysis.");

        hidden = true;
        runInMainMenu = true;
    }

    private void capture() {
        ItemStack stack = getTargetStack();

        if (stack == null || stack.isEmpty()) {
            error("Not holding or hovering any item.");
            return;
        }

        if (mc.world == null) {
            error("Not in a world.");
            return;
        }

        String name = dumpStack(stack);
        if (name != null) info("Saved data to item-dumps/%s", name);
    }

    private void captureAll() {
        if (mc.world == null) {
            error("Not in a world.");
            return;
        }

        int count = 0;

        if (mc.currentScreen instanceof HandledScreen<?> handledScreen) {
            for (Slot slot : handledScreen.getScreenHandler().slots) {
                if (slot.hasStack() && dumpStack(slot.getStack()) != null) count++;
            }
        }
        else if (mc.player != null) {
            for (ItemStack stack : mc.player.getInventory().main) {
                if (!stack.isEmpty() && dumpStack(stack) != null) count++;
            }
            for (ItemStack stack : mc.player.getInventory().armor) {
                if (!stack.isEmpty() && dumpStack(stack) != null) count++;
            }
            for (ItemStack stack : mc.player.getInventory().offHand) {
                if (!stack.isEmpty() && dumpStack(stack) != null) count++;
            }
        }

        if (count == 0) error("No non-empty items found to dump.");
        else info("Saved %d item(s) to item-dumps/", count);
    }

    /** Writes one stack's data to its own file and returns the saved file's name, or null on failure. */
    private String dumpStack(ItemStack stack) {
        NbtElement tag = stack.encode(mc.world.getRegistryManager());

        Identifier id = Registries.ITEM.getId(stack.getItem());
        String typeName = id != null ? id.getPath() : "item";

        File folder = new File(MeteorClient.FOLDER, "item-dumps");
        folder.mkdirs();

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS").format(new Date());
        File file = new File(folder, typeName + "_" + timestamp + ".txt");

        try {
            Files.writeString(file.toPath(), NbtHelper.toFormattedString(tag), StandardCharsets.UTF_8);
        } catch (IOException e) {
            error("Failed to save item data: %s", e.getMessage());
            return null;
        }

        return file.getName();
    }

    private ItemStack getTargetStack() {
        if (mc.currentScreen instanceof HandledScreen<?> handledScreen) {
            Slot slot = ((HandledScreenAccessor) handledScreen).getFocusedSlot();
            if (slot != null && slot.hasStack()) return slot.getStack();
        }

        if (mc.player == null) return null;

        ItemStack mainHand = mc.player.getMainHandStack();
        if (!mainHand.isEmpty()) return mainHand;

        return mc.player.getOffHandStack();
    }
}
