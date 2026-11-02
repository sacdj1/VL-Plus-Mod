/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient;

import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.DiscordPresence;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.meteorclient.utils.ReflectInit;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.VLSounds;
import meteordevelopment.meteorclient.utils.misc.Version;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.misc.input.KeyBinds;
import meteordevelopment.meteorclient.utils.network.OnlinePlayers;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.invoke.MethodHandles;

public class MeteorClient implements ClientModInitializer {
    public static final String MOD_ID = "meteor-client";
    public static final ModMetadata MOD_META;
    public static final String NAME;
    public static final  Version VERSION;
    public static final  String DEV_BUILD;

    public static MeteorClient INSTANCE;
    public static MeteorAddon ADDON;

    public static MinecraftClient mc;
    public static final IEventBus EVENT_BUS = new EventBus();
    public static final File FOLDER = FabricLoader.getInstance().getConfigDir().resolve("vlplus").toFile();
    public static final Logger LOG;

    static {
        MOD_META = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();

        NAME = MOD_META.getName();
        LOG = LoggerFactory.getLogger(NAME);

        String versionString = MOD_META.getVersion().getFriendlyString();
        if (versionString.contains("-")) versionString = versionString.split("-")[0];

        // When building and running through IntelliJ and not Gradle it doesn't replace the version so just use a dummy
        if (versionString.equals("${version}")) versionString = "0.0.0";

        VERSION = new Version(versionString);
        DEV_BUILD = MOD_META.getCustomValue(MeteorClient.MOD_ID + ":devbuild").getAsString();
    }

    @Override
    public void onInitializeClient() {
        if (INSTANCE == null) {
            INSTANCE = this;

            // Registry entries must be added on this real, early Fabric entrypoint call - the
            // rest of init below runs from a much later mixin hook inside MinecraftClient's own
            // constructor (see the no-op-first-call pattern here), by which point vanilla's
            // registries are already frozen and Registry.register throws IllegalStateException.
            VLSounds.init();

            return;
        }

        LOG.info("Initializing {}", NAME);

        // Global minecraft client accessor
        mc = MinecraftClient.getInstance();

        // Pre-load
        if (!FOLDER.exists()) {
            FOLDER.getParentFile().mkdirs();
            FOLDER.mkdir();
            Systems.addPreLoadTask(() -> Modules.get().get(DiscordPresence.class).toggle());
        }

        // Register addons
        AddonManager.init();

        // Register event handlers
        AddonManager.ADDONS.forEach(addon -> {
            try {
                EVENT_BUS.registerLambdaFactory(addon.getPackage(), (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));
            } catch (AbstractMethodError e) {
                throw new RuntimeException("Addon \"%s\" is too old and cannot be ran.".formatted(addon.name), e);
            }
        });

        // Register init classes
        ReflectInit.registerPackages();

        // Pre init
        ReflectInit.init(PreInit.class);

        // Register module categories
        Categories.init();

        // Load systems
        Systems.init();

        // Subscribe after systems are loaded
        EVENT_BUS.subscribe(this);

        // Initialise addons
        AddonManager.ADDONS.forEach(MeteorAddon::onInitialize);

        // Sort modules after addons have added their own
        Modules.get().sortModules();

        // Load configs
        Systems.load();

        // Chat Tools -> VL+ macros, one-time-per-name (see DefaultMacros)
        meteordevelopment.meteorclient.systems.macros.DefaultMacros.seed();

        // Post init
        ReflectInit.init(PostInit.class);

        // Force these specific classes to load now, synchronously, while nothing else is
        // concurrently touching the mod jar - HudEditorScreen/its SelectionBox/Snapper are only
        // ever lazily loaded on first use (opening the HUD editor, then dragging an element), which
        // in practice tends to land right when resource-pack loading is ALSO reading from the same
        // jar on another thread, hitting a known upstream Fabric Loader thread-safety bug
        // (ZipException "invalid LOC header" from two threads sharing one ZipFile handle) and
        // crashing. Loading them here instead, at a quiet moment during startup, avoids that race
        // window entirely for the rest of the session - it can't fix the underlying Loader bug, but
        // it means these specific classes are never the ones to trigger it later.
        preloadRacyClasses();

        // Save on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            OnlinePlayers.leave();
            Systems.save();
            GuiThemes.save();
        }));
    }

    private static final String[] RACY_CLASSES = {
        "meteordevelopment.meteorclient.utils.other.Snapper",
        "meteordevelopment.meteorclient.utils.other.Snapper$Container",
        "meteordevelopment.meteorclient.utils.other.Snapper$Element",
        "meteordevelopment.meteorclient.utils.other.Snapper$Direction",
        "meteordevelopment.meteorclient.systems.hud.screens.HudEditorScreen",
        "meteordevelopment.meteorclient.systems.hud.screens.HudEditorScreen$SelectionBox",
        "meteordevelopment.meteorclient.systems.hud.elements.DpsHud$DamageEvent",
        "meteordevelopment.meteorclient.systems.hud.elements.DpsHud$Mode"
    };

    private void preloadRacyClasses() {
        for (String name : RACY_CLASSES) {
            try {
                Class.forName(name, true, MeteorClient.class.getClassLoader());
            } catch (Throwable t) {
                LOG.warn("Failed to preload class {} (harmless - just means the race window this was meant to avoid is still open for it).", name, t);
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.currentScreen == null && mc.getOverlay() == null && KeyBinds.OPEN_COMMANDS.wasPressed()) {
            mc.setScreen(new ChatScreen(Config.get().prefix.get()));
        }
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (event.action == KeyAction.Press && KeyBinds.OPEN_GUI.matchesKey(event.key, 0)) {
            toggleGui();
        }
    }

    @EventHandler
    private void onMouseButton(MouseButtonEvent event) {
        if (event.action == KeyAction.Press && KeyBinds.OPEN_GUI.matchesMouse(event.button)) {
            toggleGui();
        }
    }

    private void toggleGui() {
        if (Utils.canCloseGui()) mc.currentScreen.close();
        else if (Utils.canOpenGui()) Tabs.get().getFirst().openScreen(GuiThemes.get());
    }

    // Hide HUD

    private boolean wasWidgetScreen, wasHudHiddenRoot;

    @EventHandler(priority = EventPriority.LOWEST)
    private void onOpenScreen(OpenScreenEvent event) {
        boolean hideHud = GuiThemes.get().hideHUD();

        if (hideHud) {
            if (!wasWidgetScreen) wasHudHiddenRoot = mc.options.hudHidden;

            if (event.screen instanceof WidgetScreen) mc.options.hudHidden = true;
            else if (!wasHudHiddenRoot) mc.options.hudHidden = false;
        }

        wasWidgetScreen = event.screen instanceof WidgetScreen;
    }

    public static Identifier identifier(String path) {
        return Identifier.of(MeteorClient.MOD_ID, path);
    }
}
