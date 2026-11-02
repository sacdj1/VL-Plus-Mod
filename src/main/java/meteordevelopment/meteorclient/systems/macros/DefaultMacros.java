/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.macros;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.misc.Keybind;

import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Seeds the same 11 chat keybinds the "Chat Tools" mod's chatkeybindings.Macro.List used to provide
 * (read from its config, config/chat_tools.json, once), as ordinary VL+ macros - so Chat Tools can be
 * disabled without losing them. Added by name, one at a time, on every launch after Macros loads its
 * saved file - a macro already present (by name) is left alone, so this never overwrites a rename or
 * edit and never duplicates on a later launch. New ones a future version adds here still show up for
 * everyone even if they've since edited the others.
 *
 * "[item]" (Chat Tools' own placeholder for a clickable link to your held item) has no equivalent
 * Starscript function here, so that one macro sends the literal text "[item]" instead of a real link.
 */
public class DefaultMacros {
    private DefaultMacros() {
    }

    public static void seed() {
        Macros macros = Macros.get();
        if (macros == null) return;

        boolean added = false;

        added |= addIfMissing(macros, "Waypoint Off", Keybind.fromKeys(GLFW_KEY_Y, GLFW_MOD_CONTROL), "/waypoint off");
        added |= addIfMissing(macros, "AFK", Keybind.fromKeys(GLFW_KEY_PERIOD, GLFW_MOD_CONTROL), "[VL+ QC]: AFK!");
        added |= addIfMissing(macros, "Busy", Keybind.fromKeys(GLFW_KEY_GRAVE_ACCENT, 0), "[VL+ Q.C]: Im Busy!");
        added |= addIfMissing(macros, "Item Link", Keybind.fromKeys(GLFW_KEY_X, GLFW_MOD_CONTROL), "[item]");
        added |= addIfMissing(macros, "Group", Keybind.fromKeys(GLFW_KEY_BACKSLASH, 0), "/group");
        added |= addIfMissing(macros, "Party", Keybind.fromKeys(GLFW_KEY_RIGHT_BRACKET, 0), "/party");
        added |= addIfMissing(macros, "Chat Public", Keybind.fromKeys(GLFW_KEY_DELETE, GLFW_MOD_CONTROL), "/chat public");
        added |= addIfMissing(macros, "Checkpoint", Keybind.fromKeys(GLFW_KEY_HOME, GLFW_MOD_CONTROL), "/checkpoint");
        added |= addIfMissing(macros, "Chat Guild", Keybind.fromKeys(GLFW_KEY_PAGE_UP, GLFW_MOD_CONTROL), "/chat guild");
        added |= addIfMissing(macros, "Chat Group", Keybind.fromKeys(GLFW_KEY_PAGE_DOWN, GLFW_MOD_CONTROL), "/chat group");
        added |= addIfMissing(macros, "Chat Party", Keybind.fromKeys(GLFW_KEY_END, GLFW_MOD_CONTROL), "/chat party");
        added |= addIfMissing(macros, "Spawn", Keybind.fromKeys(GLFW_KEY_INSERT, GLFW_MOD_CONTROL), "/spawn");

        if (added) MeteorClient.LOG.info("Seeded default macros from Chat Tools's old keybinds.");
    }

    private static boolean addIfMissing(Macros macros, String name, Keybind keybind, String message) {
        if (macros.get(name) != null) return false;

        Macro macro = new Macro();
        macro.name.set(name);
        macro.keybind.set(keybind);
        macro.messages.set(List.of(message));

        macros.add(macro);
        return true;
    }
}
