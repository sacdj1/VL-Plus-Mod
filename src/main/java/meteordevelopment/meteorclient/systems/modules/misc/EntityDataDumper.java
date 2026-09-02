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
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

public class EntityDataDumper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> captureKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("capture-key")
        .description("Saves the targeted entity's full NBT data to a file, even while a menu is open.")
        .defaultValue(Keybind.none())
        .action(this::capture)
        .build()
    );

    private final Setting<Void> captureButton = sgGeneral.add(new ButtonSetting.Builder()
        .name("capture-now")
        .description("Saves the currently targeted entity's data immediately.")
        .buttonText("Capture")
        .action(this::capture)
        .build()
    );

    public EntityDataDumper() {
        super(Categories.Debug, "entity-data-dumper", "Saves the targeted entity's full NBT data to a file for later analysis.");

        hidden = true;
        runInMainMenu = true;
    }

    private void capture() {
        Entity entity = mc.targetedEntity;

        if (entity == null) {
            error("Not looking at any entity.");
            return;
        }

        NbtCompound tag = new NbtCompound();
        entity.saveNbt(tag);

        Identifier id = EntityType.getId(entity.getType());
        String typeName = id != null ? id.getPath() : "entity";

        File folder = new File(MeteorClient.FOLDER, "entity-dumps");
        folder.mkdirs();

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File file = new File(folder, typeName + "_" + timestamp + ".txt");

        try {
            Files.writeString(file.toPath(), NbtHelper.toFormattedString(tag), StandardCharsets.UTF_8);
        } catch (IOException e) {
            error("Failed to save entity data: %s", e.getMessage());
            return;
        }

        info("Saved %s data to entity-dumps/%s", typeName, file.getName());
    }
}
