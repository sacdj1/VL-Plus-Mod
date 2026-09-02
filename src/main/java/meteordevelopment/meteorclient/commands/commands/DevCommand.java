/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;

public class DevCommand extends Command {
    public DevCommand() {
        super("dev", "Toggles visibility of hidden debug/dev modules. Hiding them also turns off any that are running, unless run with \"keep\".");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> run(true));

        builder.then(literal("keep").executes(context -> run(false)));
    }

    private int run(boolean disableWhenHiding) {
        boolean unlocked = Modules.get().toggleDev();

        if (unlocked) {
            ChatUtils.info("Dev modules are now visible.");
            return SINGLE_SUCCESS;
        }

        if (disableWhenHiding) {
            int disabled = Modules.get().disableHiddenActiveModules();
            ChatUtils.info("Dev modules are now hidden" + (disabled > 0 ? " (turned off " + disabled + " running)." : "."));
        } else {
            ChatUtils.info("Dev modules are now hidden (left running modules on).");
        }

        return SINGLE_SUCCESS;
    }
}
