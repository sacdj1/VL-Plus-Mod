/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.profiles;

import meteordevelopment.meteorclient.systems.macros.Macros;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.PostInit;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;

/**
 * One-time setup: creates a "Custom" profile snapshotting the player's current settings, and a
 * "Default" profile holding the plain code defaults, so there's always a baseline to compare/reset to.
 * Only runs once - if either profile already exists (by name), it's left untouched.
 */
public class ProfileBootstrap {
    private ProfileBootstrap() {
    }

    @PostInit
    public static void init() {
        Profiles profiles = Profiles.get();

        if (profiles.get("Custom") == null) {
            Profile custom = new Profile();
            custom.name.set("Custom");
            custom.modules.set(true);
            custom.hud.set(true);
            custom.macros.set(true);

            profiles.add(custom);
        }

        if (profiles.get("Default") == null) {
            Profile def = new Profile();
            def.name.set("Default");
            def.modules.set(true);
            def.macros.set(true);

            // Register the profile without letting Profile.save() snapshot the current (non-default) live state
            profiles.getAll().add(def);
            profiles.save();

            File folder = new File(Profiles.FOLDER, "Default");
            saveDefaultModules(folder);
            saveDefaultMacros(folder);
        }
    }

    private static void saveDefaultModules(File folder) {
        Modules fresh = new Modules();
        fresh.init();

        try {
            folder.mkdirs();
            NbtIo.write(fresh.toTag(), new File(folder, "modules.nbt").toPath());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            for (Module module : fresh.getAll()) module.settings.unregisterColorSettings();
        }
    }

    private static void saveDefaultMacros(File folder) {
        Macros fresh = new Macros();

        NbtCompound tag = fresh.toTag();
        if (tag == null) return;

        try {
            folder.mkdirs();
            NbtIo.write(tag, new File(folder, "macros.nbt").toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
