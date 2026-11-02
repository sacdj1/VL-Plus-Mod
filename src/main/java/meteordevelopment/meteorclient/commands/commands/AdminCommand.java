/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;

/**
 * Toggles the hidden Cheese category. Unlocking needs the 4-digit PIN (default 1337, change it
 * with ".Admin setpin <old> <new>"); locking again never does.
 *
 *   .Admin                      lock (if unlocked)
 *   .Admin <pin>                unlock with the PIN
 *   .Admin keep                 lock without turning running Cheese modules off
 *   .Admin setpin <old> <new>   change the PIN
 */
public class AdminCommand extends Command {
    public AdminCommand() {
        super("Admin", "Toggles visibility of the hidden Cheese category. Needs the 4-digit PIN to unlock (.Admin <pin>, default 1337); \"setpin <old> <new>\" changes it. Locking again never needs it. Separate from .dev.");
    }

    private static boolean validPin(String pin) {
        return pin.matches("\\d{4}");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> run(null, true));

        builder.then(literal("keep").executes(context -> run(null, false)));

        builder.then(literal("setpin")
            .then(argument("new", StringArgumentType.word()).executes(context -> {
                String pin = StringArgumentType.getString(context, "new");
                Modules modules = Modules.get();

                // A PIN always exists (default 1337), so changing it always needs the current one.
                ChatUtils.error("Change the PIN with \".Admin setpin <old> <new>\".");
                return SINGLE_SUCCESS;
            })
            .then(argument("newer", StringArgumentType.word()).executes(context -> {
                String old = StringArgumentType.getString(context, "new");
                String pin = StringArgumentType.getString(context, "newer");
                Modules modules = Modules.get();

                if (!modules.checkAdminPin(old)) {
                    ChatUtils.error("Wrong PIN.");
                    return SINGLE_SUCCESS;
                }
                if (!validPin(pin)) {
                    ChatUtils.error("The new PIN must be exactly 4 digits.");
                    return SINGLE_SUCCESS;
                }

                modules.setAdminPin(pin);
                ChatUtils.info("Admin PIN changed.");
                return SINGLE_SUCCESS;
            }))));

        builder.then(argument("pin", StringArgumentType.word()).executes(context -> run(StringArgumentType.getString(context, "pin"), true)));
    }

    private int run(String pin, boolean disableWhenHiding) {
        Modules modules = Modules.get();

        // The unlock persists across restarts, so ".Admin 1337" out of habit on an already-unlocked client used to LOCK it
        // again and switch every Cheese module off. Only a bare ".Admin" (or ".Admin keep") locks.
        if (modules.isCheeseUnlocked() && pin != null) {
            ChatUtils.info("Cheese modules are already visible (the unlock is saved). Use .Admin alone to hide them.");
            return SINGLE_SUCCESS;
        }

        if (!modules.isCheeseUnlocked()) {
            if (modules.hasAdminPin() && (pin == null || !modules.checkAdminPin(pin))) {
                ChatUtils.error(pin == null ? "Enter the 4-digit PIN: .Admin <pin>" : "Wrong PIN.");
                return SINGLE_SUCCESS;
            }

            modules.toggleCheese();
            ChatUtils.info("Cheese modules are now visible.");
            modules.applyAdminAutoEnable();
            if (modules.isDefaultAdminPin()) ChatUtils.info("Still using the default PIN (1337) - change it with \".Admin setpin 1337 <new 4 digits>\".");
            return SINGLE_SUCCESS;
        }

        modules.toggleCheese();

        if (disableWhenHiding) {
            int disabled = modules.disableHiddenCheeseActiveModules();
            ChatUtils.info("Cheese modules are now hidden" + (disabled > 0 ? " (turned off " + disabled + " running)." : "."));
        } else {
            ChatUtils.info("Cheese modules are now hidden (left running modules on).");
        }

        return SINGLE_SUCCESS;
    }
}
