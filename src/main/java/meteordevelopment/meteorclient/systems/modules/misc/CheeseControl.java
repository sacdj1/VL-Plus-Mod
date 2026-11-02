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

/**
 * Control panel for the admin-only (Cheese) features of other modules: one switch per feature, grouped by
 * what they belong to, all on by default. The switches apply whether or not this module itself is switched
 * on - it's just where they live - so turning one off always takes effect immediately.
 */
public class CheeseControl extends Module {
    private final SettingGroup sgBones = settings.createGroup("Bones");
    private final SettingGroup sgPirates = settings.createGroup("Suicide Pirates");
    private final SettingGroup sgSections = settings.createGroup("Visualizer Sections");

    private final Setting<Boolean> secBones = sgSections.add(new BoolSetting.Builder().name("section-bones-ring").description("Visualizer: the ring following Bones's angry particles. Off hides its settings and turns it off.").defaultValue(true).build());
    private final Setting<Boolean> secBonesRings = sgSections.add(new BoolSetting.Builder().name("section-bones-rings").description("Visualizer: the constant Bones rings (aggro, cyclone, teleport), the teleport path and the Suicide Pirate rings (admin-only, needs .Admin). Off hides their settings and turns them off.").defaultValue(true).build());
    private final Setting<Boolean> secLava = sgSections.add(new BoolSetting.Builder().name("section-lava-floor").description("Visualizer: the lava-floor safe-edge marker.").defaultValue(true).build());
    private final Setting<Boolean> secFire = sgSections.add(new BoolSetting.Builder().name("section-fire-floor").description("Visualizer: the fire-floor safe-edge marker.").defaultValue(true).build());
    private final Setting<Boolean> secCgAoe = sgSections.add(new BoolSetting.Builder().name("section-cg-aoe").description("Visualizer: the Cosmic Gatekeeper's area-attack ring.").defaultValue(true).build());
    private final Setting<Boolean> secCgBomb = sgSections.add(new BoolSetting.Builder().name("section-cg-bombs").description("Visualizer: the Cosmic Gatekeeper's bombs.").defaultValue(true).build());
    private final Setting<Boolean> secPulsar = sgSections.add(new BoolSetting.Builder().name("section-pulsars").description("Visualizer: the Pulsar ring.").defaultValue(true).build());
    private final Setting<Boolean> secAegis = sgSections.add(new BoolSetting.Builder().name("section-aegis").description("Visualizer: the Aegis marker.").defaultValue(true).build());
    private final Setting<Boolean> secReaper = sgSections.add(new BoolSetting.Builder().name("section-reaper").description("Visualizer: the Reaper marker.").defaultValue(true).build());

    private final Setting<Boolean> autoEnable = sgBones.add(new BoolSetting.Builder()
        .name("auto-enable")
        .description("Switch the Visualizer on by itself whenever you unlock Cheese (.Admin) and each time you join a world. Turn it off if you'd rather enable it by hand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> bonesAggro = sgBones.add(new BoolSetting.Builder()
        .name("aggro-ring")
        .description("Visualizer > Bones rings: the ring showing how close you can get before Bones aggros.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> bonesCyclone = sgBones.add(new BoolSetting.Builder()
        .name("cyclone-ring")
        .description("Visualizer > Bones rings: the ring showing how far his cyclone attack reaches.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> bonesTeleport = sgBones.add(new BoolSetting.Builder()
        .name("teleport-ring")
        .description("Visualizer > Bones rings: the ring showing how far you can get before he teleports you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> bonesTpPath = sgBones.add(new BoolSetting.Builder()
        .name("teleport-path")
        .description("Visualizer > Bones rings: the markers showing where he teleported from and to.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> bonesWarning = sgBones.add(new BoolSetting.Builder()
        .name("bones-warning-hud")
        .description("The Bones Warning HUD element (and the distance it shows).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pirateRings = sgPirates.add(new BoolSetting.Builder()
        .name("pirate-explosion-rings")
        .description("Visualizer > Bones rings: the explosion-radius ring around each Suicide Pirate.")
        .defaultValue(true)
        .build()
    );

    public CheeseControl() {
        super(Categories.Cheese, "cheese-control", "Switches for the sections of the Visualizer (and the admin-only Bones rings inside it): each one off hides its settings and turns it off. All on by default; they apply whether or not this module is on.", "admin-control", "bones-control");
        adminOnly = true;
    }

    private static CheeseControl get() {
        Modules modules = Modules.get();
        return modules != null ? modules.get(CheeseControl.class) : null;
    }

    public static boolean sectionBones() { CheeseControl c = get(); return c == null || c.secBones.get(); }
    public static boolean sectionBonesRings() { CheeseControl c = get(); return c == null || c.secBonesRings.get(); }
    public static boolean sectionLava() { CheeseControl c = get(); return c == null || c.secLava.get(); }
    public static boolean sectionFire() { CheeseControl c = get(); return c == null || c.secFire.get(); }
    public static boolean sectionCgAoe() { CheeseControl c = get(); return c == null || c.secCgAoe.get(); }
    public static boolean sectionCgBombs() { CheeseControl c = get(); return c == null || c.secCgBomb.get(); }
    public static boolean sectionPulsars() { CheeseControl c = get(); return c == null || c.secPulsar.get(); }
    public static boolean sectionAegis() { CheeseControl c = get(); return c == null || c.secAegis.get(); }
    public static boolean sectionReaper() { CheeseControl c = get(); return c == null || c.secReaper.get(); }
    public static boolean autoEnable() { CheeseControl c = get(); return c == null || c.autoEnable.get(); }
    public static boolean aggroRing() { CheeseControl c = get(); return c == null || c.bonesAggro.get(); }
    public static boolean cycloneRing() { CheeseControl c = get(); return c == null || c.bonesCyclone.get(); }
    public static boolean teleportRing() { CheeseControl c = get(); return c == null || c.bonesTeleport.get(); }
    public static boolean teleportPath() { CheeseControl c = get(); return c == null || c.bonesTpPath.get(); }
    public static boolean warningHud() { CheeseControl c = get(); return c == null || c.bonesWarning.get(); }
    public static boolean pirateRings() { CheeseControl c = get(); return c == null || c.pirateRings.get(); }
}
