/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

//Created by squidoodly

import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.screens.settings.NamedAreaListScreen;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.starscript.Script;
import meteordevelopment.starscript.value.Value;
import meteordevelopment.starscript.value.ValueMap;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.*;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.world.*;
import net.minecraft.client.realms.gui.screen.RealmsScreen;
import net.minecraft.util.Pair;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

public class DiscordPresence extends Module {
    public enum SelectMode {
        Random,
        Sequential
    }

    private final SettingGroup sgLine1 = settings.createGroup("Line 1");
    private final SettingGroup sgLine2 = settings.createGroup("Line 2");

    // Line 1

    private final Setting<List<String>> line1Strings = sgLine1.add(new StringListSetting.Builder()
        .name("line-1-messages")
        .description("Messages used for the first line - the one Discord actually shows at a glance (member list, etc.) before someone clicks in for the full card. {player.activity} is a guess at what you're currently doing (\"Fighting X\"/\"Mining X\"/plain \"Exploring\" - deliberately not biome-based, not meaningful for Ventureland); {zone.current} is whichever named area (see Zones below) your position falls inside, once you've plotted some.")
        .defaultValue("{player.activity}")
        .onChanged(strings -> recompileLine1())
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );

    private final Setting<Integer> line1UpdateDelay = sgLine1.add(new IntSetting.Builder()
        .name("line-1-update-delay")
        .description("How fast to update the first line in ticks.")
        .defaultValue(200)
        .min(10)
        .sliderRange(10, 200)
        .build()
    );

    private final Setting<SelectMode> line1SelectMode = sgLine1.add(new EnumSetting.Builder<SelectMode>()
        .name("line-1-select-mode")
        .description("How to select messages for the first line.")
        .defaultValue(SelectMode.Sequential)
        .build()
    );

    // Line 2

    private final Setting<List<String>> line2Strings = sgLine2.add(new StringListSetting.Builder()
        .name("line-2-messages")
        .description("Messages used for the second line. {player.activity} is a guess at what you're currently doing - \"Fighting X\" (recently attacked/been hit), \"Mining X\", or plain \"Exploring\" as the default. {server.real_player_count}/{server.real_max_player_count} come from a status ping (the same one the Multiplayer server list uses for its \"X/Y players\" line), not the tab list - the tab list's own {server.player_count} can be padded with non-real entries, so this is the one that's actually meaningful here.")
        .defaultValue("{player.activity}", "{server.real_player_count} Players", "{round(server.tps, 1)} TPS", "{server.bossbar}")
        .onChanged(strings -> recompileLine2())
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );

    private final Setting<Integer> line2UpdateDelay = sgLine2.add(new IntSetting.Builder()
        .name("line-2-update-delay")
        .description("How fast to update the second line in ticks.")
        .defaultValue(60)
        .min(10)
        .sliderRange(10, 200)
        .build()
    );

    private final Setting<SelectMode> line2SelectMode = sgLine2.add(new EnumSetting.Builder<SelectMode>()
        .name("line-2-select-mode")
        .description("How to select messages for the second line.")
        .defaultValue(SelectMode.Sequential)
        .build()
    );

    // Zones - exposes {zone.current} to Line 1/2's own Starscript templates above, rather than
    // being its own fixed on/off toggle. Whether (and where) it actually shows up in your presence
    // is entirely up to whether you put {zone.current} in a template - same as every other
    // Starscript value here (TPS, boss bar, activity).

    private final SettingGroup sgZones = settings.createGroup("Zones");

    private final Setting<List<NamedArea>> areas = sgZones.add(new NamedAreaListSetting.Builder()
        .name("named-areas")
        .description("X/Z box areas for {zone.current} below - see Manage Areas.")
        .build()
    );

    private final Setting<String> defaultAreaName = sgZones.add(new StringSetting.Builder()
        .name("default-area-name")
        .description("{zone.current}'s value while you're not inside any area below - Ventureland's own zones are numbered 0-25, so this is deliberately NOT \"Area 0\" to avoid colliding with the real one.")
        .defaultValue("Unknown Area")
        .build()
    );

    private final Setting<Void> manageAreas = sgZones.add(new ButtonSetting.Builder()
        .name("manage-areas")
        .description("Add/edit/remove the named areas above.")
        .buttonText("Manage Areas")
        .screen(theme -> new NamedAreaListScreen(theme, areas))
        .build()
    );

    // Same idea as Ambience's own Visualize Regions - a plain full-height wireframe outline per
    // box, not that system's chunked/FOV-culled real-boundary scan (overkill for a flat X/Z
    // rectangle that doesn't even care about Y) - just enough to see where a zone's boxes actually
    // sit while you're placing/checking them.

    private final Setting<Boolean> visualizeZones = sgZones.add(new BoolSetting.Builder()
        .name("visualize-zones")
        .description("Draws a wireframe outline for every box of every Visualize-enabled area above, spanning the full world height.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> visualizeLineColor = sgZones.add(new ColorSetting.Builder()
        .name("visualize-line-color")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(visualizeZones::get)
        .build()
    );

    private final Setting<SettingColor> visualizeSideColor = sgZones.add(new ColorSetting.Builder()
        .name("visualize-side-color")
        .defaultValue(new SettingColor(255, 255, 255, 20))
        .visible(visualizeZones::get)
        .build()
    );

    private final Setting<ShapeMode> visualizeShapeMode = sgZones.add(new EnumSetting.Builder<ShapeMode>()
        .name("visualize-shape-mode")
        .defaultValue(ShapeMode.Both)
        .visible(visualizeZones::get)
        .build()
    );

    private static final RichPresence rpc = new RichPresence();
    private SmallImage currentSmallImage;
    private int ticks;
    private boolean forceUpdate, lastWasInMainMenu;

    private final List<Script> line1Scripts = new ArrayList<>();
    private int line1Ticks, line1I;

    private final List<Script> line2Scripts = new ArrayList<>();
    private int line2Ticks, line2I;

    public static final List<Pair<String, String>> customStates = new ArrayList<>();

    static {
        registerCustomState("com.terraformersmc.modmenu.gui", "Browsing mods");
        registerCustomState("me.jellysquid.mods.sodium.client", "Changing options");
    }

    private static DiscordPresence instance;

    public DiscordPresence() {
        super(Categories.Misc, "discord-presence", "Displays VL+ as your presence on Discord.");

        runInMainMenu = true;
        instance = this;

        // Registered here rather than in MeteorStarscript itself - zone.current is specifically a
        // Discord Presence concept (its areas are only ever configured through this module), not a
        // general-purpose value every other consumer of Starscript needs. Available to any template
        // regardless of whether this module is currently active, same as every other value already
        // registered in MeteorStarscript's own init().
        MeteorStarscript.ss.set("zone", new ValueMap().set("current", DiscordPresence::currentAreaName));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!visualizeZones.get() || MeteorClient.mc.world == null) return;

        double minY = MeteorClient.mc.world.getBottomY(), maxY = MeteorClient.mc.world.getTopY();

        for (NamedArea area : areas.get()) {
            if (!area.visualize) continue;

            for (NamedArea.Box box : area.boxes) {
                event.renderer.box(
                    Math.min(box.x1, box.x2), minY, Math.min(box.z1, box.z2),
                    Math.max(box.x1, box.x2) + 1, maxY, Math.max(box.z1, box.z2) + 1,
                    visualizeSideColor.get(), visualizeLineColor.get(), visualizeShapeMode.get(), 0
                );
            }
        }
    }

    private static Value currentAreaName() {
        if (MeteorClient.mc.player == null) return Value.string("");

        int x = MeteorClient.mc.player.getBlockX(), z = MeteorClient.mc.player.getBlockZ();
        for (NamedArea area : instance.areas.get()) {
            if (area.contains(x, z)) return Value.string(area.name);
        }

        return Value.string(instance.defaultAreaName.get());
    }

    /** Registers a custom state to be used when the current screen is a class in the specified package. */
    public static void registerCustomState(String packageName, String state) {
        for (var pair : customStates) {
            if (pair.getLeft().equals(packageName)) {
                pair.setRight(state);
                return;
            }
        }

        customStates.add(new Pair<>(packageName, state));
    }

    /** The package name must match exactly to the one provided through {@link #registerCustomState(String, String)}. */
    public static void unregisterCustomState(String packageName) {
        customStates.removeIf(pair -> pair.getLeft().equals(packageName));
    }

    @Override
    public void onActivate() {
        DiscordIPC.start(1545178024419397722L, null);

        rpc.setStart(System.currentTimeMillis() / 1000L);

        String largeText = "%s %s".formatted(MeteorClient.NAME, MeteorClient.VERSION);
        if (!MeteorClient.DEV_BUILD.isEmpty()) largeText += " Dev Build: " + MeteorClient.DEV_BUILD;
        rpc.setLargeImage("vlplus_icon", largeText);

        currentSmallImage = SmallImage.Snail;

        recompileLine1();
        recompileLine2();

        ticks = 0;
        line1Ticks = 0;
        line2Ticks = 0;
        lastWasInMainMenu = false;

        line1I = 0;
        line2I = 0;
    }

    @Override
    public void onDeactivate() {
        DiscordIPC.stop();
    }

    private void recompile(List<String> messages, List<Script> scripts) {
        scripts.clear();

        for (String message : messages) {
            Script script = MeteorStarscript.compile(message);
            if (script != null) scripts.add(script);
        }

        forceUpdate = true;
    }

    private void recompileLine1() {
        recompile(line1Strings.get(), line1Scripts);
    }

    private void recompileLine2() {
        recompile(line2Strings.get(), line2Scripts);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean update = false;

        // Image
        if (ticks >= 200 || forceUpdate) {
            currentSmallImage = currentSmallImage.next();
            currentSmallImage.apply();
            update = true;

            ticks = 0;
        }
        else ticks++;

        if (Utils.canUpdate()) {
            // Line 1
            if (line1Ticks >= line1UpdateDelay.get() || forceUpdate) {
                if (!line1Scripts.isEmpty()) {
                    int i = Utils.random(0, line1Scripts.size());
                    if (line1SelectMode.get() == SelectMode.Sequential) {
                        if (line1I >= line1Scripts.size()) line1I = 0;
                        i = line1I++;
                    }

                    String message = MeteorStarscript.run(line1Scripts.get(i));
                    if (message != null) rpc.setDetails(message);
                }
                update = true;

                line1Ticks = 0;
            } else line1Ticks++;

            // Line 2
            if (line2Ticks >= line2UpdateDelay.get() || forceUpdate) {
                if (!line2Scripts.isEmpty()) {
                    int i = Utils.random(0, line2Scripts.size());
                    if (line2SelectMode.get() == SelectMode.Sequential) {
                        if (line2I >= line2Scripts.size()) line2I = 0;
                        i = line2I++;
                    }

                    String message = MeteorStarscript.run(line2Scripts.get(i));
                    if (message != null) rpc.setState(message);
                }
                update = true;

                line2Ticks = 0;
            } else line2Ticks++;
        }
        else {
            if (!lastWasInMainMenu) {
                rpc.setDetails(MeteorClient.NAME + " " + (MeteorClient.DEV_BUILD.isEmpty() ? MeteorClient.VERSION : MeteorClient.VERSION + " " + MeteorClient.DEV_BUILD));

                if (mc.currentScreen instanceof TitleScreen) rpc.setState("Looking at title screen");
                else if (mc.currentScreen instanceof SelectWorldScreen) rpc.setState("Selecting world");
                else if (mc.currentScreen instanceof CreateWorldScreen || mc.currentScreen instanceof EditGameRulesScreen) rpc.setState("Creating world");
                else if (mc.currentScreen instanceof EditWorldScreen) rpc.setState("Editing world");
                else if (mc.currentScreen instanceof LevelLoadingScreen) rpc.setState("Loading world");
                else if (mc.currentScreen instanceof MultiplayerScreen) rpc.setState("Selecting server");
                else if (mc.currentScreen instanceof AddServerScreen) rpc.setState("Adding server");
                else if (mc.currentScreen instanceof ConnectScreen || mc.currentScreen instanceof DirectConnectScreen) rpc.setState("Connecting to server");
                else if (mc.currentScreen instanceof WidgetScreen) rpc.setState("Browsing VL+'s GUI");
                else if (mc.currentScreen instanceof OptionsScreen || mc.currentScreen instanceof SkinOptionsScreen || mc.currentScreen instanceof SoundOptionsScreen || mc.currentScreen instanceof VideoOptionsScreen || mc.currentScreen instanceof ControlsOptionsScreen || mc.currentScreen instanceof LanguageOptionsScreen || mc.currentScreen instanceof ChatOptionsScreen || mc.currentScreen instanceof PackScreen || mc.currentScreen instanceof AccessibilityOptionsScreen) rpc.setState("Changing options");
                else if (mc.currentScreen instanceof CreditsScreen) rpc.setState("Reading credits");
                else if (mc.currentScreen instanceof RealmsScreen) rpc.setState("Browsing Realms");
                else {
                    boolean setState = false;
                    if (mc.currentScreen != null) {
                        String className = mc.currentScreen.getClass().getName();
                        for (var pair : customStates) {
                            if (className.startsWith(pair.getLeft())) {
                                rpc.setState(pair.getRight());
                                setState = true;
                                break;
                            }
                        }
                    }
                    if (!setState) rpc.setState("In main menu");
                }

                update = true;
            }
        }

        // Update
        if (update) DiscordIPC.setActivity(rpc);
        forceUpdate = false;
        lastWasInMainMenu = !Utils.canUpdate();
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!Utils.canUpdate()) lastWasInMainMenu = false;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton help = theme.button("Open documentation.");
        help.action = () -> Util.getOperatingSystem().open("https://github.com/MeteorDevelopment/meteor-client/wiki/Starscript");

        return help;
    }

    private enum SmallImage {
        MineGame("minegame", "MineGame159"),
        Snail("seasnail", "seasnail8169");

        private final String key, text;

        SmallImage(String key, String text) {
            this.key = key;
            this.text = text;
        }

        void apply() {
            rpc.setSmallImage(key, text);
        }

        SmallImage next() {
            if (this == MineGame) return Snail;
            return MineGame;
        }
    }
}
