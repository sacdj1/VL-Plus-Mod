/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

/**
 * A single place other modules' optional diagnostic chat messages (the "why isn't this working"
 * kind, not real feature output) check before printing - hidden (needs .dev) and everything in it
 * defaults off, so a normal user never sees a module's internal troubleshooting output, but it's
 * still there, one setting flip away, next time something needs diagnosing instead of a whole new
 * round of temporary logging getting added and then torn out again.
 */
public class DebugLog extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> particleRecolor = sgGeneral.add(new BoolSetting.Builder()
        .name("particle-adjust-recolor")
        .description("Particle Adjust: tells you in chat, once per particle type (capped), when it recolors something - which mode/color was used. For checking whether Hit Detection is catching the particles you expect.")
        .defaultValue(false)
        .build()
    );

    public DebugLog() {
        super(Categories.Debug, "debug-log", "Switches for other modules' optional troubleshooting chat messages - all off by default, this module itself has to be on AND .dev unlocked for any of them to print.", "verbose-log", "troubleshooting");
        hidden = true;
    }

    private static DebugLog get() {
        Modules modules = Modules.get();
        return modules != null ? modules.get(DebugLog.class) : null;
    }

    /** True only while .dev is unlocked AND this module (and the specific switch passed) are both on - the gate every other module's optional debug output should check before printing anything. */
    private static boolean enabled(java.util.function.Predicate<DebugLog> which) {
        Modules modules = Modules.get();
        if (modules == null || !modules.isDevUnlocked()) return false;

        DebugLog log = get();
        return log != null && log.isActive() && which.test(log);
    }

    public static boolean particleRecolorEnabled() {
        return enabled(log -> log.particleRecolor.get());
    }

    public static void log(String message, Object... args) {
        ChatUtils.infoPrefix("Debug", message, args);
    }
}
