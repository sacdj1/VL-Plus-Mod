/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.MeteorClient;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.CheeseControl;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Draws overlays for things you can't otherwise see. Currently: a ring that follows Bones's "angry
 * particle" ring - built from the actual particles the server sends (their centre and average
 * radius), so it shows exactly where the attack is, wherever it lands, rather than a fixed circle
 * on the boss. The constant always-on Bones rings live in the admin-only Bones Rings module instead.
 */
public class Visualizer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAppearance = settings.createGroup("Ring Appearance");

    private final Setting<Boolean> seeThrough = sgAppearance.add(new BoolSetting.Builder()
        .name("rings-see-through")
        .description("Rings don't hide each other: you can see one ring through another. Off = the old behavior, a ring in front hides any ring behind it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> blockingRings = sgAppearance.add(new StringListSetting.Builder()
        .name("rings-that-block")
        .description("With See Through on: these rings still hide whatever is behind them (one per line: bones, bones-rings, cg-aoe, bombs, pulsar, aegis, reaper, amal-adds, obelisk). Leave empty for every ring to be see-through.")
        .defaultValue(new ArrayList<>())
        .visible(seeThrough::get)
        .build()
    );

    private final Setting<Boolean> bonesRingEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("bones-ring")
        .description("Bones: the ring that follows his angry-particle ring (teleports and other attacks). Turn off to hide it without turning the rest of the Visualizer off.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> particleType = sgGeneral.add(new StringSetting.Builder()
        .name("particle-type")
        .description("The particle the ring of angry particles is made of (registry id) - vanilla's angry villager particle by default. Change it if the server uses a different one.")
        .defaultValue("minecraft:angry_villager")
        .build()
    );

    // This particle type isn't exclusive to Bones - confirmed also used by the player's own Vacuum
    // ability and by "elite" entities' ground pound attack, both of which can spawn it well away
    // from Bones himself and corrupt the ring fit with unrelated points. Gated by distance to the
    // nearest "Bones"-named entity instead of accepting every matching particle in the world.

    private final Setting<String> bonesName = sgGeneral.add(new StringSetting.Builder()
        .name("bones-name")
        .description("Text (case-insensitive) Bones's own name must contain, used to find his position and ignore angry-particles that aren't actually from him (Vacuum, elite ground pound, ...).")
        .defaultValue("Bones")
        .build()
    );

    private final Setting<Double> bonesParticleRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("bones-particle-range")
        .description("How close one of the angry particles above needs to spawn to Bones's own current position to count toward his ring - keeps Vacuum/ground pound particles elsewhere from being mistaken for his ring.")
        .defaultValue(15.0)
        .min(1)
        .sliderRange(2, 40)
        .build()
    );

    private final Setting<Integer> lifetimeMs = sgGeneral.add(new IntSetting.Builder()
        .name("lifetime")
        .description("How long, in milliseconds, the ring stays drawn after the last of its particles was seen.")
        .defaultValue(1200)
        .range(200, 10000)
        .sliderRange(200, 5000)
        .build()
    );

    private final Setting<Integer> minParticles = sgGeneral.add(new IntSetting.Builder()
        .name("min-particles")
        .description("How many particles have to be seen together before a ring is drawn, so a stray particle doesn't draw one.")
        .defaultValue(6)
        .range(3, 64)
        .sliderRange(3, 32)
        .build()
    );

    private final Setting<Double> minRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-radius")
        .description("Ignores any group of particles whose fitted ring is smaller than this many blocks across-radius - so small unrelated angry-particle clusters (a different attack, a stray puff) don't get mistaken for the ring.")
        .defaultValue(4)
        .min(0.5)
        .sliderRange(1, 30)
        .build()
    );

    private final Setting<Double> maxRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-radius")
        .description("Ignores any group whose fitted ring is bigger than this many blocks.")
        .defaultValue(40)
        .min(2)
        .sliderRange(5, 100)
        .build()
    );

    private final Setting<Integer> burstWindow = sgGeneral.add(new IntSetting.Builder()
        .name("burst-window")
        .description("Only particles seen within this many milliseconds of the newest one are used together to fit a ring - one attack's particles arrive close together, so this keeps separate attacks (or an old ring and a new one) from blending.")
        .defaultValue(400)
        .range(50, 3000)
        .sliderRange(100, 1500)
        .build()
    );

    private final Setting<Double> yOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-offset")
        .description("Shifts the ring up/down from the lowest particle's height.")
        .defaultValue(0)
        .sliderRange(-3, 3)
        .build()
    );

    private final Setting<Double> ringHeight = sgAppearance.add(new DoubleSetting.Builder()
        .name("ring-height")
        .description("How far up into the air the ring's wall reaches.")
        .defaultValue(3)
        .min(0.5)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> segments = sgAppearance.add(new IntSetting.Builder()
        .name("segments")
        .description("How many straight pieces the circle is built from - higher is smoother.")
        .defaultValue(72)
        .range(12, 256)
        .sliderRange(24, 128)
        .build()
    );

    private final Setting<SettingColor> baseColor = sgAppearance.add(new ColorSetting.Builder()
        .name("base-color")
        .description("Color of the ring at ground level.")
        .defaultValue(new SettingColor(255, 130, 20, 170))
        .build()
    );

    private final Setting<SettingColor> topColor = sgAppearance.add(new ColorSetting.Builder()
        .name("top-color")
        .description("Color the ring fades to at the top - use a low alpha to fade out.")
        .defaultValue(new SettingColor(255, 230, 90, 0))
        .build()
    );

    private final Setting<Boolean> baseLine = sgAppearance.add(new BoolSetting.Builder()
        .name("base-line")
        .description("Draws a solid line along the bottom edge of the ring.")
        .defaultValue(true)
        .build()
    );

    // Lava floor

    public enum LavaScope {
        Everywhere,
        BossFights,
        Areas
    }

    private final SettingGroup sgLava = settings.createGroup("Boss Fights: Lava Floor");

    private final Setting<Boolean> lavaEnabled = sgLava.add(new BoolSetting.Builder()
        .name("lava-floor")
        .description("Marks the safe edge of lava.")
        .defaultValue(false)
        .build()
    );

    private final Setting<LavaScope> lavaScope = sgLava.add(new EnumSetting.Builder<LavaScope>()
        .name("lava-scope")
        .description("Everywhere: any lava near you. Boss Fights: only while one of the bosses below is near you (Vulcan Imperator and Volcanic Guardian by default). Areas: only lava inside the areas below.")
        .defaultValue(LavaScope.Everywhere)
        .visible(lavaEnabled::get)
        .build()
    );

    private final Setting<String> lavaBosses = sgLava.add(new StringSetting.Builder()
        .name("lava-bosses")
        .description("Boss names (comma separated, case-insensitive, matches anywhere in the name) that switch Lava Floor on while one is near you.")
        .defaultValue("Vulcan Imperator, Volcanic Guardian")
        .visible(() -> lavaOn() && lavaScope.get() == LavaScope.BossFights)
        .build()
    );

    private final Setting<Double> lavaBossRange = sgLava.add(new DoubleSetting.Builder()
        .name("lava-boss-range")
        .description("How close, in blocks, one of those bosses has to be to count as being in the fight.")
        .defaultValue(60)
        .min(10)
        .sliderRange(20, 150)
        .visible(() -> lavaOn() && lavaScope.get() == LavaScope.BossFights)
        .build()
    );

    private final Setting<Integer> lavaRange = sgLava.add(new IntSetting.Builder()
        .name("lava-range")
        .description("How far around you, in blocks, to look for lava.")
        .defaultValue(20)
        .range(4, 48)
        .sliderRange(8, 40)
        .visible(lavaEnabled::get)
        .build()
    );

    private final Setting<Double> lavaWallHeight = sgLava.add(new DoubleSetting.Builder()
        .name("lava-wall-height")
        .description("How far up the gradient wall reaches above the floor.")
        .defaultValue(1.5)
        .min(0.25)
        .sliderRange(0.5, 4)
        .visible(lavaEnabled::get)
        .build()
    );

    private final Setting<SettingColor> lavaBaseColor = sgLava.add(new ColorSetting.Builder()
        .name("lava-base-color")
        .description("Color of the wall where it meets the floor.")
        .defaultValue(new SettingColor(255, 90, 0, 190))
        .visible(lavaEnabled::get)
        .build()
    );

    private final Setting<SettingColor> lavaTopColor = sgLava.add(new ColorSetting.Builder()
        .name("lava-top-color")
        .description("Color the wall fades to at the top - use a low alpha to fade out.")
        .defaultValue(new SettingColor(255, 220, 60, 0))
        .visible(lavaEnabled::get)
        .build()
    );

    private final Setting<Boolean> lavaSafeTop = sgLava.add(new BoolSetting.Builder()
        .name("highlight-safe-blocks")
        .description("Also tints the top of each safe block that touches lava, so the blocks you can stand on stand out.")
        .defaultValue(true)
        .visible(lavaEnabled::get)
        .build()
    );

    private final Setting<SettingColor> lavaSafeColor = sgLava.add(new ColorSetting.Builder()
        .name("safe-block-color")
        .description("Tint of the safe blocks' tops.")
        .defaultValue(new SettingColor(60, 255, 90, 70))
        .visible(() -> lavaOn() && lavaSafeTop.get())
        .build()
    );

    private final Setting<Boolean> lavaDiagonalsEnabled = sgLava.add(new BoolSetting.Builder()
        .name("diagonals-safe")
        .description("Also marks a corner safe (with its own marker, not a full wall) wherever a lava block's diagonal neighbor is safe floor - the plain wall above only ever checks the 4 orthogonal faces, so a safe tile sitting only at a diagonal corner otherwise gets no marker at all.")
        .defaultValue(false)
        .visible(lavaEnabled::get)
        .build()
    );

    private final Setting<SettingColor> lavaDiagonalColor = sgLava.add(new ColorSetting.Builder()
        .name("diagonal-color")
        .description("Color of diagonal corner markers - deliberately its own setting, not the same as the wall's Base/Top Color, so a corner (not a full wall face) is visually obvious as a different kind of marker.")
        .defaultValue(new SettingColor(255, 170, 0, 190))
        .visible(() -> lavaOn() && lavaDiagonalsEnabled.get())
        .build()
    );

    private final Setting<Integer> lavaRebuildTicks = sgLava.add(new IntSetting.Builder()
        .name("rescan-interval")
        .description("How often, in ticks, the area is rescanned for lava - lower reacts faster to the floor changing, higher is lighter on performance.")
        .defaultValue(4)
        .range(1, 40)
        .sliderRange(1, 20)
        .visible(lavaEnabled::get)
        .build()
    );

    private final SettingGroup sgLavaAreas = settings.createGroup("Boss Fights: Lava Areas");

    private final Setting<Boolean> area1Enabled = sgLavaAreas.add(new BoolSetting.Builder()
        .name("area-1")
        .description("First area (used when Lava Scope is Areas).")
        .defaultValue(false)
        .visible(() -> lavaOn() && lavaScope.get() == LavaScope.Areas)
        .build()
    );

    private final Setting<Integer> area1X = sgLavaAreas.add(new IntSetting.Builder().name("area-1-x").description("Area 1 center X.").defaultValue(0).sliderRange(-3000, 3000).visible(() -> area1Enabled.get() && lavaOn() && lavaScope.get() == LavaScope.Areas).build());
    private final Setting<Integer> area1Z = sgLavaAreas.add(new IntSetting.Builder().name("area-1-z").description("Area 1 center Z.").defaultValue(0).sliderRange(-3000, 3000).visible(() -> area1Enabled.get() && lavaOn() && lavaScope.get() == LavaScope.Areas).build());
    private final Setting<Integer> area1Radius = sgLavaAreas.add(new IntSetting.Builder().name("area-1-radius").description("Area 1 radius, in blocks.").defaultValue(30).min(1).sliderRange(5, 100).visible(() -> area1Enabled.get() && lavaOn() && lavaScope.get() == LavaScope.Areas).build());

    private final Setting<Boolean> area2Enabled = sgLavaAreas.add(new BoolSetting.Builder()
        .name("area-2")
        .description("Second area (used when Lava Scope is Areas).")
        .defaultValue(false)
        .visible(() -> lavaOn() && lavaScope.get() == LavaScope.Areas)
        .build()
    );

    private final Setting<Integer> area2X = sgLavaAreas.add(new IntSetting.Builder().name("area-2-x").description("Area 2 center X.").defaultValue(0).sliderRange(-3000, 3000).visible(() -> area2Enabled.get() && lavaOn() && lavaScope.get() == LavaScope.Areas).build());
    private final Setting<Integer> area2Z = sgLavaAreas.add(new IntSetting.Builder().name("area-2-z").description("Area 2 center Z.").defaultValue(0).sliderRange(-3000, 3000).visible(() -> area2Enabled.get() && lavaOn() && lavaScope.get() == LavaScope.Areas).build());
    private final Setting<Integer> area2Radius = sgLavaAreas.add(new IntSetting.Builder().name("area-2-radius").description("Area 2 radius, in blocks.").defaultValue(30).min(1).sliderRange(5, 100).visible(() -> area2Enabled.get() && lavaOn() && lavaScope.get() == LavaScope.Areas).build());

    private record LavaEdge(int x, int y, int z, Direction dir) {}

    // A diagonal neighbor only ever shares a single corner point with the lava block, never a
    // full face the way an orthogonal neighbor does - dx/dz (each -1 or 1) locate which of the
    // 4 corners of that 1x1 floor tile is the safe one, instead of reusing Direction (which can't
    // represent a diagonal at all).
    private record LavaCorner(int x, int y, int z, int dx, int dz) {}

    private final List<LavaEdge> lavaEdges = new ArrayList<>();
    private final List<LavaCorner> lavaCorners = new ArrayList<>();
    private int lavaTickCounter;
    private final Renderer3D lavaRenderer = new Renderer3D();

    // Fire floor - same idea as Lava Floor above, just for fire/soul fire. Unlike lava (a fluid that
    // occupies the floor tile itself), fire is a non-solid block sitting ON TOP of a normal solid
    // floor, so its scan/safe-neighbour checks are offset by one block versus lava's - see isFire()/
    // isSafeStand() below.

    public enum FireScope {
        Everywhere,
        BossFights,
        Areas
    }

    private final SettingGroup sgFire = settings.createGroup("Boss Fights: Fire Floor");

    private final Setting<Boolean> fireEnabled = sgFire.add(new BoolSetting.Builder()
        .name("fire-floor")
        .description("Marks the safe edge of fire/soul fire.")
        .defaultValue(true)
        .build()
    );

    private final Setting<FireScope> fireScope = sgFire.add(new EnumSetting.Builder<FireScope>()
        .name("fire-scope")
        .description("Everywhere: any fire near you. Boss Fights: only while one of the bosses below is near you. Areas: only fire inside the areas below.")
        .defaultValue(FireScope.Everywhere)
        .visible(fireEnabled::get)
        .build()
    );

    private final Setting<String> fireBosses = sgFire.add(new StringSetting.Builder()
        .name("fire-bosses")
        .description("Boss names (comma separated, case-insensitive, matches anywhere in the name) that switch Fire Floor on while one is near you.")
        .defaultValue("")
        .visible(() -> fireOn() && fireScope.get() == FireScope.BossFights)
        .build()
    );

    private final Setting<Double> fireBossRange = sgFire.add(new DoubleSetting.Builder()
        .name("fire-boss-range")
        .description("How close, in blocks, one of those bosses has to be to count as being in the fight.")
        .defaultValue(60)
        .min(10)
        .sliderRange(20, 150)
        .visible(() -> fireOn() && fireScope.get() == FireScope.BossFights)
        .build()
    );

    private final Setting<Integer> fireRange = sgFire.add(new IntSetting.Builder()
        .name("fire-range")
        .description("How far around you, in blocks, to look for fire.")
        .defaultValue(20)
        .range(4, 48)
        .sliderRange(8, 40)
        .visible(fireEnabled::get)
        .build()
    );

    private final Setting<Double> fireWallHeight = sgFire.add(new DoubleSetting.Builder()
        .name("fire-wall-height")
        .description("How far up the gradient wall reaches above the floor.")
        .defaultValue(1.5)
        .min(0.25)
        .sliderRange(0.5, 4)
        .visible(fireEnabled::get)
        .build()
    );

    private final Setting<SettingColor> fireBaseColor = sgFire.add(new ColorSetting.Builder()
        .name("fire-base-color")
        .description("Color of the wall where it meets the floor.")
        .defaultValue(new SettingColor(255, 140, 0, 190))
        .visible(fireEnabled::get)
        .build()
    );

    private final Setting<SettingColor> fireTopColor = sgFire.add(new ColorSetting.Builder()
        .name("fire-top-color")
        .description("Color the wall fades to at the top - use a low alpha to fade out.")
        .defaultValue(new SettingColor(255, 220, 60, 0))
        .visible(fireEnabled::get)
        .build()
    );

    private final Setting<Boolean> fireSafeTop = sgFire.add(new BoolSetting.Builder()
        .name("highlight-safe-blocks")
        .description("Also tints the top of each safe block that touches fire, so the blocks you can stand on stand out.")
        .defaultValue(true)
        .visible(fireEnabled::get)
        .build()
    );

    private final Setting<SettingColor> fireSafeColor = sgFire.add(new ColorSetting.Builder()
        .name("safe-block-color")
        .description("Tint of the safe blocks' tops.")
        .defaultValue(new SettingColor(60, 255, 90, 70))
        .visible(() -> fireOn() && fireSafeTop.get())
        .build()
    );

    private final Setting<Boolean> fireDiagonalsEnabled = sgFire.add(new BoolSetting.Builder()
        .name("diagonals-safe")
        .description("Also marks a corner safe (with its own marker, not a full wall) wherever a fire block's diagonal neighbor is safe to stand on - the plain wall above only ever checks the 4 orthogonal faces, so a safe tile sitting only at a diagonal corner otherwise gets no marker at all.")
        .defaultValue(false)
        .visible(fireEnabled::get)
        .build()
    );

    private final Setting<SettingColor> fireDiagonalColor = sgFire.add(new ColorSetting.Builder()
        .name("diagonal-color")
        .description("Color of diagonal corner markers - deliberately its own setting, not the same as the wall's Base/Top Color, so a corner (not a full wall face) is visually obvious as a different kind of marker.")
        .defaultValue(new SettingColor(255, 120, 60, 190))
        .visible(() -> fireOn() && fireDiagonalsEnabled.get())
        .build()
    );

    private final Setting<Integer> fireRebuildTicks = sgFire.add(new IntSetting.Builder()
        .name("rescan-interval")
        .description("How often, in ticks, the area is rescanned for fire - lower reacts faster to the floor changing, higher is lighter on performance.")
        .defaultValue(4)
        .range(1, 40)
        .sliderRange(1, 20)
        .visible(fireEnabled::get)
        .build()
    );

    private final SettingGroup sgFireAreas = settings.createGroup("Boss Fights: Fire Areas");

    private final Setting<Boolean> fireArea1Enabled = sgFireAreas.add(new BoolSetting.Builder()
        .name("area-1")
        .description("First area (used when Fire Scope is Areas).")
        .defaultValue(false)
        .visible(() -> fireOn() && fireScope.get() == FireScope.Areas)
        .build()
    );

    private final Setting<Integer> fireArea1X = sgFireAreas.add(new IntSetting.Builder().name("area-1-x").description("Area 1 center X.").defaultValue(0).sliderRange(-3000, 3000).visible(() -> fireArea1Enabled.get() && fireOn() && fireScope.get() == FireScope.Areas).build());
    private final Setting<Integer> fireArea1Z = sgFireAreas.add(new IntSetting.Builder().name("area-1-z").description("Area 1 center Z.").defaultValue(0).sliderRange(-3000, 3000).visible(() -> fireArea1Enabled.get() && fireOn() && fireScope.get() == FireScope.Areas).build());
    private final Setting<Integer> fireArea1Radius = sgFireAreas.add(new IntSetting.Builder().name("area-1-radius").description("Area 1 radius, in blocks.").defaultValue(30).min(1).sliderRange(5, 100).visible(() -> fireArea1Enabled.get() && fireOn() && fireScope.get() == FireScope.Areas).build());

    private final Setting<Boolean> fireArea2Enabled = sgFireAreas.add(new BoolSetting.Builder()
        .name("area-2")
        .description("Second area (used when Fire Scope is Areas).")
        .defaultValue(false)
        .visible(() -> fireOn() && fireScope.get() == FireScope.Areas)
        .build()
    );

    private final Setting<Integer> fireArea2X = sgFireAreas.add(new IntSetting.Builder().name("area-2-x").description("Area 2 center X.").defaultValue(0).sliderRange(-3000, 3000).visible(() -> fireArea2Enabled.get() && fireOn() && fireScope.get() == FireScope.Areas).build());
    private final Setting<Integer> fireArea2Z = sgFireAreas.add(new IntSetting.Builder().name("area-2-z").description("Area 2 center Z.").defaultValue(0).sliderRange(-3000, 3000).visible(() -> fireArea2Enabled.get() && fireOn() && fireScope.get() == FireScope.Areas).build());
    private final Setting<Integer> fireArea2Radius = sgFireAreas.add(new IntSetting.Builder().name("area-2-radius").description("Area 2 radius, in blocks.").defaultValue(30).min(1).sliderRange(5, 100).visible(() -> fireArea2Enabled.get() && fireOn() && fireScope.get() == FireScope.Areas).build());

    private record FireEdge(int x, int y, int z, Direction dir) {}

    private record FireCorner(int x, int y, int z, int dx, int dz) {}

    private final List<FireEdge> fireEdges = new ArrayList<>();
    private final List<FireCorner> fireCorners = new ArrayList<>();
    private int fireTickCounter;
    private final Renderer3D fireRenderer = new Renderer3D();

    // Cosmic Gatekeeper: AOE ring (follows the enchanted-hit particle circles)

    private final SettingGroup sgCgParent = settings.createGroup("Cosmic Gatekeeper");
    private final SettingGroup sgCgAoe = settings.createSubGroup("AOE Ring", sgCgParent);

    private final Setting<String> cgBossName = sgCgAoe.add(new StringSetting.Builder()
        .name("cg-boss-name")
        .description("Cosmic Gatekeeper's name (case-insensitive, matches anywhere in the name). Both Cosmic Gatekeeper entries only work while this boss is within range - otherwise wool block particles from breaking wool, or enchanted hits from anything else, would set them off.")
        .defaultValue("Cosmic Gatekeeper")
        .build()
    );

    private final Setting<Double> cgBossRange = sgCgAoe.add(new DoubleSetting.Builder()
        .name("cg-boss-range")
        .description("How close, in blocks, the boss has to be to you for the Cosmic Gatekeeper entries to be active.")
        .defaultValue(80)
        .min(10)
        .sliderRange(20, 200)
        .build()
    );

    private final Setting<Boolean> cgAoeEnabled = sgCgAoe.add(new BoolSetting.Builder()
        .name("cg-aoe-ring")
        .description("Cosmic Gatekeeper's normal AOE attack.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> cgAoeParticle = sgCgAoe.add(new StringSetting.Builder()
        .name("cg-aoe-particle")
        .description("The particle the AOE circle is made of (registry id).")
        .defaultValue("minecraft:enchanted_hit")
        .visible(cgAoeEnabled::get)
        .build()
    );

    private final Setting<Integer> cgAoeLifetime = sgCgAoe.add(new IntSetting.Builder()
        .name("cg-aoe-lifetime")
        .description("How long, in milliseconds, the ring stays drawn after the last of its particles - long enough to cover all three pulses.")
        .defaultValue(1500)
        .range(200, 10000)
        .sliderRange(200, 5000)
        .visible(cgAoeEnabled::get)
        .build()
    );

    private final Setting<Integer> cgAoeMinParticles = sgCgAoe.add(new IntSetting.Builder()
        .name("cg-aoe-min-particles")
        .description("How many particles have to be seen together before a ring is drawn.")
        .defaultValue(6)
        .range(3, 64)
        .sliderRange(3, 32)
        .visible(cgAoeEnabled::get)
        .build()
    );

    private final Setting<Double> cgAoeMinRadius = sgCgAoe.add(new DoubleSetting.Builder()
        .name("cg-aoe-min-radius")
        .description("Ignores particle groups whose fitted ring is smaller than this.")
        .defaultValue(2)
        .min(0.5)
        .sliderRange(1, 30)
        .visible(cgAoeEnabled::get)
        .build()
    );

    private final Setting<Double> cgAoeMaxRadius = sgCgAoe.add(new DoubleSetting.Builder()
        .name("cg-aoe-max-radius")
        .description("Ignores particle groups whose fitted ring is bigger than this.")
        .defaultValue(30)
        .min(2)
        .sliderRange(5, 100)
        .visible(cgAoeEnabled::get)
        .build()
    );

    private final Setting<Integer> cgAoeBurstWindow = sgCgAoe.add(new IntSetting.Builder()
        .name("cg-aoe-burst-window")
        .description("Only particles seen within this many milliseconds of the newest are fitted together.")
        .defaultValue(400)
        .range(50, 3000)
        .sliderRange(100, 1500)
        .visible(cgAoeEnabled::get)
        .build()
    );

    private final Setting<SettingColor> cgAoeBaseColor = sgCgAoe.add(new ColorSetting.Builder()
        .name("cg-aoe-base-color")
        .description("Ring color at ground level.")
        .defaultValue(new SettingColor(170, 90, 255, 170))
        .visible(cgAoeEnabled::get)
        .build()
    );

    private final Setting<SettingColor> cgAoeTopColor = sgCgAoe.add(new ColorSetting.Builder()
        .name("cg-aoe-top-color")
        .description("Color the ring fades to at the top.")
        .defaultValue(new SettingColor(255, 255, 255, 0))
        .visible(cgAoeEnabled::get)
        .build()
    );

    // Cosmic Gatekeeper: bombs

    private final SettingGroup sgCgBomb = settings.createSubGroup("Bomb Radius", sgCgParent);

    private final Setting<Boolean> bombEnabled = sgCgBomb.add(new BoolSetting.Builder()
        .name("cg-bomb")
        .description("Cosmic Gatekeeper's bombs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> bombRadius = sgCgBomb.add(new DoubleSetting.Builder()
        .name("bomb-radius")
        .description("The blast radius, in blocks - the same for every bomb type. The default is a placeholder guess (adjust it).")
        .defaultValue(6)
        .min(1)
        .sliderRange(2, 30)
        .visible(bombEnabled::get)
        .build()
    );

    private final Setting<String> bombInnerParticle = sgCgBomb.add(new StringSetting.Builder()
        .name("bomb-inner-particle")
        .description("The particle the expanding inner ring is made of (registry id) - crit by default; change it if the server uses something else.")
        .defaultValue("minecraft:crit")
        .visible(bombEnabled::get)
        .build()
    );

    private final Setting<Integer> bombTimeout = sgCgBomb.add(new IntSetting.Builder()
        .name("bomb-timeout")
        .description("A bomb is forgotten this many milliseconds after its last wool particle. Recorded bombs explode much faster than the old default - if markers are outliving the real bomb, lower this.")
        .defaultValue(900)
        .range(300, 15000)
        .sliderRange(500, 8000)
        .visible(bombEnabled::get)
        .build()
    );

    private final Setting<Boolean> ringOnlyAfterImpact = sgCgBomb.add(new BoolSetting.Builder()
        .name("ring-only-after-impact")
        .description("While a bomb is still flying (homing on you, missile visible), hides its radius-preview ring - only the missile itself shows. The real blast ring (fitted from the actual explosion particles once it lands) is unaffected either way and still shows exactly when the blast happens.")
        .defaultValue(false)
        .visible(bombEnabled::get)
        .build()
    );

    private final Setting<Double> bombMergeRadius = sgCgBomb.add(new DoubleSetting.Builder()
        .name("bomb-merge-radius")
        .description("Wool particles of the same color this close together (blocks) are treated as one bomb.")
        .defaultValue(3)
        .min(0.5)
        .sliderRange(1, 10)
        .visible(bombEnabled::get)
        .build()
    );

    private final Setting<Double> bombExplodeAt = sgCgBomb.add(new DoubleSetting.Builder()
        .name("explode-warning")
        .description("The outer ring flashes once the inner ring reaches this fraction of its radius (0.9 = 90%).")
        .defaultValue(0.85)
        .range(0.3, 1)
        .sliderRange(0.5, 1)
        .visible(bombEnabled::get)
        .build()
    );

    private final Setting<Double> bombHeight = sgCgBomb.add(new DoubleSetting.Builder()
        .name("bomb-ring-height")
        .description("How far up the rings' walls reach.")
        .defaultValue(2)
        .min(0.5)
        .sliderRange(1, 10)
        .visible(bombEnabled::get)
        .build()
    );

    private final Setting<Double> bombPillarHeight = sgCgBomb.add(new DoubleSetting.Builder()
        .name("pillar-height")
        .description("Height of the pillar marking the bomb's exact position. 0 turns it off.")
        .defaultValue(6)
        .min(0)
        .sliderRange(0, 20)
        .visible(bombEnabled::get)
        .build()
    );

    private final Setting<SettingColor> bombCyan = sgCgBomb.add(new ColorSetting.Builder().name("cyan-bomb-color").description("Ring color for cyan wool bombs.").defaultValue(new SettingColor(0, 230, 255, 190)).visible(bombEnabled::get).build());
    private final Setting<SettingColor> bombRed = sgCgBomb.add(new ColorSetting.Builder().name("red-bomb-color").description("Ring color for red wool bombs.").defaultValue(new SettingColor(255, 50, 50, 190)).visible(bombEnabled::get).build());
    private final Setting<SettingColor> bombYellow = sgCgBomb.add(new ColorSetting.Builder().name("yellow-bomb-color").description("Ring color for yellow wool bombs.").defaultValue(new SettingColor(255, 220, 40, 190)).visible(bombEnabled::get).build());
    private final Setting<SettingColor> bombInnerColor = sgCgBomb.add(new ColorSetting.Builder().name("inner-ring-color").description("Color of the growing inner ring.").defaultValue(new SettingColor(255, 255, 255, 200)).visible(bombEnabled::get).build());

    private final Setting<Boolean> bombPathEnabled = sgCgBomb.add(new BoolSetting.Builder()
        .name("bomb-path")
        .description("Floor bombs: the line showing where the bomb will travel, from the boss-side wall across to the far wall.")
        .defaultValue(true)
        .visible(bombEnabled::get)
        .build()
    );

    private final Setting<Integer> bombPathLifetime = sgCgBomb.add(new IntSetting.Builder()
        .name("bomb-path-lifetime")
        .description("How long, in milliseconds, a bomb's path line stays drawn after its last particle. Recorded floor bombs explode much faster than the old default - if the line is outliving the real bomb, lower this.")
        .defaultValue(900)
        .range(200, 15000)
        .sliderRange(500, 8000)
        .visible(() -> bombOn() && bombPathEnabled.get())
        .build()
    );

    private final Setting<Double> bombPathHeight = sgCgBomb.add(new DoubleSetting.Builder()
        .name("bomb-path-height")
        .description("Height of the path line's wall.")
        .defaultValue(0.6)
        .min(0.05)
        .sliderRange(0.1, 3)
        .visible(() -> bombOn() && bombPathEnabled.get())
        .build()
    );

    // Two independent sets of missile settings - the flying/homing bombs (drawBombs) and the floor
    // bombs following their path (drawBombPaths) are visually distinct enough in the recorded fight
    // (different heights, different movement) that sharing one enabled/scale/color made it impossible
    // to tune or disable them separately.

    private final Setting<Boolean> homingMissileEnabled = sgCgBomb.add(new BoolSetting.Builder()
        .name("homing-missile-model")
        .description("Draws a small missile hologram on bombs flying toward a player - the CG's bombs are visibly missiles, not just markers, while they're homing in.")
        .defaultValue(true)
        .visible(bombEnabled::get)
        .build()
    );

    private final Setting<Double> homingMissileScale = sgCgBomb.add(new DoubleSetting.Builder()
        .name("homing-missile-scale")
        .description("Size of the homing missile hologram.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.2, 3)
        .visible(() -> bombOn() && homingMissileEnabled.get())
        .build()
    );

    private final Setting<SettingColor> homingMissileColor = sgCgBomb.add(new ColorSetting.Builder()
        .name("homing-missile-color")
        .description("Tint of the homing missile hologram.")
        .defaultValue(new SettingColor(70, 210, 255, 200))
        .visible(() -> bombOn() && homingMissileEnabled.get())
        .build()
    );

    private final Setting<Boolean> floorMissileEnabled = sgCgBomb.add(new BoolSetting.Builder()
        .name("floor-missile-model")
        .description("Draws a small missile hologram on floor bombs following their path, at the head of the trail.")
        .defaultValue(true)
        .visible(bombEnabled::get)
        .build()
    );

    private final Setting<Double> floorMissileScale = sgCgBomb.add(new DoubleSetting.Builder()
        .name("floor-missile-scale")
        .description("Size of the floor bomb's missile hologram.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.2, 3)
        .visible(() -> bombOn() && floorMissileEnabled.get())
        .build()
    );

    private final Setting<SettingColor> floorMissileColor = sgCgBomb.add(new ColorSetting.Builder()
        .name("floor-missile-color")
        .description("Tint of the floor bomb's missile hologram.")
        .defaultValue(new SettingColor(70, 210, 255, 200))
        .visible(() -> bombOn() && floorMissileEnabled.get())
        .build()
    );

    private static final class BombPath {
        final net.minecraft.block.Block wool;
        final List<double[]> pts = new ArrayList<>();
        long last;

        BombPath(net.minecraft.block.Block wool) { this.wool = wool; }
    }

    private final List<BombPath> bombPaths = new ArrayList<>();

    private static final class Bomb {
        final net.minecraft.block.Block wool;
        double x, y, z;
        int samples;
        long lastSeen;

        Bomb(net.minecraft.block.Block wool, double x, double y, double z, long now) {
            this.wool = wool;
            this.x = x; this.y = y; this.z = z;
            this.samples = 1;
            this.lastSeen = now;
        }

        void add(double px, double py, double pz, long now) {
            samples++;
            double w = 1.0 / Math.min(samples, 8); // running mean, leaning on recent samples
            x += (px - x) * w; y += (py - y) * w; z += (pz - z) * w;
            lastSeen = now;
        }
    }

    // Pulsars (ring follows the firework particles)

    private final SettingGroup sgPulsar = settings.createGroup("Pulsars: Ring");

    private final Setting<Boolean> pulsarEnabled = sgPulsar.add(new BoolSetting.Builder()
        .name("pulsar-ring")
        .description("Pulsar's firework ring.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> pulsarName = sgPulsar.add(new StringSetting.Builder()
        .name("pulsar-name")
        .description("Text (case-insensitive) the Pulsar's name must contain. The ring only works while one is within range, so fireworks from anything else don't draw it. Leave empty to draw for any firework particles. (Cosmic Gatekeeper's own \"Spawning: Pulsar\" telegraph text is excluded automatically, regardless of this text.)")
        .defaultValue("Pulsar")
        .visible(pulsarEnabled::get)
        .build()
    );

    private final Setting<Double> pulsarRange = sgPulsar.add(new DoubleSetting.Builder()
        .name("pulsar-range")
        .description("How close a Pulsar has to be to you, in blocks.")
        .defaultValue(80)
        .min(10)
        .sliderRange(20, 200)
        .visible(pulsarEnabled::get)
        .build()
    );

    private final Setting<String> pulsarParticle = sgPulsar.add(new StringSetting.Builder()
        .name("pulsar-particle")
        .description("The particle the ring is made of (registry id).")
        .defaultValue("minecraft:firework")
        .visible(pulsarEnabled::get)
        .build()
    );

    private final Setting<Integer> pulsarLifetime = sgPulsar.add(new IntSetting.Builder()
        .name("pulsar-lifetime")
        .description("How long, in milliseconds, the ring stays drawn after its last particle.")
        .defaultValue(1200)
        .range(200, 10000)
        .sliderRange(200, 5000)
        .visible(pulsarEnabled::get)
        .build()
    );

    private final Setting<Integer> pulsarMinParticles = sgPulsar.add(new IntSetting.Builder()
        .name("pulsar-min-particles")
        .description("How many particles have to be seen together before a ring is drawn. Recorded: the Pulsar's ring is ~12 firework particles.")
        .defaultValue(8)
        .range(3, 64)
        .sliderRange(3, 32)
        .visible(pulsarEnabled::get)
        .build()
    );

    private final Setting<Double> pulsarMinRadius = sgPulsar.add(new DoubleSetting.Builder()
        .name("pulsar-min-radius")
        .description("Ignores particle groups whose fitted ring is smaller than this. Recorded: the Pulsar's own firework ring is small (~0.6 blocks), so keep this low.")
        .defaultValue(0.3)
        .min(0.1)
        .sliderRange(0.1, 30)
        .visible(pulsarEnabled::get)
        .build()
    );

    private final Setting<Double> pulsarMaxRadius = sgPulsar.add(new DoubleSetting.Builder()
        .name("pulsar-max-radius")
        .description("Ignores particle groups whose fitted ring is bigger than this.")
        .defaultValue(30)
        .min(2)
        .sliderRange(5, 100)
        .visible(pulsarEnabled::get)
        .build()
    );

    private final Setting<Integer> pulsarBurstWindow = sgPulsar.add(new IntSetting.Builder()
        .name("pulsar-burst-window")
        .description("Only particles seen within this many milliseconds of the newest are fitted together. Keep it short: the ring appears in one tick, and the line of sparks that follows it would spoil the fit.")
        .defaultValue(120)
        .range(20, 3000)
        .sliderRange(50, 1500)
        .visible(pulsarEnabled::get)
        .build()
    );

    private final Setting<Double> pulsarDrawRadius = sgPulsar.add(new DoubleSetting.Builder()
        .name("pulsar-draw-radius")
        .description("Radius of the ring drawn around the spot the firework ring was found. 0 = draw the fitted radius as-is (tiny for the Pulsar). Set this to the Pulsar's real reach.")
        .defaultValue(6.0)
        .min(0)
        .sliderRange(0, 30)
        .visible(pulsarEnabled::get)
        .build()
    );

    private final Setting<Boolean> pulsarOnGround = sgPulsar.add(new BoolSetting.Builder()
        .name("pulsar-on-ground")
        .description("Draw the ring on the floor below the particles instead of at their height. Recorded: the Pulsar's sparks float ~10 blocks above the floor.")
        .defaultValue(true)
        .visible(pulsarEnabled::get)
        .build()
    );

    private final Setting<SettingColor> pulsarBaseColor = sgPulsar.add(new ColorSetting.Builder()
        .name("pulsar-base-color")
        .description("Ring color at ground level.")
        .defaultValue(new SettingColor(255, 80, 200, 170))
        .visible(pulsarEnabled::get)
        .build()
    );

    private final Setting<SettingColor> pulsarTopColor = sgPulsar.add(new ColorSetting.Builder()
        .name("pulsar-top-color")
        .description("Color the ring fades to at the top.")
        .defaultValue(new SettingColor(255, 230, 120, 0))
        .visible(pulsarEnabled::get)
        .build()
    );

    // Sirens (The Sirens boss fight: 3 stationary, invisible mobs around the arena)

    private final SettingGroup sgSirens = settings.createGroup("Sirens: Markers");

    private final Setting<Boolean> sirensEnabled = sgSirens.add(new BoolSetting.Builder()
        .name("sirens-markers")
        .description("Marks each Siren - recorded as invisible and stationary for the whole fight, one at each of (up to) 3 fixed spots around the arena, so there's normally nothing to see there at all. These are the real enemies (wither skeletons) - not the separate, single, constantly-teleporting invisible wither that only drives the encounter's boss bar and is excluded here on purpose.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> sirensName = sgSirens.add(new StringSetting.Builder()
        .name("sirens-name")
        .description("Text (case-insensitive) a mob's name must contain to be marked. (Cosmic Gatekeeper's own \"Spawning: Siren\" telegraph text is excluded automatically, regardless of this text.)")
        .defaultValue("Siren")
        .visible(sirensEnabled::get)
        .build()
    );

    private final Setting<Double> sirensRange = sgSirens.add(new DoubleSetting.Builder()
        .name("sirens-range")
        .description("Only marks Sirens within this many blocks of you.")
        .defaultValue(60)
        .min(5)
        .sliderRange(10, 150)
        .visible(sirensEnabled::get)
        .build()
    );

    private final Setting<Double> sirensPillarHeight = sgSirens.add(new DoubleSetting.Builder()
        .name("sirens-pillar-height")
        .defaultValue(5)
        .min(0.5)
        .sliderRange(1, 15)
        .visible(sirensEnabled::get)
        .build()
    );

    private final Setting<Double> sirensRingRadius = sgSirens.add(new DoubleSetting.Builder()
        .name("sirens-ring-radius")
        .description("Radius of the ring around each Siren's feet. 0 turns the ring off.")
        .defaultValue(1.2)
        .min(0)
        .sliderRange(0, 5)
        .visible(sirensEnabled::get)
        .build()
    );

    private final Setting<SettingColor> sirensColor = sgSirens.add(new ColorSetting.Builder()
        .name("sirens-color")
        .defaultValue(new SettingColor(120, 200, 255, 200))
        .visible(sirensEnabled::get)
        .build()
    );

    // Sirens: Line Attack - a channeled beam from the Siren toward its target, made of a dense,
    // continuous trail of enchant/enchanted_hit particles (confirmed from the recorded fight: gaps
    // between consecutive particles are a couple of milliseconds within one cast, and well over
    // 400ms between separate casts) - normally very hard to actually see, so drawn as a solid line
    // connecting every still-"alive" point in arrival order.

    // Amalgamation: Adds - marks the named adds that spawn alongside Nightmare Amalgamation
    // (confirmed from recordings: Nightmare Tarantula/spider, Nightmare Stalker/cave_spider,
    // Nightmare Toxic Specimen/skeleton, Nightmare Mender/wither_skeleton - all easy to lose track
    // of in the chaos since they're normal mob models, not obviously distinct at a glance) with a
    // colored pillar per type, plus a separate tracker for the "Obelisk" item-entities (the end
    // crystal + sheep structures) that also spawn during the fight.

    private final SettingGroup sgAmalAdds = settings.createGroup("Amalgamation: Adds");

    private final Setting<Boolean> amalAddsEnabled = sgAmalAdds.add(new BoolSetting.Builder()
        .name("amal-adds")
        .description("Marks Amalgamation's named adds with a colored pillar, one color per type below, so they're easy to spot and tell apart.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> amalAddsRange = sgAmalAdds.add(new DoubleSetting.Builder()
        .name("amal-adds-range")
        .description("How close an add needs to be to you to get marked.")
        .defaultValue(40.0)
        .min(5)
        .sliderRange(10, 100)
        .visible(amalAddsEnabled::get)
        .build()
    );

    private final Setting<Double> amalAddsPillarHeight = sgAmalAdds.add(new DoubleSetting.Builder()
        .name("amal-adds-pillar-height")
        .description("Height of each add's marker pillar.")
        .defaultValue(6.0)
        .min(1)
        .sliderRange(1, 20)
        .visible(amalAddsEnabled::get)
        .build()
    );

    private final Setting<String> tarantulaName = sgAmalAdds.add(new StringSetting.Builder()
        .name("tarantula-name")
        .description("Text (case-insensitive) a nearby spider's name must contain to be marked as a Tarantula add. Leave empty to turn off just this one.")
        .defaultValue("Tarantula")
        .visible(amalAddsEnabled::get)
        .build()
    );

    private final Setting<SettingColor> tarantulaColor = sgAmalAdds.add(new ColorSetting.Builder()
        .name("tarantula-color")
        .description("Marker color for Tarantula adds.")
        .defaultValue(new SettingColor(255, 140, 0, 190))
        .visible(amalAddsEnabled::get)
        .build()
    );

    private final Setting<String> stalkerName = sgAmalAdds.add(new StringSetting.Builder()
        .name("stalker-name")
        .description("Text (case-insensitive) a nearby cave spider's name must contain to be marked as a Stalker add. Leave empty to turn off just this one.")
        .defaultValue("Stalker")
        .visible(amalAddsEnabled::get)
        .build()
    );

    private final Setting<SettingColor> stalkerColor = sgAmalAdds.add(new ColorSetting.Builder()
        .name("stalker-color")
        .description("Marker color for Stalker adds.")
        .defaultValue(new SettingColor(170, 70, 255, 190))
        .visible(amalAddsEnabled::get)
        .build()
    );

    private final Setting<String> toxicSpecimenName = sgAmalAdds.add(new StringSetting.Builder()
        .name("toxic-specimen-name")
        .description("Text (case-insensitive) a nearby skeleton's name must contain to be marked as a Toxic Specimen add. Leave empty to turn off just this one.")
        .defaultValue("Toxic Specimen")
        .visible(amalAddsEnabled::get)
        .build()
    );

    private final Setting<SettingColor> toxicSpecimenColor = sgAmalAdds.add(new ColorSetting.Builder()
        .name("toxic-specimen-color")
        .description("Marker color for Toxic Specimen adds.")
        .defaultValue(new SettingColor(60, 220, 60, 190))
        .visible(amalAddsEnabled::get)
        .build()
    );

    private final Setting<String> menderName = sgAmalAdds.add(new StringSetting.Builder()
        .name("mender-name")
        .description("Text (case-insensitive) a nearby wither skeleton's name must contain to be marked as a Mender add. Leave empty to turn off just this one.")
        .defaultValue("Mender")
        .visible(amalAddsEnabled::get)
        .build()
    );

    private final Setting<SettingColor> menderColor = sgAmalAdds.add(new ColorSetting.Builder()
        .name("mender-color")
        .description("Marker color for Mender adds - a different, more attention-grabbing color than the others by default, since a healer-type add is usually worth prioritizing.")
        .defaultValue(new SettingColor(255, 230, 0, 220))
        .visible(amalAddsEnabled::get)
        .build()
    );

    private final Setting<Boolean> obeliskEnabled = sgAmalAdds.add(new BoolSetting.Builder()
        .name("obelisk-tracker")
        .description("Marks the \"Obelisk\" structures (end crystal + sheep) with their own colored pillar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> obeliskName = sgAmalAdds.add(new StringSetting.Builder()
        .name("obelisk-name")
        .description("Text (case-insensitive) a nearby entity's name must contain to be marked as an Obelisk.")
        .defaultValue("Obelisk")
        .visible(obeliskEnabled::get)
        .build()
    );

    private final Setting<Double> obeliskRange = sgAmalAdds.add(new DoubleSetting.Builder()
        .name("obelisk-range")
        .description("How close an Obelisk needs to be to you to get marked.")
        .defaultValue(60.0)
        .min(5)
        .sliderRange(10, 150)
        .visible(obeliskEnabled::get)
        .build()
    );

    private final Setting<SettingColor> obeliskColor = sgAmalAdds.add(new ColorSetting.Builder()
        .name("obelisk-color")
        .description("Marker color for Obelisks.")
        .defaultValue(new SettingColor(0, 220, 255, 190))
        .visible(obeliskEnabled::get)
        .build()
    );

    private final Setting<Double> obeliskPillarHeight = sgAmalAdds.add(new DoubleSetting.Builder()
        .name("obelisk-pillar-height")
        .description("Height of each Obelisk's marker pillar.")
        .defaultValue(8.0)
        .min(1)
        .sliderRange(1, 30)
        .visible(obeliskEnabled::get)
        .build()
    );

    private final SettingGroup sgSirensLine = settings.createGroup("Sirens: Line Attack");

    private final Setting<Boolean> sirensLineEnabled = sgSirensLine.add(new BoolSetting.Builder()
        .name("sirens-line-attack")
        .description("Marks the Siren's channeled line attack as a solid line.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> sirensLineParticle = sgSirensLine.add(new StringSetting.Builder()
        .name("sirens-line-particle")
        .description("Particle(s) the line attack is made of (registry id, comma separated).")
        .defaultValue("minecraft:enchant,minecraft:enchanted_hit")
        .visible(sirensLineEnabled::get)
        .build()
    );

    private final Setting<Integer> sirensLineLifetime = sgSirensLine.add(new IntSetting.Builder()
        .name("sirens-line-lifetime")
        .description("How long, in milliseconds, a point on the line stays drawn after it last got a particle - keep this short, just long enough to bridge gaps within one cast, so separate casts don't visually merge into one line.")
        .defaultValue(400)
        .range(100, 5000)
        .sliderRange(100, 2000)
        .visible(sirensLineEnabled::get)
        .build()
    );

    private final Setting<SettingColor> sirensLineColor = sgSirensLine.add(new ColorSetting.Builder()
        .name("sirens-line-color")
        .defaultValue(new SettingColor(255, 60, 220, 220))
        .visible(sirensLineEnabled::get)
        .build()
    );

    private volatile boolean sirensPresent = false;
    private final List<Seen> sirensLineSeen = new ArrayList<>();

    // Aegis (marks the mob when it's no longer visible normally)

    private final SettingGroup sgAegis = settings.createGroup("Aegis: Marker");

    private final Setting<Boolean> aegisEnabled = sgAegis.add(new BoolSetting.Builder()
        .name("aegis-marker")
        .description("Marks the Aegis mob when it can't be seen normally.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> aegisName = sgAegis.add(new StringSetting.Builder()
        .name("aegis-name")
        .description("Text (case-insensitive) the Aegis mob's name must contain.")
        .defaultValue("Aegis")
        .visible(aegisEnabled::get)
        .build()
    );

    private final Setting<Double> aegisRange = sgAegis.add(new DoubleSetting.Builder()
        .name("aegis-range")
        .description("Only looks for the Aegis within this many blocks of you.")
        .defaultValue(60)
        .min(5)
        .sliderRange(10, 150)
        .visible(aegisEnabled::get)
        .build()
    );

    private final Setting<Boolean> aegisAlways = sgAegis.add(new BoolSetting.Builder()
        .name("aegis-always")
        .description("Marks it even while it isn't flagged invisible. In the recorded fight the Aegis was never flagged invisible, so this has to stay on for the marker to show. Off: only while it's flagged invisible, or gone and only its particles are left.")
        .defaultValue(true)
        .visible(aegisEnabled::get)
        .build()
    );

    private final Setting<String> aegisParticle = sgAegis.add(new StringSetting.Builder()
        .name("aegis-particle")
        .description("A particle the Aegis gives off (registry id, e.g. minecraft:witch) - when it's invisible or missing, the marker follows these particles instead. Empty = only use the mob itself.")
        .defaultValue("")
        .visible(aegisEnabled::get)
        .build()
    );

    private final Setting<Double> aegisParticleRadius = sgAegis.add(new DoubleSetting.Builder()
        .name("aegis-particle-radius")
        .description("Particles further than this from where the Aegis was last seen (or from you, if it never was) are ignored.")
        .defaultValue(10)
        .min(1)
        .sliderRange(2, 40)
        .visible(aegisEnabled::get)
        .build()
    );

    private final Setting<Integer> aegisMemory = sgAegis.add(new IntSetting.Builder()
        .name("aegis-memory")
        .description("How long, in milliseconds, the marker stays at its last known spot after the mob and its particles stop showing.")
        .defaultValue(2000)
        .range(0, 15000)
        .sliderRange(0, 8000)
        .visible(aegisEnabled::get)
        .build()
    );

    private final Setting<Double> aegisPillarHeight = sgAegis.add(new DoubleSetting.Builder()
        .name("aegis-pillar-height")
        .description("Height of the pillar marking its position.")
        .defaultValue(4)
        .min(0.5)
        .sliderRange(1, 15)
        .visible(aegisEnabled::get)
        .build()
    );

    private final Setting<Double> aegisRingRadius = sgAegis.add(new DoubleSetting.Builder()
        .name("aegis-ring-radius")
        .description("Radius of the ring around its feet. 0 turns the ring off.")
        .defaultValue(1.2)
        .min(0)
        .sliderRange(0, 5)
        .visible(aegisEnabled::get)
        .build()
    );

    private final Setting<SettingColor> aegisColor = sgAegis.add(new ColorSetting.Builder()
        .name("aegis-color")
        .description("Marker color.")
        .defaultValue(new SettingColor(200, 110, 255, 200))
        .visible(aegisEnabled::get)
        .build()
    );

    // Reaper (marks the mob when it's no longer visible normally) - a second, separate tracker of
    // the exact same shape as Aegis above, just pointed at a different mob by default. Deliberately
    // not merged with Aegis into one generic list - keeping them as two independent copies means each
    // one's settings (name/particle/range/etc.) stay simple single values instead of a list-of-configs.

    private final SettingGroup sgReaper = settings.createGroup("Reaper: Marker");

    private final Setting<Boolean> reaperEnabled = sgReaper.add(new BoolSetting.Builder()
        .name("reaper-marker")
        .description("Marks the Reaper mob when it can't be seen normally.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> reaperName = sgReaper.add(new StringSetting.Builder()
        .name("reaper-name")
        .description("Text (case-insensitive) the Reaper mob's name must contain.")
        .defaultValue("Reaper")
        .visible(reaperEnabled::get)
        .build()
    );

    private final Setting<Double> reaperRange = sgReaper.add(new DoubleSetting.Builder()
        .name("reaper-range")
        .description("Only looks for the Reaper within this many blocks of you.")
        .defaultValue(60)
        .min(5)
        .sliderRange(10, 150)
        .visible(reaperEnabled::get)
        .build()
    );

    private final Setting<Boolean> reaperAlways = sgReaper.add(new BoolSetting.Builder()
        .name("reaper-always")
        .description("Marks it even while it isn't flagged invisible. Off: only while it's flagged invisible, or gone and only its particles are left.")
        .defaultValue(false)
        .visible(reaperEnabled::get)
        .build()
    );

    private final Setting<String> reaperParticle = sgReaper.add(new StringSetting.Builder()
        .name("reaper-particle")
        .description("A particle the Reaper gives off (registry id, e.g. minecraft:soul) - once it's out of render distance (or invisible/missing), the marker follows these particles instead. Empty = only use the mob itself.")
        .defaultValue("")
        .visible(reaperEnabled::get)
        .build()
    );

    private final Setting<Double> reaperParticleRadius = sgReaper.add(new DoubleSetting.Builder()
        .name("reaper-particle-radius")
        .description("Particles further than this from where the Reaper was last seen are ignored.")
        .defaultValue(10)
        .min(1)
        .sliderRange(2, 40)
        .visible(reaperEnabled::get)
        .build()
    );

    private final Setting<Integer> reaperMemory = sgReaper.add(new IntSetting.Builder()
        .name("reaper-memory")
        .description("How long, in milliseconds, the marker stays at its last known spot after the mob and its particles stop showing.")
        .defaultValue(2000)
        .range(0, 15000)
        .sliderRange(0, 8000)
        .visible(reaperEnabled::get)
        .build()
    );

    private final Setting<Double> reaperPillarHeight = sgReaper.add(new DoubleSetting.Builder()
        .name("reaper-pillar-height")
        .description("Height of the pillar marking its position.")
        .defaultValue(4)
        .min(0.5)
        .sliderRange(1, 15)
        .visible(reaperEnabled::get)
        .build()
    );

    private final Setting<Double> reaperRingRadius = sgReaper.add(new DoubleSetting.Builder()
        .name("reaper-ring-radius")
        .description("Radius of the ring around its feet. 0 turns the ring off.")
        .defaultValue(1.2)
        .min(0)
        .sliderRange(0, 5)
        .visible(reaperEnabled::get)
        .build()
    );

    private final Setting<SettingColor> reaperColor = sgReaper.add(new ColorSetting.Builder()
        .name("reaper-color")
        .description("Marker color.")
        .defaultValue(new SettingColor(150, 40, 40, 200))
        .visible(reaperEnabled::get)
        .build()
    );

    private double reaperX, reaperY, reaperZ;
    private boolean reaperKnown;
    private boolean reaperEntityHidden;
    private long reaperLastSeenMs;
    private volatile double reaperParticleX, reaperParticleY, reaperParticleZ;
    private volatile long reaperParticleMs;

    private volatile boolean pulsarPresent = false;
    private final List<Seen> pulsarSeen = new ArrayList<>();

    private double aegisX, aegisY, aegisZ;
    private boolean aegisKnown;
    private boolean aegisEntityHidden;
    private long aegisLastSeenMs;
    private volatile double aegisParticleX, aegisParticleY, aegisParticleZ;
    private volatile long aegisParticleMs;

    private volatile boolean cgPresent = false;
    private final List<Bomb> bombs = new ArrayList<>();
    private record BombRingPoint(net.minecraft.block.Block wool, Seen p) {}
    private final List<BombRingPoint> bombRingSeen = new ArrayList<>();
    private final List<Seen> aoeSeen = new ArrayList<>();
    private final List<Seen> bombInnerSeen = new ArrayList<>();

    private record Seen(double x, double y, double z, long time) {}

    private final List<Seen> seen = new ArrayList<>();

    // Own renderer (not the shared event one) so depth testing can be on for just this module -
    // the shared meshes draw with depth test off, i.e. on top of everything including walls.
    private final Renderer3D ring = new Renderer3D();
    // Rings that opaquely hide whatever is behind them (see See Through below); everything else goes through `ring`.
    private final Renderer3D ringSolid = new Renderer3D();
    private Renderer3D cur = ring;

    // The admin-only constant Bones rings live in their own component: its settings are merged into this module's and its
    // events are forwarded while this module is on, so there is a single Visualizer with sections instead of two modules.
    private final BonesRings bones = new BonesRings();

    public BonesRings bones() {
        return bones;
    }

    /** True if the ring with this key should hide what's behind it (the old behavior). */
    public boolean ringBlocks(String key) {
        if (!seeThrough.get()) return true;

        for (String entry : blockingRings.get()) if (entry.trim().equalsIgnoreCase(key)) return true;
        return false;
    }

    private Renderer3D pick(String key) {
        return ringBlocks(key) ? ringSolid : ring;
    }

    private boolean aoeOn() { return cgAoeEnabled.get() && CheeseControl.sectionCgAoe(); }
    private boolean bombOn() { return bombEnabled.get() && CheeseControl.sectionCgBombs(); }
    private boolean pulsarOn() { return pulsarEnabled.get() && CheeseControl.sectionPulsars(); }
    private boolean aegisOn() { return aegisEnabled.get() && CheeseControl.sectionAegis(); }
    private boolean reaperOn() { return reaperEnabled.get() && CheeseControl.sectionReaper(); }
    private boolean lavaOn() { return lavaEnabled.get() && CheeseControl.sectionLava(); }
    private boolean fireOn() { return fireEnabled.get() && CheeseControl.sectionFire(); }
    private boolean bonesRingOn() { return bonesRingEnabled.get() && CheeseControl.sectionBones(); }

    public Visualizer() {
        super(Categories.Render, "visualizer", "Boss and hazard visualizers: a ring that follows Bones's angry particles, and a lava-floor safe-edge marker.", "boss-visualizer", "cg", "cosmic-gatekeeper", "sirens", "bombs", "lava-floor");

        settings.groups.addAll(bones.settings.groups);

        // Whole sections can be locked away from Cheese Control (and the admin ones need .Admin as well).
        sgLava.visible = sgLavaAreas.visible = CheeseControl::sectionLava;
        sgFire.visible = sgFireAreas.visible = CheeseControl::sectionFire;
        sgCgAoe.visible = CheeseControl::sectionCgAoe;
        sgCgBomb.visible = CheeseControl::sectionCgBombs;
        sgPulsar.visible = CheeseControl::sectionPulsars;
        sgAegis.visible = CheeseControl::sectionAegis;
        sgReaper.visible = CheeseControl::sectionReaper;
        for (SettingGroup group : bones.settings.groups) {
            group.visible = () -> Modules.get() != null && Modules.get().isCheeseUnlocked() && CheeseControl.sectionBonesRings();
        }

        ringSolid.lines.depthTest = true;
        ringSolid.triangles.depthTest = true;
        ring.lines.depthTest = true;
        ring.triangles.depthTest = true;
        lavaRenderer.lines.depthTest = true;
        lavaRenderer.triangles.depthTest = true;
        fireRenderer.lines.depthTest = true;
        fireRenderer.triangles.depthTest = true;
    }

    @Override
    public void onActivate() {
        MeteorClient.EVENT_BUS.subscribe(bones);
        bones.onActivate();
    }

    @Override
    public void onDeactivate() {
        MeteorClient.EVENT_BUS.unsubscribe(bones);
        bones.onDeactivate();
        seen.clear();
        aoeSeen.clear();
        bombInnerSeen.clear();
        bombs.clear();
        pulsarSeen.clear();
        sirensLineSeen.clear();
        aegisKnown = false;
        aegisParticleMs = 0;
        reaperKnown = false;
        reaperParticleMs = 0;
        lavaEdges.clear();
        lavaCorners.clear();
        fireEdges.clear();
        fireCorners.clear();
    }

    private boolean lavaBossNearby() {
        String[] names = lavaBosses.get().toLowerCase().split(",");
        double rangeSq = lavaBossRange.get() * lavaBossRange.get();

        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity) continue;
            if (entity.squaredDistanceTo(mc.player) > rangeSq) continue;

            String name = entity.getName().getString().toLowerCase();
            for (String wanted : names) {
                wanted = wanted.trim();
                if (!wanted.isEmpty() && name.contains(wanted)) return true;
            }
        }

        return false;
    }

    private boolean inLavaArea(int x, int z) {
        if (lavaScope.get() != LavaScope.Areas) return true;

        if (area1Enabled.get() && Math.hypot(x - area1X.get(), z - area1Z.get()) <= area1Radius.get()) return true;
        return area2Enabled.get() && Math.hypot(x - area2X.get(), z - area2Z.get()) <= area2Radius.get();
    }

    private static boolean isLava(net.minecraft.world.World world, BlockPos pos) {
        FluidState fluid = world.getFluidState(pos);
        return fluid.isIn(FluidTags.LAVA);
    }

    /** Safe = solid on top and not lava; the block above it must be open so you can actually stand there. */
    private static boolean isSafeFloor(net.minecraft.world.World world, BlockPos pos) {
        if (isLava(world, pos)) return false;
        if (!world.getBlockState(pos).isSideSolidFullSquare(world, pos, Direction.UP)) return false;

        BlockPos above = pos.up();
        return world.getBlockState(above).getCollisionShape(world, above).isEmpty() && !isLava(world, above);
    }

    @EventHandler
    private void onLavaTick(TickEvent.Post event) {
        if (!lavaOn() || mc.world == null || mc.player == null) {
            lavaEdges.clear();
            lavaCorners.clear();
            return;
        }

        if (++lavaTickCounter < lavaRebuildTicks.get()) return;
        lavaTickCounter = 0;

        if (lavaScope.get() == LavaScope.BossFights && !lavaBossNearby()) {
            lavaEdges.clear();
            lavaCorners.clear();
            return;
        }

        List<LavaEdge> edges = new ArrayList<>();
        List<LavaCorner> corners = new ArrayList<>();
        boolean diagonals = lavaDiagonalsEnabled.get();
        int range = lavaRange.get();
        BlockPos center = mc.player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos.Mutable neighbor = new BlockPos.Mutable();

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int x = center.getX() + dx, z = center.getZ() + dz;
                if (!inLavaArea(x, z)) continue;

                for (int dy = -4; dy <= 3; dy++) {
                    pos.set(x, center.getY() + dy, z);
                    if (!isLava(mc.world, pos)) continue;

                    // Only lava whose air above is open matters - buried lava can't be stepped into.
                    if (isLava(mc.world, pos.up())) continue;

                    for (Direction dir : Direction.Type.HORIZONTAL) {
                        neighbor.set(pos).move(dir);
                        if (isSafeFloor(mc.world, neighbor)) edges.add(new LavaEdge(pos.getX(), pos.getY(), pos.getZ(), dir));
                    }

                    if (diagonals) {
                        for (int cdx = -1; cdx <= 1; cdx += 2) {
                            for (int cdz = -1; cdz <= 1; cdz += 2) {
                                neighbor.set(pos.getX() + cdx, pos.getY(), pos.getZ() + cdz);
                                if (isSafeFloor(mc.world, neighbor)) corners.add(new LavaCorner(pos.getX(), pos.getY(), pos.getZ(), cdx, cdz));
                            }
                        }
                    }
                }
            }
        }

        lavaEdges.clear();
        lavaEdges.addAll(edges);
        lavaCorners.clear();
        lavaCorners.addAll(corners);
    }

    @EventHandler
    private void onRenderLava(Render3DEvent event) {
        if (!lavaOn() || (lavaEdges.isEmpty() && lavaCorners.isEmpty())) return;

        Color bottom = lavaBaseColor.get();
        Color top = lavaTopColor.get();
        double h = lavaWallHeight.get();

        lavaRenderer.begin();

        for (LavaEdge edge : lavaEdges) {
            // The shared face between the lava block and its safe neighbour, lifted to sit on the
            // floor's top (y + 1) and rising from there.
            double y0 = edge.y() + 1, y1 = y0 + h;
            double x0 = edge.x(), z0 = edge.z();
            double ax, az, bx, bz;

            switch (edge.dir()) {
                case EAST -> { ax = x0 + 1; az = z0;     bx = x0 + 1; bz = z0 + 1; }
                case WEST -> { ax = x0;     az = z0;     bx = x0;     bz = z0 + 1; }
                case SOUTH -> { ax = x0;    az = z0 + 1; bx = x0 + 1; bz = z0 + 1; }
                default -> { ax = x0;       az = z0;     bx = x0 + 1; bz = z0; } // NORTH
            }

            lavaRenderer.quad(ax, y0, az, ax, y1, az, bx, y1, bz, bx, y0, bz, top, top, bottom, bottom);
            lavaRenderer.line(ax, y0, az, bx, y0, bz, bottom);

            if (lavaSafeTop.get()) {
                int nx = edge.x() + edge.dir().getOffsetX(), nz = edge.z() + edge.dir().getOffsetZ();
                Color safe = lavaSafeColor.get();
                lavaRenderer.quad(nx, y0 + 0.01, nz, nx, y0 + 0.01, nz + 1, nx + 1, y0 + 0.01, nz + 1, nx + 1, y0 + 0.01, nz, safe);
            }
        }

        if (lavaDiagonalsEnabled.get()) {
            Color diagonal = lavaDiagonalColor.get();

            for (LavaCorner corner : lavaCorners) {
                // The single point the lava tile and its diagonal-safe neighbor actually share -
                // whichever of the tile's 4 corners dx/dz points toward.
                double cx = corner.x() + (corner.dx() > 0 ? 1 : 0);
                double cz = corner.z() + (corner.dz() > 0 ? 1 : 0);
                double y0 = corner.y() + 1, y1 = y0 + h;

                lavaRenderer.line(cx, y0, cz, cx, y1, cz, diagonal);
            }
        }

        lavaRenderer.end();
        lavaRenderer.render(event.matrices);
    }

    private boolean fireBossNearby() {
        String[] names = fireBosses.get().toLowerCase().split(",");
        double rangeSq = fireBossRange.get() * fireBossRange.get();

        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity) continue;
            if (entity.squaredDistanceTo(mc.player) > rangeSq) continue;

            String name = entity.getName().getString().toLowerCase();
            for (String wanted : names) {
                wanted = wanted.trim();
                if (!wanted.isEmpty() && name.contains(wanted)) return true;
            }
        }

        return false;
    }

    private boolean inFireArea(int x, int z) {
        if (fireScope.get() != FireScope.Areas) return true;

        if (fireArea1Enabled.get() && Math.hypot(x - fireArea1X.get(), z - fireArea1Z.get()) <= fireArea1Radius.get()) return true;
        return fireArea2Enabled.get() && Math.hypot(x - fireArea2X.get(), z - fireArea2Z.get()) <= fireArea2Radius.get();
    }

    private static boolean isFire(net.minecraft.world.World world, BlockPos pos) {
        net.minecraft.block.BlockState state = world.getBlockState(pos);
        return state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE);
    }

    /**
     * Safe = you could actually stand right here. Unlike lava (a fluid that occupies the floor tile
     * itself, so isSafeFloor checks pos itself for solidity), fire is a non-solid block sitting ON TOP
     * of a normal floor - so this checks the block BELOW pos for solidity instead, and pos itself
     * (where your feet/body would be) for being open and not on fire.
     */
    private static boolean isSafeStand(net.minecraft.world.World world, BlockPos pos) {
        if (isFire(world, pos)) return false;
        if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) return false;

        BlockPos below = pos.down();
        if (!world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)) return false;

        BlockPos above = pos.up();
        return world.getBlockState(above).getCollisionShape(world, above).isEmpty() && !isFire(world, above);
    }

    @EventHandler
    private void onFireTick(TickEvent.Post event) {
        if (!fireOn() || mc.world == null || mc.player == null) {
            fireEdges.clear();
            return;
        }

        if (++fireTickCounter < fireRebuildTicks.get()) return;
        fireTickCounter = 0;

        if (fireScope.get() == FireScope.BossFights && !fireBossNearby()) {
            fireEdges.clear();
            return;
        }

        List<FireEdge> edges = new ArrayList<>();
        int range = fireRange.get();
        BlockPos center = mc.player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos.Mutable neighbor = new BlockPos.Mutable();

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int x = center.getX() + dx, z = center.getZ() + dz;
                if (!inFireArea(x, z)) continue;

                for (int dy = -4; dy <= 3; dy++) {
                    pos.set(x, center.getY() + dy, z);
                    if (!isFire(mc.world, pos)) continue;

                    for (Direction dir : Direction.Type.HORIZONTAL) {
                        neighbor.set(pos).move(dir);
                        if (isSafeStand(mc.world, neighbor)) edges.add(new FireEdge(pos.getX(), pos.getY(), pos.getZ(), dir));
                    }
                }
            }
        }

        fireEdges.clear();
        fireEdges.addAll(edges);
    }

    @EventHandler
    private void onRenderFire(Render3DEvent event) {
        if (!fireOn() || fireEdges.isEmpty()) return;

        Color bottom = fireBaseColor.get();
        Color top = fireTopColor.get();
        double h = fireWallHeight.get();

        fireRenderer.begin();

        for (FireEdge edge : fireEdges) {
            // Fire sits at the SAME y-level as the space you'd stand in (unlike lava's y+1), so the
            // wall base is just edge.y() - no +1 offset.
            double y0 = edge.y(), y1 = y0 + h;
            double x0 = edge.x(), z0 = edge.z();
            double ax, az, bx, bz;

            switch (edge.dir()) {
                case EAST -> { ax = x0 + 1; az = z0;     bx = x0 + 1; bz = z0 + 1; }
                case WEST -> { ax = x0;     az = z0;     bx = x0;     bz = z0 + 1; }
                case SOUTH -> { ax = x0;    az = z0 + 1; bx = x0 + 1; bz = z0 + 1; }
                default -> { ax = x0;       az = z0;     bx = x0 + 1; bz = z0; } // NORTH
            }

            fireRenderer.quad(ax, y0, az, ax, y1, az, bx, y1, bz, bx, y0, bz, top, top, bottom, bottom);
            fireRenderer.line(ax, y0, az, bx, y0, bz, bottom);

            if (fireSafeTop.get()) {
                int nx = edge.x() + edge.dir().getOffsetX(), nz = edge.z() + edge.dir().getOffsetZ();
                Color safe = fireSafeColor.get();
                fireRenderer.quad(nx, y0 + 0.01, nz, nx, y0 + 0.01, nz + 1, nx + 1, y0 + 0.01, nz + 1, nx + 1, y0 + 0.01, nz, safe);
            }
        }

        fireRenderer.end();
        fireRenderer.render(event.matrices);
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ParticleS2CPacket packet)) return;

        Identifier id = Registries.PARTICLE_TYPE.getId(packet.getParameters().getType());
        if (id == null) return;

        String idStr = id.toString();
        long now = System.currentTimeMillis();
        Seen point = new Seen(packet.getX(), packet.getY(), packet.getZ(), now);

        if (bonesRingOn() && idStr.equals(particleType.get().trim()) && bonesPos != null) {
            double r = bonesParticleRange.get();

            if (bonesPos.squaredDistanceTo(packet.getX(), packet.getY(), packet.getZ()) <= r * r) {
                synchronized (seen) { seen.add(point); }
            }
        }

        if (pulsarPresent && pulsarOn() && idStr.equals(pulsarParticle.get().trim())) {
            synchronized (pulsarSeen) { pulsarSeen.add(point); }
        }

        if (sirensPresent && sirensLineEnabled.get()) {
            for (String wanted : sirensLineParticle.get().split(",")) {
                if (idStr.equals(wanted.trim())) {
                    synchronized (sirensLineSeen) { sirensLineSeen.add(point); }
                    break;
                }
            }
        }

        String aegisId = aegisParticle.get().trim();
        // Only trusted once the Aegis has actually been identified by name at least once - anchoring
        // this to the PLAYER's own position instead whenever it hadn't (the old behaviour) meant any
        // other entity/mechanic emitting the same particle type near you (e.g. CG's invisible wither)
        // got mistaken for the Aegis, since nothing here distinguishes "near the real Aegis" from
        // "near me". The real mob is name-matchable via trackAegis() whenever it exists (Aegis Always
        // covers the invisible-flagged case), so this fallback only needs to bridge gaps after that.
        if (aegisOn() && !aegisId.isEmpty() && idStr.equals(aegisId) && aegisKnown) {
            double rx = aegisX, rz = aegisZ;

            if (Math.hypot(packet.getX() - rx, packet.getZ() - rz) <= aegisParticleRadius.get()) {
                long prev = aegisParticleMs;
                aegisParticleX = prev != 0 && now - prev < 800 ? aegisParticleX + (packet.getX() - aegisParticleX) * 0.4 : packet.getX();
                aegisParticleY = prev != 0 && now - prev < 800 ? aegisParticleY + (packet.getY() - aegisParticleY) * 0.4 : packet.getY();
                aegisParticleZ = prev != 0 && now - prev < 800 ? aegisParticleZ + (packet.getZ() - aegisParticleZ) * 0.4 : packet.getZ();
                aegisParticleMs = now;
            }
        }

        String reaperId = reaperParticle.get().trim();
        // Same guard as Aegis above: only trusted once the Reaper's been identified by name at least
        // once, so it can't get mistaken for some other entity/mechanic emitting the same particle
        // type near you.
        if (reaperOn() && !reaperId.isEmpty() && idStr.equals(reaperId) && reaperKnown) {
            double rx = reaperX, rz = reaperZ;

            if (Math.hypot(packet.getX() - rx, packet.getZ() - rz) <= reaperParticleRadius.get()) {
                long prev = reaperParticleMs;
                reaperParticleX = prev != 0 && now - prev < 800 ? reaperParticleX + (packet.getX() - reaperParticleX) * 0.4 : packet.getX();
                reaperParticleY = prev != 0 && now - prev < 800 ? reaperParticleY + (packet.getY() - reaperParticleY) * 0.4 : packet.getY();
                reaperParticleZ = prev != 0 && now - prev < 800 ? reaperParticleZ + (packet.getZ() - reaperParticleZ) * 0.4 : packet.getZ();
                reaperParticleMs = now;
            }
        }

        if (cgPresent && aoeOn() && idStr.equals(cgAoeParticle.get().trim())) {
            synchronized (aoeSeen) { aoeSeen.add(point); }
        }

        if (cgPresent && bombOn()) {
            if (packet.getParameters() instanceof BlockStateParticleEffect bs && isBombWool(bs.getBlockState().getBlock())) {
                // Recorded fight: the flight trail is count 6 (and follows the targeted player); the blast itself
                // is an exact radius-6 ring of count-1 particles that lasts ~1.3s. Ring points must not count as bombs.
                if (packet.getCount() == 5 && packet.getSpeed() == 0 && bombPathEnabled.get()) {
                    // Floor-bomb path preview: count-5 particles laid along the floor, ~0.8 apart, in travel order.
                    net.minecraft.block.Block wool = bs.getBlockState().getBlock();
                    synchronized (bombPaths) {
                        BombPath target = null;
                        double best = 2.0;
                        for (BombPath path : bombPaths) {
                            if (path.wool != wool) continue;
                            // Must also be a live continuation of THIS path, not just spatially close -
                            // floor bombs repeat the same lanes, so a brand new bomb's first particle
                            // often lands right where the PREVIOUS bomb's path ended. Without a recency
                            // check, that new bomb's points kept getting appended to the old bomb's
                            // already-finished path instead of starting a fresh one - merging every
                            // same-color bomb for the rest of the fight into one ever-growing path that
                            // never went stale (its lifetime timer kept getting pushed back by the next
                            // bomb), which is why old trails never disappeared.
                            if (point.time() - path.last > 600) continue;
                            double[] end = path.pts.get(path.pts.size() - 1);
                            double d = Math.hypot(end[0] - point.x(), end[2] - point.z());
                            if (d < best) { best = d; target = path; }
                        }
                        if (target == null) { target = new BombPath(wool); bombPaths.add(target); }
                        target.pts.add(new double[]{point.x(), point.y(), point.z()});
                        target.last = point.time();
                    }
                } else if (packet.getCount() > 1) {
                    synchronized (bombs) { addBombSample(bs.getBlockState().getBlock(), point); }
                } else {
                    synchronized (bombRingSeen) { bombRingSeen.add(new BombRingPoint(bs.getBlockState().getBlock(), point)); }
                }
            }

            if (idStr.equals(bombInnerParticle.get().trim())) {
                synchronized (bombInnerSeen) { bombInnerSeen.add(point); }
            }
        }
    }

    /**
     * Stricter than entityNearby() for one reason: the recorded fights show Cosmic Gatekeeper's OWN
     * nametag telegraphing "... Spawning: X" (Pulsar, Siren, ...) well before (and for a while
     * independent of) the real mob actually existing. A plain contains() match catches the boss's
     * telegraph text too - so presence tracking used to go true off the boss's own status line
     * rather than the real mob.
     *
     * Originally "fixed" (for Pulsar) by requiring the name to START WITH the needle instead - broke
     * the very next fight, because the real mob's own name picks up a difficulty-tier prefix
     * ("Nightmare Pulsar <hp>" on harder difficulties, confirmed the same thing happens to Sirens ->
     * "Nightmare Siren"), which doesn't start with "pulsar" either. Excluding telegraph text
     * specifically (it always contains "spawning:") is the actual distinguishing feature, and doesn't
     * care what prefix the real mob's name has at any difficulty - shared here since Pulsar, Aegis,
     * Reaper and Sirens all have the exact same false-positive risk.
     */
    private boolean summonedMobNearby(String name, double range) {
        if (mc.world == null || mc.player == null) return false;

        String needle = name.trim().toLowerCase();
        if (needle.isEmpty()) return false;

        double rangeSq = range * range;
        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity) continue;
            if (entity.squaredDistanceTo(mc.player) > rangeSq) continue;

            String entityName = entity.getName().getString().trim().toLowerCase();
            if (!entityName.contains(needle)) continue;
            if (entityName.contains("spawning:")) continue;

            return true;
        }

        return false;
    }

    /** Position of the first nearby entity matching name, or null if none within a generous search radius. */
    private net.minecraft.util.math.Vec3d findEntityPos(String name, double searchRange) {
        if (mc.world == null || mc.player == null) return null;

        String needle = name.trim().toLowerCase();
        if (needle.isEmpty()) return null;

        double rangeSq = searchRange * searchRange;
        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity) continue;
            if (mc.player.squaredDistanceTo(entity) > rangeSq) continue;
            if (!entity.getName().getString().trim().toLowerCase().contains(needle)) continue;

            return entity.getPos();
        }

        return null;
    }

    private boolean entityNearby(String name, double range) {
        if (mc.world == null || mc.player == null) return false;

        String needle = name.trim().toLowerCase();
        if (needle.isEmpty()) return false;

        double rangeSq = range * range;
        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity) continue;
            if (entity.squaredDistanceTo(mc.player) > rangeSq) continue;
            if (entity.getName().getString().toLowerCase().contains(needle)) return true;
        }

        return false;
    }

    private void trackAegis(long now) {
        aegisEntityHidden = false;
        if (!aegisOn() || mc.world == null || mc.player == null) { aegisKnown = false; return; }

        String needle = aegisName.get().trim().toLowerCase();
        double rangeSq = aegisRange.get() * aegisRange.get();
        net.minecraft.entity.Entity found = null;

        if (!needle.isEmpty()) {
            for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
                if (entity instanceof net.minecraft.entity.player.PlayerEntity) continue;
                if (entity.squaredDistanceTo(mc.player) > rangeSq) continue;

                // Excludes Cosmic Gatekeeper's own "Spawning: X" telegraph text - confirmed to
                // falsely match Pulsar's and the Siren's own name checks the same way, since the
                // boss's status line mentions whatever it's about to summon next.
                String entityName = entity.getName().getString().toLowerCase();
                if (!entityName.contains(needle)) continue;
                if (entityName.contains("spawning:")) continue;

                // Prefer the real mob over a floating name-tag armor stand.
                if (found == null || (found instanceof net.minecraft.entity.decoration.ArmorStandEntity && !(entity instanceof net.minecraft.entity.decoration.ArmorStandEntity))) found = entity;
            }
        }

        if (found != null) {
            aegisX = found.getX(); aegisY = found.getY(); aegisZ = found.getZ();
            aegisKnown = true;
            aegisLastSeenMs = now;
            aegisEntityHidden = found.isInvisible() || aegisAlways.get();
        } else if (aegisKnown && now - aegisLastSeenMs > aegisMemory.get() + 5000) {
            aegisKnown = false;
        }
    }

    private void trackReaper(long now) {
        reaperEntityHidden = false;
        if (!reaperOn() || mc.world == null || mc.player == null) { reaperKnown = false; return; }

        String needle = reaperName.get().trim().toLowerCase();
        double rangeSq = reaperRange.get() * reaperRange.get();
        net.minecraft.entity.Entity found = null;

        if (!needle.isEmpty()) {
            for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
                if (entity instanceof net.minecraft.entity.player.PlayerEntity) continue;
                if (entity.squaredDistanceTo(mc.player) > rangeSq) continue;

                // Excludes Cosmic Gatekeeper's own "Spawning: X" telegraph text - see trackAegis.
                String entityName = entity.getName().getString().toLowerCase();
                if (!entityName.contains(needle)) continue;
                if (entityName.contains("spawning:")) continue;

                // Prefer the real mob over a floating name-tag armor stand.
                if (found == null || (found instanceof net.minecraft.entity.decoration.ArmorStandEntity && !(entity instanceof net.minecraft.entity.decoration.ArmorStandEntity))) found = entity;
            }
        }

        if (found != null) {
            reaperX = found.getX(); reaperY = found.getY(); reaperZ = found.getZ();
            reaperKnown = true;
            reaperLastSeenMs = now;
            reaperEntityHidden = found.isInvisible() || reaperAlways.get();
        } else if (reaperKnown && now - reaperLastSeenMs > reaperMemory.get() + 5000) {
            reaperKnown = false;
        }
    }

    private boolean cgBossNearby() {
        if (mc.world == null || mc.player == null) return false;
        if (!aoeOn() && !bombOn()) return false;

        String needle = cgBossName.get().trim().toLowerCase();
        if (needle.isEmpty()) return false;

        double rangeSq = cgBossRange.get() * cgBossRange.get();

        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity) continue;
            if (entity.squaredDistanceTo(mc.player) > rangeSq) continue;
            if (entity.getName().getString().toLowerCase().contains(needle)) return true;
        }

        return false;
    }

    private static boolean isBombWool(net.minecraft.block.Block block) {
        return block == Blocks.CYAN_WOOL || block == Blocks.RED_WOOL || block == Blocks.YELLOW_WOOL;
    }

    /** Caller holds the bombs lock. Wool particles of one color that land close together are one bomb. */
    private void addBombSample(net.minecraft.block.Block wool, Seen p) {
        double merge = bombMergeRadius.get();

        Bomb nearest = null;
        double best = Double.MAX_VALUE;
        for (Bomb b : bombs) {
            if (b.wool != wool) continue;
            double d = Math.hypot(b.x - p.x(), b.z - p.z());
            if (d <= merge && d < best) { best = d; nearest = b; }
        }

        if (nearest != null) nearest.add(p.x(), p.y(), p.z(), p.time());
        else bombs.add(new Bomb(wool, p.x(), p.y(), p.z(), p.time()));
    }

    // Bones's own position, cached once per tick instead of re-scanning the world for every single
    // angry-particle packet - this particle type is common enough (Bones's own ring, Vacuum, elite
    // ground pound) that a full world scan per particle would repeat the same per-particle world-
    // query cost already fixed once this session for Near Entities hit detection.
    private net.minecraft.util.math.Vec3d bonesPos;

    @EventHandler
    private void onTick(TickEvent.Post event) {
        long now = System.currentTimeMillis();

        bonesPos = bonesRingOn() ? findEntityPos(bonesName.get(), maxRadius.get() * 3) : null;

        pulsarPresent = pulsarOn() && (pulsarName.get().trim().isEmpty() || summonedMobNearby(pulsarName.get(), pulsarRange.get()));
        if (!pulsarPresent) synchronized (pulsarSeen) { pulsarSeen.clear(); }
        synchronized (pulsarSeen) { pulsarSeen.removeIf(sn -> sn.time() < now - pulsarLifetime.get()); }

        sirensPresent = sirensEnabled.get() && summonedMobNearby(sirensName.get(), sirensRange.get());
        if (!sirensPresent) synchronized (sirensLineSeen) { sirensLineSeen.clear(); }
        synchronized (sirensLineSeen) { sirensLineSeen.removeIf(sn -> sn.time() < now - sirensLineLifetime.get()); }

        trackAegis(now);
        trackReaper(now);

        cgPresent = cgBossNearby();
        if (!cgPresent) {
            synchronized (aoeSeen) { aoeSeen.clear(); }
            synchronized (bombs) { bombs.clear(); }
            synchronized (bombRingSeen) { bombRingSeen.clear(); }
            synchronized (bombPaths) { bombPaths.clear(); }
            synchronized (bombInnerSeen) { bombInnerSeen.clear(); }
        }

        long cutoff = now - lifetimeMs.get();
        synchronized (seen) { seen.removeIf(s -> s.time() < cutoff); }

        long aoeCutoff = now - cgAoeLifetime.get();
        synchronized (aoeSeen) { aoeSeen.removeIf(s -> s.time() < aoeCutoff); }

        long bombCutoff = now - bombTimeout.get();
        synchronized (bombs) { bombs.removeIf(b -> b.lastSeen < bombCutoff); }
        synchronized (bombRingSeen) { bombRingSeen.removeIf(r -> r.p().time() < now - 500); }
        synchronized (bombPaths) { bombPaths.removeIf(path -> path.last < now - bombPathLifetime.get()); }
        synchronized (bombInnerSeen) { bombInnerSeen.removeIf(s -> s.time() < now - 1500); }
    }

    /**
     * Least-squares circle through the particles' xz (algebraic / Kasa fit): minimises
     * sum((x^2 + z^2 + D x + E z + F)^2), giving centre (-D/2, -E/2) and radius sqrt(D^2/4 + E^2/4 - F).
     * Unlike "mean of the points, mean distance", this stays accurate when only part of the ring has
     * spawned so far. Returns {cx, cz, r}, or null if the points are (near) collinear.
     */
    private static double[] fitCircle(List<Seen> pts) {
        double n = pts.size();
        double sx = 0, sz = 0, sxx = 0, szz = 0, sxz = 0, sxr = 0, szr = 0, sr = 0;

        for (Seen p : pts) {
            double x = p.x(), z = p.z(), r2 = x * x + z * z;
            sx += x; sz += z; sxx += x * x; szz += z * z; sxz += x * z;
            sxr += x * r2; szr += z * r2; sr += r2;
        }

        // Solve [sxx sxz sx; sxz szz sz; sx sz n] [D E F]^T = -[sxr szr sr]^T by Cramer's rule.
        double det = sxx * (szz * n - sz * sz) - sxz * (sxz * n - sz * sx) + sx * (sxz * sz - szz * sx);
        if (Math.abs(det) < 1e-9) return null;

        double b0 = -sxr, b1 = -szr, b2 = -sr;
        double dD = b0 * (szz * n - sz * sz) - sxz * (b1 * n - sz * b2) + sx * (b1 * sz - szz * b2);
        double dE = sxx * (b1 * n - sz * b2) - b0 * (sxz * n - sz * sx) + sx * (sxz * b2 - b1 * sx);
        double dF = sxx * (szz * b2 - b1 * sz) - sxz * (sxz * b2 - b1 * sx) + b0 * (sxz * sz - szz * sx);

        double D = dD / det, E = dE / det, F = dF / det;
        double rr = D * D / 4 + E * E / 4 - F;
        if (rr <= 0) return null;

        return new double[]{-D / 2, -E / 2, Math.sqrt(rr)};
    }

    /** Newest burst of particles fitted to a circle: {cx, cz, r, minY}, or null if there isn't a good ring. */
    private static volatile String lastReject = "";

    private static double[] fitLatestBurst(List<Seen> all, int minCount, int windowMs, double minR, double maxR) {
        lastReject = "";
        List<Seen> snapshot;
        synchronized (all) {
            if (all.size() < minCount) return null;
            snapshot = new ArrayList<>(all);
        }

        long newest = 0;
        for (Seen sn : snapshot) newest = Math.max(newest, sn.time());

        List<Seen> kept = new ArrayList<>();
        for (Seen sn : snapshot) if (sn.time() >= newest - windowMs) kept.add(sn);
        if (kept.size() < minCount) { lastReject = "only " + kept.size() + " particles in the newest burst (need " + minCount + ")"; return null; }

        double[] fit = fitCircle(kept);
        if (fit == null) { lastReject = "the particles are in a line, not a ring"; return null; }

        double cx = fit[0], cz = fit[1], r = fit[2];
        if (r < minR || r > maxR) { lastReject = String.format("fitted ring radius %.1f is outside Min/Max Radius (%.1f-%.1f)", r, minR, maxR); return null; }

        // A real ring's points all sit near the fitted radius - a scattered clump doesn't.
        double sumSq = 0, minY = Double.MAX_VALUE;
        for (Seen sn : kept) {
            double d = Math.hypot(sn.x() - cx, sn.z() - cz) - r;
            sumSq += d * d;
            minY = Math.min(minY, sn.y());
        }
        if (Math.sqrt(sumSq / kept.size()) > Math.max(0.75, r * 0.2)) { lastReject = String.format("%d particles, radius %.1f, but they don't form a clean ring (scattered)", kept.size(), r); return null; }

        return new double[]{cx, cz, r, minY};
    }

    private void drawWall(double cx, double cz, double r, double y0, double height, Color bottom, Color top) {
        double y1 = y0 + height;
        int n = segments.get();

        double px = cx + r, pz = cz;
        for (int i = 1; i <= n; i++) {
            double angle = i * 2 * Math.PI / n;
            double x = cx + Math.cos(angle) * r;
            double z = cz + Math.sin(angle) * r;

            // Vertex order is (bottom-left, top-left, top-right, bottom-right); the color params are
            // (topLeft, topRight, bottomRight, bottomLeft) - bottom edge gets the base color, top edge
            // the top color, a clean vertical gradient.
            cur.quad(px, y0, pz, px, y1, pz, x, y1, z, x, y0, z, top, top, bottom, bottom);

            if (baseLine.get()) cur.line(px, y0, pz, x, y0, z, bottom);

            px = x;
            pz = z;
        }
    }

    /** Two crossed vertical planes - a pillar that reads from any angle. */
    private void drawPillar(double x, double y0, double z, double height, Color bottom, Color top) {
        double w = 0.25, y1 = y0 + height;
        cur.quad(x - w, y0, z, x - w, y1, z, x + w, y1, z, x + w, y0, z, top, top, bottom, bottom);
        cur.quad(x, y0, z - w, x, y1, z - w, x, y1, z + w, x, y0, z + w, top, top, bottom, bottom);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        ring.begin();
        ringSolid.begin();
        cur = pick("bones");

        // Bones: the ring follows his angry-particle ring.
        double[] bones = bonesRingOn() ? fitLatestBurst(seen, minParticles.get(), burstWindow.get(), minRadius.get(), maxRadius.get()) : null;

        if (bones != null) drawWall(bones[0], bones[1], bones[2], bones[3] + yOffset.get(), ringHeight.get(), baseColor.get(), topColor.get());

        cur = pick("cg-aoe");
        // Cosmic Gatekeeper AOE: follows the enchanted-hit circle.
        if (cgPresent && aoeOn()) {
            double[] aoe = fitLatestBurst(aoeSeen, cgAoeMinParticles.get(), cgAoeBurstWindow.get(), cgAoeMinRadius.get(), cgAoeMaxRadius.get());
            if (aoe != null) drawWall(aoe[0], aoe[1], aoe[2], aoe[3], ringHeight.get(), cgAoeBaseColor.get(), cgAoeTopColor.get());
        }

        cur = pick("bombs");
        if (cgPresent && bombOn()) {
            drawBombs();
            if (bombPathEnabled.get()) drawBombPaths();
        }

        cur = pick("pulsar");
        if (pulsarPresent && pulsarOn()) {
            double[] pulsar = fitLatestBurst(pulsarSeen, pulsarMinParticles.get(), pulsarBurstWindow.get(), pulsarMinRadius.get(), pulsarMaxRadius.get());
            if (pulsar != null) {
                double r = pulsarDrawRadius.get() > 0 ? pulsarDrawRadius.get() : pulsar[2];
                double y0 = pulsarOnGround.get() ? groundBelow(pulsar[0], pulsar[3], pulsar[1], 40) : pulsar[3];
                drawWall(pulsar[0], pulsar[1], r, y0, ringHeight.get(), pulsarBaseColor.get(), pulsarTopColor.get());
            }
        }

        cur = pick("aegis");
        if (aegisOn()) drawAegis();

        cur = pick("reaper");
        if (reaperOn()) drawReaper();

        if (sirensEnabled.get()) drawSirens(event);
        if (sirensLineEnabled.get()) drawSirensLine();

        cur = pick("amal-adds");
        if (amalAddsEnabled.get()) drawAmalAdds(event);

        cur = pick("obelisk");
        if (obeliskEnabled.get()) drawObelisks(event);

        ring.end();
        ringSolid.end();

        // Blockers first (they write depth), then the see-through rings, which test against that depth but write none -
        // so they neither hide each other nor hide anything behind them.
        ringSolid.render(event.matrices);
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        ring.render(event.matrices);
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
    }

    private void drawSirens(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        String needle = sirensName.get().trim().toLowerCase();
        if (needle.isEmpty()) return;

        double rangeSq = sirensRange.get() * sirensRange.get();
        SettingColor c = sirensColor.get();
        Color top = new Color(c.r, c.g, c.b, 0);

        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof net.minecraft.entity.LivingEntity living) || entity instanceof net.minecraft.entity.player.PlayerEntity) continue;

            // The real Sirens (the 3 stationary adds this marks) are wither_skeleton entities named
            // "Nightmare Siren". A SEPARATE single wither entity named "Nightmare Sirens VII <hp> ..."
            // exists purely to drive the encounter's boss bar - constantly teleports around the arena,
            // and isn't a real enemy at all, but its name also contains "siren" so a plain text match
            // caught it too. Excluded by entity type, not name, since that's the actual distinction -
            // no difficulty-tier name prefix can ever confuse the two this way.
            if (living.getType() == net.minecraft.entity.EntityType.WITHER) continue;

            String entityName = living.getName().getString().trim().toLowerCase();
            if (!entityName.contains(needle)) continue;
            if (entityName.contains("spawning:")) continue;

            if (mc.player.squaredDistanceTo(living) > rangeSq) continue;

            net.minecraft.util.math.Vec3d pos = living.getLerpedPos(event.tickDelta);
            drawPillar(pos.x, pos.y, pos.z, sirensPillarHeight.get(), c, top);
            if (sirensRingRadius.get() > 0) drawWall(pos.x, pos.z, sirensRingRadius.get(), pos.y, 0.6, c, top);
        }
    }

    /** Which add-marker color (if any) a nearby living entity's name matches, checked against each of the 4 configured add names in turn. */
    private SettingColor matchAmalAdd(String name) {
        String n = name.toLowerCase();

        String needle = tarantulaName.get().trim().toLowerCase();
        if (!needle.isEmpty() && n.contains(needle)) return tarantulaColor.get();

        needle = stalkerName.get().trim().toLowerCase();
        if (!needle.isEmpty() && n.contains(needle)) return stalkerColor.get();

        needle = toxicSpecimenName.get().trim().toLowerCase();
        if (!needle.isEmpty() && n.contains(needle)) return toxicSpecimenColor.get();

        needle = menderName.get().trim().toLowerCase();
        if (!needle.isEmpty() && n.contains(needle)) return menderColor.get();

        return null;
    }

    private void drawAmalAdds(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        double rangeSq = amalAddsRange.get() * amalAddsRange.get();
        double pillarHeight = amalAddsPillarHeight.get();

        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof net.minecraft.entity.LivingEntity living) || entity instanceof net.minecraft.entity.player.PlayerEntity) continue;
            if (mc.player.squaredDistanceTo(living) > rangeSq) continue;

            SettingColor c = matchAmalAdd(living.getName().getString().trim());
            if (c == null) continue;

            Color top = new Color(c.r, c.g, c.b, 0);
            net.minecraft.util.math.Vec3d pos = living.getLerpedPos(event.tickDelta);
            drawPillar(pos.x, pos.y, pos.z, pillarHeight, c, top);
        }
    }

    /** Obelisks are a minecraft:item entity (not a LivingEntity) - plain Entity + name match, same as Pulsar's own "720❤ Pulsar" item-entity marker. */
    private void drawObelisks(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        String needle = obeliskName.get().trim().toLowerCase();
        if (needle.isEmpty()) return;

        double rangeSq = obeliskRange.get() * obeliskRange.get();
        SettingColor fallback = obeliskColor.get();
        double standMatchRangeSq = 3.0 * 3.0;

        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity) continue;
            if (mc.player.squaredDistanceTo(entity) > rangeSq) continue;

            String name = entity.getName().getString().trim().toLowerCase();
            if (!name.contains(needle)) continue;

            net.minecraft.util.math.Vec3d pos = entity.getLerpedPos(event.tickDelta);

            // Each Obelisk's own color isn't on this item entity itself (it has no style of its
            // own - just plain HP text) - it's a separate, invisible "Obelisk" armor stand nearby
            // whose displayed name is colored per-Obelisk server-side. Read that live each frame
            // (not cached - confirmed via recording that FightRecorder's own entity snapshots
            // strip all style/color off a name, keeping only plain text, so there's no way to get
            // this color from recorded data; it only exists on the real, currently-loaded entity).
            int styleRgb = nearbyNamedStandColor(pos, needle, standMatchRangeSq);
            Color c = styleRgb != -1 ? new Color((styleRgb >> 16) & 0xFF, (styleRgb >> 8) & 0xFF, styleRgb & 0xFF, fallback.a) : fallback;
            Color top = new Color(c.r, c.g, c.b, 0);

            drawPillar(pos.x, pos.y, pos.z, obeliskPillarHeight.get(), c, top);
        }
    }

    /** First matching nearby invisible armor stand's own text color (packed RGB, no alpha), or -1 if none found or it has no explicit color. */
    private int nearbyNamedStandColor(net.minecraft.util.math.Vec3d pos, String needle, double matchRangeSq) {
        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ArmorStandEntity) || !entity.isInvisible()) continue;
            if (entity.squaredDistanceTo(pos.x, pos.y, pos.z) > matchRangeSq) continue;

            Text name = entity.getName();
            if (!name.getString().trim().toLowerCase().contains(needle)) continue;

            int[] color = {-1};
            name.visit((style, asString) -> {
                if (style.getColor() != null) color[0] = style.getColor().getRgb();
                return Optional.empty();
            }, Style.EMPTY);

            if (color[0] != -1) return color[0];
        }

        return -1;
    }

    private void drawSirensLine() {
        List<Seen> pts;
        synchronized (sirensLineSeen) { pts = new ArrayList<>(sirensLineSeen); }
        if (pts.size() < 2) return;

        pts.sort((a, b) -> Long.compare(a.time(), b.time()));

        cur = pick("sirens-line");

        long now = System.currentTimeMillis();
        int lifetime = Math.max(1, sirensLineLifetime.get());
        SettingColor c = sirensLineColor.get();

        for (int i = 1; i < pts.size(); i++) {
            Seen a = pts.get(i - 1), b = pts.get(i);

            double alphaScale = MathHelper.clamp(1 - (now - b.time()) / (double) lifetime, 0, 1);
            Color segColor = new Color(c.r, c.g, c.b, (int) (c.a * alphaScale));

            cur.line(a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), segColor);
        }
    }

    private void drawAegis() {
        long now = System.currentTimeMillis();
        SettingColor c = aegisColor.get();
        Color top = new Color(c.r, c.g, c.b, 0);

        double x, y, z;

        if (aegisParticleMs != 0 && now - aegisParticleMs < 700) {
            x = aegisParticleX; y = aegisParticleY - 0.5; z = aegisParticleZ;
        } else if (aegisKnown && aegisEntityHidden) {
            x = aegisX; y = aegisY; z = aegisZ;
        } else if (aegisKnown && !aegisEntityHidden && now - aegisLastSeenMs < 0) {
            return;
        } else if (aegisKnown && now - Math.max(aegisLastSeenMs, aegisParticleMs) < aegisMemory.get() && aegisLastSeenMs != 0 && !entityStillVisibleAtLastSpot()) {
            x = aegisX; y = aegisY; z = aegisZ;
        } else {
            return;
        }

        drawPillar(x, y, z, aegisPillarHeight.get(), c, top);
        if (aegisRingRadius.get() > 0) drawWall(x, z, aegisRingRadius.get(), y, 0.6, c, top);
    }

    /** True while the mob is present and visible right now - then there's nothing to mark (unless Always). */
    private boolean entityStillVisibleAtLastSpot() {
        return System.currentTimeMillis() - aegisLastSeenMs < 100 && !aegisEntityHidden;
    }

    private void drawReaper() {
        long now = System.currentTimeMillis();
        SettingColor c = reaperColor.get();
        Color top = new Color(c.r, c.g, c.b, 0);

        double x, y, z;

        if (reaperParticleMs != 0 && now - reaperParticleMs < 700) {
            x = reaperParticleX; y = reaperParticleY - 0.5; z = reaperParticleZ;
        } else if (reaperKnown && reaperEntityHidden) {
            x = reaperX; y = reaperY; z = reaperZ;
        } else if (reaperKnown && !reaperEntityHidden && now - reaperLastSeenMs < 0) {
            return;
        } else if (reaperKnown && now - Math.max(reaperLastSeenMs, reaperParticleMs) < reaperMemory.get() && reaperLastSeenMs != 0 && !reaperStillVisibleAtLastSpot()) {
            x = reaperX; y = reaperY; z = reaperZ;
        } else {
            return;
        }

        drawPillar(x, y, z, reaperPillarHeight.get(), c, top);
        if (reaperRingRadius.get() > 0) drawWall(x, z, reaperRingRadius.get(), y, 0.6, c, top);
    }

    /** True while the mob is present and visible right now - then there's nothing to mark (unless Always). */
    private boolean reaperStillVisibleAtLastSpot() {
        return System.currentTimeMillis() - reaperLastSeenMs < 100 && !reaperEntityHidden;
    }

    /** Top of the floor under a point (the flying bomb's trail is a couple of blocks up), or the point itself if there's none. */
    /**
     * Centers of every ring of a KNOWN radius in a pile of points, as {cx, cz, floorY}. The floor-bomb phases drop
     * several blasts of one color at once, overlapping - one circle fit over the mix gives garbage - so instead each
     * point votes for every center that would put it on a ring of that radius; real rings are the vote peaks.
     */
    private static List<double[]> findRings(List<Seen> all, double radius, int windowMs) {
        List<double[]> out = new ArrayList<>();
        if (all.size() < 12) return out;

        long newest = 0;
        for (Seen sn : all) newest = Math.max(newest, sn.time());

        List<Seen> pts = new ArrayList<>();
        for (Seen sn : all) if (sn.time() >= newest - windowMs) pts.add(sn);

        final double cell = 0.5;
        final int steps = 48;

        for (int ring = 0; ring < 8 && pts.size() >= 12; ring++) {
            java.util.HashMap<Long, Integer> votes = new java.util.HashMap<>();
            long bestKey = 0;
            int best = 0;

            for (Seen sn : pts) {
                for (int i = 0; i < steps; i++) {
                    double a = Math.PI * 2 * i / steps;
                    long gx = Math.round((sn.x() + Math.cos(a) * radius) / cell);
                    long gz = Math.round((sn.z() + Math.sin(a) * radius) / cell);
                    long key = (gx << 32) ^ (gz & 0xffffffffL);
                    int v = votes.merge(key, 1, Integer::sum);
                    if (v > best) { best = v; bestKey = key; }
                }
            }

            // A full ring of N points puts N votes on its center; lone strays land far below that.
            if (best < Math.max(10, pts.size() / 6)) break;

            double cx = (bestKey >> 32) * cell;
            double cz = (int) bestKey * cell;

            double minY = Double.MAX_VALUE;
            List<Seen> rest = new ArrayList<>();
            for (Seen sn : pts) {
                if (Math.abs(Math.hypot(sn.x() - cx, sn.z() - cz) - radius) <= 0.9) minY = Math.min(minY, sn.y());
                else rest.add(sn);
            }

            if (rest.size() == pts.size()) break;

            // Leftovers of an already-found ring (its points wobble a little off the exact radius) peak right next to it.
            boolean duplicate = false;
            for (double[] o : out) if (Math.hypot(o[0] - cx, o[1] - cz) < 2.0) { duplicate = true; break; }
            if (!duplicate) out.add(new double[]{cx, cz, minY});
            pts = rest;
        }

        return out;
    }

    private double groundBelow(double x, double y, double z) {
        return groundBelow(x, y, z, 16);
    }

    private double groundBelow(double x, double y, double z, int maxDrop) {
        if (mc.world == null) return y - 0.5;

        net.minecraft.util.math.BlockPos.Mutable pos = new net.minecraft.util.math.BlockPos.Mutable(Math.floor(x), Math.floor(y), Math.floor(z));
        for (int i = 0; i < maxDrop; i++) {
            if (!mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) return pos.getY() + 1;
            pos.move(0, -1, 0);
        }

        return y - 0.5;
    }

    // {rightMin, rightMax, upMin, upMax, forwardMin, forwardMax, brightness} - forward+ is the nose direction,
    // local units are fractions of the missile's overall length (~1.3 blocks at scale 1). Same general
    // shape as the first-person hologram (see Animations' Missile preset), rebuilt here in world space
    // since that one draws relative to the held item's view-model transform, not a world position.
    private static final double[][] MISSILE_PARTS = {
        {-0.12, 0.12, -0.12, 0.12, -0.20, 0.20, 1.0},   // body
        {-0.12, 0.12, -0.12, 0.12, -0.55, -0.20, 0.8},  // body, tail half
        {-0.12, 0.12, -0.12, 0.12, 0.20, 0.45, 0.9},    // body, toward the nose
        {-0.09, 0.09, -0.09, 0.09, 0.45, 0.60, 0.95},   // nose taper 1
        {-0.05, 0.05, -0.05, 0.05, 0.60, 0.72, 1.1},    // nose taper 2
        {-0.02, 0.02, -0.02, 0.02, 0.72, 0.80, 1.3},    // tip
        {0.12, 0.32, -0.02, 0.02, -0.45, -0.25, 0.85},  // fin +right
        {-0.32, -0.12, -0.02, 0.02, -0.45, -0.25, 0.85},// fin -right
        {-0.02, 0.02, 0.12, 0.32, -0.45, -0.25, 0.85},  // fin +up
        {-0.02, 0.02, -0.32, -0.12, -0.45, -0.25, 0.85},// fin -up
        {-0.08, 0.08, -0.08, 0.08, -0.62, -0.50, 1.4},  // exhaust glow
    };

    /**
     * A small holographic missile at (x,y,z), nose pointed along the horizontal direction (dirX,dirZ) -
     * both the bombs flying at you and the floor bombs are visibly missiles in the actual fight, so this
     * draws on them while they're still moving (see drawBombPaths/drawBombs).
     */
    private void drawMissileHologram(double x, double y, double z, double dirX, double dirZ, double scale, SettingColor color) {
        double len = Math.hypot(dirX, dirZ);
        double fx = len > 1e-4 ? dirX / len : 0, fz = len > 1e-4 ? dirZ / len : 1;
        double rx = fz, rz = -fx; // right = forward rotated -90 around Y

        long ms = System.currentTimeMillis();
        double flicker = 0.85 + 0.15 * Math.sin(ms * 0.03);

        for (int i = 0; i < MISSILE_PARTS.length; i++) {
            double[] part = MISSILE_PARTS[i];
            double shimmer = 0.85 + 0.15 * Math.sin(ms * 0.02 + i * 1.7);
            double bright = part[6] * shimmer;

            int r = (int) Math.min(255, color.r * bright), g = (int) Math.min(255, color.g * bright), b = (int) Math.min(255, color.b * bright);
            int a = (int) Math.max(0, Math.min(255, color.a * flicker * Math.min(1.2, 0.6 + bright * 0.4)));
            Color c = new Color(r, g, b, a);

            drawLocalBox(x, y, z, rx, rz, fx, fz, scale, part[0], part[1], part[2], part[3], part[4], part[5], c);
        }

        // Direction tracer: a bright line running from the tail through the nose and a bit further -
        // the box shape alone (nose cone + fins) reads ambiguously at a glance, this makes the actual
        // heading unmistakable.
        double tailF = -0.55 * scale, tipF = 1.05 * scale;
        double tx = x + fx * tailF, tz = z + fz * tailF;
        double hx = x + fx * tipF, hz = z + fz * tipF;
        Color tailColor = new Color(color.r, color.g, color.b, 0);
        Color headColor = new Color(255, 255, 255, Math.min(255, color.a + 60));
        cur.line(tx, y, tz, hx, y, hz, tailColor, headColor);
    }

    /** One box of drawMissileHologram, in the missile's own local axes (right, up=world Y, forward), then placed in the world. */
    private void drawLocalBox(double cx, double cy, double cz, double rx, double rz, double fx, double fz, double scale,
                               double rMin, double rMax, double uMin, double uMax, double fMin, double fMax, Color color) {
        double[][] corners = new double[8][3];
        int idx = 0;
        for (double r : new double[]{rMin, rMax}) {
            for (double u : new double[]{uMin, uMax}) {
                for (double f : new double[]{fMin, fMax}) {
                    corners[idx][0] = cx + (rx * r + fx * f) * scale;
                    corners[idx][1] = cy + u * scale;
                    corners[idx][2] = cz + (rz * r + fz * f) * scale;
                    idx++;
                }
            }
        }

        // Corner index bits: r(1) u(2) f(4). Each face fixes one axis and winds the other two consistently.
        quadFace(corners, 0, 1, 3, 2, color); // r = rMin
        quadFace(corners, 4, 6, 7, 5, color); // r = rMax
        quadFace(corners, 0, 4, 5, 1, color); // u = uMin
        quadFace(corners, 2, 3, 7, 6, color); // u = uMax
        quadFace(corners, 0, 2, 6, 4, color); // f = fMin
        quadFace(corners, 1, 5, 7, 3, color); // f = fMax
    }

    private void quadFace(double[][] c, int a, int b, int cIdx, int d, Color color) {
        cur.quad(c[a][0], c[a][1], c[a][2], c[b][0], c[b][1], c[b][2], c[cIdx][0], c[cIdx][1], c[cIdx][2], c[d][0], c[d][1], c[d][2], color);
    }

    private void drawBombPaths() {
        List<BombPath> paths = new ArrayList<>();
        synchronized (bombPaths) {
            for (BombPath path : bombPaths) {
                BombPath copy = new BombPath(path.wool);
                copy.pts.addAll(path.pts);
                paths.add(copy);
            }
        }

        double h = bombPathHeight.get();
        for (BombPath path : paths) {
            SettingColor base = path.wool == Blocks.CYAN_WOOL ? bombCyan.get() : path.wool == Blocks.RED_WOOL ? bombRed.get() : bombYellow.get();
            Color top = new Color(base.r, base.g, base.b, 0);

            for (int i = 1; i < path.pts.size(); i++) {
                double[] a = path.pts.get(i - 1), b = path.pts.get(i);
                double y0 = Math.min(a[1], b[1]) - 0.2;
                cur.quad(a[0], y0, a[2], a[0], y0 + h, a[2], b[0], y0 + h, b[2], b[0], y0, b[2], top, top, base, base);
                cur.line(a[0], y0, a[2], b[0], y0, b[2], base);
            }

            // The head of the trail is where the actual bomb is right now - draw the missile there,
            // pointed the way it's been travelling.
            if (floorMissileEnabled.get() && path.pts.size() >= 2) {
                double[] a = path.pts.get(path.pts.size() - 2), b = path.pts.get(path.pts.size() - 1);
                drawMissileHologram(b[0], b[1] + 0.15, b[2], b[0] - a[0], b[2] - a[2], floorMissileScale.get(), floorMissileColor.get());
            }
        }
    }

    private void drawBombs() {
        List<Bomb> snapshot;
        synchronized (bombs) { snapshot = new ArrayList<>(bombs); }

        List<Seen> inner;
        synchronized (bombInnerSeen) { inner = new ArrayList<>(bombInnerSeen); }

        double outerR = bombRadius.get();
        boolean blink = (System.currentTimeMillis() / 150) % 2 == 0;

        // Real blasts: fit the count-1 ring particles per color and draw them as they are; the flying-bomb
        // marker of the same color near it is done, so drop it.
        for (net.minecraft.block.Block wool : new net.minecraft.block.Block[]{Blocks.CYAN_WOOL, Blocks.RED_WOOL, Blocks.YELLOW_WOOL}) {
            List<Seen> pts = new ArrayList<>();
            synchronized (bombRingSeen) { for (BombRingPoint r : bombRingSeen) if (r.wool() == wool) pts.add(r.p()); }

            SettingColor rc = wool == Blocks.CYAN_WOOL ? bombCyan.get() : wool == Blocks.RED_WOOL ? bombRed.get() : bombYellow.get();

            for (double[] ring : findRings(pts, outerR, 300)) {
                drawWall(ring[0], ring[1], outerR, ring[2] - 0.2, bombHeight.get(), blink ? new Color(255, 255, 255, rc.a) : rc, new Color(rc.r, rc.g, rc.b, 0));

                snapshot.removeIf(b -> b.wool == wool && Math.hypot(b.x - ring[0], b.z - ring[1]) <= 3.0);
                synchronized (bombs) { bombs.removeIf(b -> b.wool == wool && Math.hypot(b.x - ring[0], b.z - ring[1]) <= 3.0); }
            }
        }

        for (Bomb b : snapshot) {
            SettingColor base = b.wool == Blocks.CYAN_WOOL ? bombCyan.get() : b.wool == Blocks.RED_WOOL ? bombRed.get() : bombYellow.get();
            Color top = new Color(base.r, base.g, base.b, 0);
            double y0 = groundBelow(b.x, b.y, b.z);

            // Inner ring: mean horizontal distance of the freshest inner-particle burst around this bomb.
            long newest = 0;
            for (Seen sn : inner) if (Math.hypot(sn.x() - b.x, sn.z() - b.z) <= outerR * 1.6) newest = Math.max(newest, sn.time());

            double sum = 0;
            int count = 0;
            for (Seen sn : inner) {
                double d = Math.hypot(sn.x() - b.x, sn.z() - b.z);
                if (d <= outerR * 1.6 && sn.time() >= newest - 250) { sum += d; count++; }
            }

            double innerR = count >= 3 ? sum / count : 0;
            boolean warning = innerR >= outerR * bombExplodeAt.get();

            Color outerColor = warning && blink ? new Color(255, 255, 255, base.a) : base;
            if (!ringOnlyAfterImpact.get()) drawWall(b.x, b.z, outerR, y0, bombHeight.get(), outerColor, top);

            // Still flying (hasn't landed/exploded yet) - homing on a player, so point it that way.
            // b.y is the bomb's own tracked altitude (running mean of its real particle samples) -
            // using y0 (ground level below it) here instead used to pin the missile to the floor for
            // its whole flight regardless of how high it actually was.
            if (homingMissileEnabled.get() && mc.player != null) {
                drawMissileHologram(b.x, b.y + 0.3, b.z, mc.player.getX() - b.x, mc.player.getZ() - b.z, homingMissileScale.get(), homingMissileColor.get());
            }

            if (innerR > 0.3) {
                SettingColor ic = bombInnerColor.get();
                drawWall(b.x, b.z, Math.min(innerR, outerR), y0, bombHeight.get(), ic, new Color(ic.r, ic.g, ic.b, 0));
            }

            if (bombPillarHeight.get() > 0) drawPillar(b.x, y0, b.z, bombPillarHeight.get(), base, top);
        }
    }
}
