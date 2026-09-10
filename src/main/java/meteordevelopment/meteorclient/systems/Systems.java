/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.accounts.Accounts;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.macros.Macros;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.profiles.Profiles;
import meteordevelopment.meteorclient.systems.proxies.Proxies;
import meteordevelopment.meteorclient.systems.vlitems.VLItems;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.NbtCompound;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Systems {
    @SuppressWarnings("rawtypes")
    private static final Map<Class<? extends System>, System<?>> systems = new Reference2ReferenceOpenHashMap<>();
    private static final List<Runnable> preLoadTasks = new ArrayList<>(1);

    public static void addPreLoadTask(Runnable task) {
        preLoadTasks.add(task);
    }

    public static void init() {
        Config config = new Config();
        System<?> configSystem = add(config);
        configSystem.init();
        configSystem.load();

        // Registers the colors from config tab. This allows rainbow colours to work for friends.
        config.settings.registerColorSettings(null);

        add(new Modules());
        add(new Macros());
        add(new Friends());
        add(new Accounts());
        add(new Waypoints());
        add(new Profiles());
        add(new Proxies());
        add(new Hud());
        add(new VLItems());

        MeteorClient.EVENT_BUS.subscribe(Systems.class);
    }

    private static System<?> add(System<?> system) {
        systems.put(system.getClass(), system);
        MeteorClient.EVENT_BUS.subscribe(system);
        system.init();

        return system;
    }

    // save/load

    @EventHandler
    private static void onGameLeft(GameLeftEvent event) {
        save();
    }

    // Autosave every 5 minutes (6000 ticks), on top of the shutdown-hook/GameLeftEvent saves -
    // the only two save points before this, both of which get skipped entirely by a hard/forced
    // exit or a fatal crash (confirmed happening repeatedly on this modpack, unrelated to VL+ -
    // an upstream Fabric Loader classloading race). Without this, a single crash mid-session lost
    // every setting changed since the last clean shutdown, no matter how long ago that was.
    private static final int AUTOSAVE_INTERVAL_TICKS = 6000;
    private static int autosaveTicks;

    @EventHandler
    private static void onTick(TickEvent.Post event) {
        if (++autosaveTicks < AUTOSAVE_INTERVAL_TICKS) return;
        autosaveTicks = 0;

        save();
    }

    public static void save(File folder) {
        long start = java.lang.System.currentTimeMillis();
        MeteorClient.LOG.info("Saving");

        // Isolated per-system: one system throwing (e.g. mid-save class-loading hiccup) used to
        // abort the whole loop, silently skipping every system after it in iteration order -
        // reported as settings reverting to defaults on the next launch, since only some of the
        // .nbt files actually got written.
        for (System<?> system : systems.values()) {
            try {
                system.save(folder);
            } catch (Throwable t) {
                MeteorClient.LOG.error("Failed to save system '{}'.", system.getName(), t);
            }
        }

        MeteorClient.LOG.info("Saved in {} milliseconds.", java.lang.System.currentTimeMillis() - start);
    }

    public static void save() {
        save(null);
    }

    public static void load(File folder) {
        long start = java.lang.System.currentTimeMillis();
        MeteorClient.LOG.info("Loading");

        for (Runnable task : preLoadTasks) task.run();

        // See save() above - same isolation, same reasoning.
        for (System<?> system : systems.values()) {
            try {
                system.load(folder);
            } catch (Throwable t) {
                MeteorClient.LOG.error("Failed to load system '{}'.", system.getName(), t);
            }
        }

        MeteorClient.LOG.info("Loaded in {} milliseconds", java.lang.System.currentTimeMillis() - start);
    }

    public static void load() {
        load(null);
    }

    @SuppressWarnings("unchecked")
    public static <T extends System<?>> T get(Class<T> klass) {
        return (T) systems.get(klass);
    }

    // Whole-config clipboard (every system in one blob) - see per-system toClipboard/fromClipboard
    // in NbtUtils for the single-system equivalent already used by ConfigTab/GuiTab/HudTab/etc.
    // Isolated per-system on paste too, same reasoning as load() above - one bad/foreign system
    // entry shouldn't block every other system in the same paste from applying.

    private static final String CLIPBOARD_NAME = "vlplus-full-config";

    public static boolean toClipboard() {
        NbtCompound tag = new NbtCompound();
        tag.putString("name", CLIPBOARD_NAME);

        for (System<?> system : systems.values()) {
            NbtCompound sysTag = system.toTag();
            if (sysTag != null) tag.put(system.getName(), sysTag);
        }

        return NbtUtils.toClipboard(CLIPBOARD_NAME, tag);
    }

    public static boolean fromClipboard() {
        NbtCompound schema = new NbtCompound();
        schema.putString("name", CLIPBOARD_NAME);

        NbtCompound pasted = NbtUtils.fromClipboard(schema);
        if (pasted == null) return false;

        for (System<?> system : systems.values()) {
            if (!pasted.contains(system.getName())) continue;

            try {
                system.fromTag(pasted.getCompound(system.getName()));
            } catch (Throwable t) {
                MeteorClient.LOG.error("Failed to paste system '{}'.", system.getName(), t);
            }
        }

        return true;
    }
}
