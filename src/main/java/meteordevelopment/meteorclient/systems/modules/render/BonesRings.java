/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.CheeseControl;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin-only (unlocked with .Admin, PIN-protected). Constant rings around Captain Bones (found
 * by name) showing how far away he gets angry, plus a second ring for his cyclone attack's reach, drawn as a wall that fades from a solid base up into
 * the air. Only shown while someone is actually in the fight by default, and its height can be
 * locked to where Bones first spawned in so it doesn't bob with him.
 */
public class BonesRings extends Module {
    // One top-level "Bones" section holding all of this boss's own sub-sections nested inside it,
    // instead of each sitting as its own top-level section in the Visualizer's settings screen.
    private final SettingGroup sgBonesParent = settings.createGroup("Bones");

    private final SettingGroup sgBones = settings.createSubGroup("Aggro Ring", sgBonesParent);
    private final SettingGroup sgAppearance = settings.createSubGroup("Ring Appearance", sgBonesParent);
    private final SettingGroup sgCyclone = settings.createSubGroup("Cyclone Ring", sgBonesParent);
    private final SettingGroup sgTeleport = settings.createSubGroup("Teleport Ring", sgBonesParent);
    private final SettingGroup sgPirate = settings.createSubGroup("Suicide Pirates", sgBonesParent);
    private final SettingGroup sgTpPath = settings.createSubGroup("Teleport Path", sgBonesParent);

    private final Setting<Boolean> tpPath = sgTpPath.add(new BoolSetting.Builder()
        .name("teleport-path")
        .description("When Bones teleports (he draws a 36-particle ring whose diameter runs from where he was to where he lands), marks both ends and the line between them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> tpParticle = sgTpPath.add(new StringSetting.Builder()
        .name("teleport-particle")
        .description("The particle the teleport ring is made of (registry id). The vacuum ability uses the same particle, in bursts of ~360 - those are ignored by the burst size limits below.")
        .defaultValue("minecraft:angry_villager")
        .visible(tpPath::get)
        .build()
    );

    private final Setting<Integer> tpMinBurst = sgTpPath.add(new IntSetting.Builder()
        .name("min-burst-size")
        .description("A teleport ring is at least this many particles (recorded: 36, sometimes 31).")
        .defaultValue(28)
        .range(6, 200)
        .sliderRange(10, 60)
        .visible(tpPath::get)
        .build()
    );

    private final Setting<Integer> tpMaxBurst = sgTpPath.add(new IntSetting.Builder()
        .name("max-burst-size")
        .description("...and at most this many. Keep it well under the vacuum's ~360 and above the 41-particle bursts.")
        .defaultValue(38)
        .range(6, 400)
        .sliderRange(10, 100)
        .visible(tpPath::get)
        .build()
    );

    private final Setting<Integer> tpLifetime = sgTpPath.add(new IntSetting.Builder()
        .name("path-lifetime")
        .description("How long, in milliseconds, the markers stay after a teleport.")
        .defaultValue(2500)
        .range(300, 15000)
        .sliderRange(500, 6000)
        .visible(tpPath::get)
        .build()
    );

    private final Setting<Double> tpMarkerRadius = sgTpPath.add(new DoubleSetting.Builder()
        .name("marker-radius")
        .description("Radius of the rings marking the start and end points.")
        .defaultValue(1.5)
        .min(0.3)
        .sliderRange(0.5, 6)
        .visible(tpPath::get)
        .build()
    );

    private final Setting<SettingColor> tpFromColor = sgTpPath.add(new ColorSetting.Builder()
        .name("from-color")
        .description("Where he teleported from.")
        .defaultValue(new SettingColor(255, 140, 40, 200))
        .visible(tpPath::get)
        .build()
    );

    private final Setting<SettingColor> tpToColor = sgTpPath.add(new ColorSetting.Builder()
        .name("to-color")
        .description("Where he teleported to.")
        .defaultValue(new SettingColor(60, 255, 120, 200))
        .visible(tpPath::get)
        .build()
    );

    private static final class TpPoint {
        final double x, z;
        final long time;

        TpPoint(double x, double z, long time) { this.x = x; this.z = z; this.time = time; }
    }

    private final List<TpPoint> tpSeen = new ArrayList<>();
    private final List<double[]> bossHistory = new ArrayList<>(); // {timeMs, x, z}
    private long tpHandledAt;
    private double[] tpMark; // {fromX, fromZ, toX, toZ, y, timeMs}


    private final Setting<Boolean> bonesRing = sgBones.add(new BoolSetting.Builder()
        .name("bones-ring")
        .description("Mid ring: stay OUTSIDE it or Bones aggros on you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> bossName = sgBones.add(new StringSetting.Builder()
        .name("boss-name")
        .description("Text (case-insensitive) the boss's name must contain to be recognised as Bones.")
        .defaultValue("Bones")
        .build()
    );

    private final Setting<Double> aggroRadius = sgBones.add(new DoubleSetting.Builder()
        .name("aggro-radius")
        .description("The distance, in blocks, at which Bones gets angry - set this to the real value; the default is a placeholder guess (adjust it).")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<Boolean> onlyInFight = sgBones.add(new BoolSetting.Builder()
        .name("only-in-fight")
        .description("Only draw the ring while at least one player (you included) is within Fight Range of Bones, i.e. someone is actually in the fight.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> fightRange = sgBones.add(new DoubleSetting.Builder()
        .name("fight-range")
        .description("How close a player has to be to Bones to count as being in the fight.")
        .defaultValue(20)
        .min(1)
        .sliderRange(5, 100)
        .visible(onlyInFight::get)
        .build()
    );

    private final Setting<Boolean> lockHeight = sgBones.add(new BoolSetting.Builder()
        .name("lock-to-spawn-height")
        .description("Keeps the ring at the height Bones first appeared at instead of following his Y as he jumps or moves up and down.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> yOffset = sgBones.add(new DoubleSetting.Builder()
        .name("y-offset")
        .description("Shifts the ring up/down from where it would otherwise sit (Bones's feet, or the locked spawn height).")
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
        .defaultValue(new SettingColor(255, 40, 40, 170))
        .build()
    );

    private final Setting<SettingColor> topColor = sgAppearance.add(new ColorSetting.Builder()
        .name("top-color")
        .description("Color the ring fades to at the top - use a low alpha to fade out.")
        .defaultValue(new SettingColor(255, 190, 40, 0))
        .build()
    );

    private final Setting<Boolean> baseLine = sgAppearance.add(new BoolSetting.Builder()
        .name("base-line")
        .description("Draws a solid line along the bottom edge of the ring.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cycloneRing = sgCyclone.add(new BoolSetting.Builder()
        .name("cyclone-ring")
        .description("Inner ring: marks how far his cyclone attack reaches. Uses the same boss detection, fight check and locked height as the aggro ring.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> cycloneRadius = sgCyclone.add(new DoubleSetting.Builder()
        .name("cyclone-radius")
        .description("How far, in blocks, Bones's cyclone attack reaches - set this to the real value; the default is a placeholder guess (adjust it).")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 40)
        .visible(cycloneRing::get)
        .build()
    );

    private final Setting<Double> cycloneHeight = sgCyclone.add(new DoubleSetting.Builder()
        .name("cyclone-height")
        .description("How far up into the air the cyclone ring's wall reaches.")
        .defaultValue(3)
        .min(0.5)
        .sliderRange(1, 20)
        .visible(cycloneRing::get)
        .build()
    );

    private final Setting<SettingColor> cycloneBaseColor = sgCyclone.add(new ColorSetting.Builder()
        .name("cyclone-base-color")
        .description("Color of the cyclone ring at ground level.")
        .defaultValue(new SettingColor(60, 170, 255, 170))
        .visible(cycloneRing::get)
        .build()
    );

    private final Setting<SettingColor> cycloneTopColor = sgCyclone.add(new ColorSetting.Builder()
        .name("cyclone-top-color")
        .description("Color the cyclone ring fades to at the top - use a low alpha to fade out.")
        .defaultValue(new SettingColor(190, 120, 255, 0))
        .visible(cycloneRing::get)
        .build()
    );

    private final Setting<Boolean> teleportRing = sgTeleport.add(new BoolSetting.Builder()
        .name("teleport-ring")
        .description("Outer ring: stay INSIDE it or Bones teleports you. Uses the same boss detection, fight check and locked height as the other rings.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> teleportLabel = sgTeleport.add(new StringSetting.Builder()
        .name("teleport-label")
        .description("A note to yourself about this ring - not drawn anywhere.")
        .defaultValue("Stay inside or get teleported")
        .visible(teleportRing::get)
        .build()
    );

    private final Setting<Double> teleportRadius = sgTeleport.add(new DoubleSetting.Builder()
        .name("teleport-radius")
        .description("How far from Bones, in blocks, you can get before he teleports you - set this to the real value; the default is a placeholder guess (adjust it). Should be larger than the aggro radius.")
        .defaultValue(12)
        .min(1)
        .sliderRange(1, 60)
        .visible(teleportRing::get)
        .build()
    );

    private final Setting<Double> teleportHeight = sgTeleport.add(new DoubleSetting.Builder()
        .name("teleport-height")
        .description("How far up into the air the teleport ring's wall reaches.")
        .defaultValue(3)
        .min(0.5)
        .sliderRange(1, 20)
        .visible(teleportRing::get)
        .build()
    );

    private final Setting<SettingColor> teleportBaseColor = sgTeleport.add(new ColorSetting.Builder()
        .name("teleport-base-color")
        .description("Color of the teleport ring at ground level.")
        .defaultValue(new SettingColor(80, 255, 120, 170))
        .visible(teleportRing::get)
        .build()
    );

    private final Setting<SettingColor> teleportTopColor = sgTeleport.add(new ColorSetting.Builder()
        .name("teleport-top-color")
        .description("Color the teleport ring fades to at the top - use a low alpha to fade out.")
        .defaultValue(new SettingColor(255, 255, 120, 0))
        .visible(teleportRing::get)
        .build()
    );

    // Suicide Pirates

    public enum PirateColorChange {
        Off,
        WhenInside,
        ByDistance
    }

    public enum PirateFlicker {
        Off,
        Always,
        WhenInside
    }

    private final Setting<Boolean> pirateRing = sgPirate.add(new BoolSetting.Builder()
        .name("suicide-pirate-ring")
        .description("A ring around each Suicide Pirate showing its explosion radius.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> pirateName = sgPirate.add(new StringSetting.Builder()
        .name("pirate-name")
        .description("Text (case-insensitive) an entity's name must contain to count as a Suicide Pirate.")
        .defaultValue("Suicide Pirate")
        .visible(pirateRing::get)
        .build()
    );

    private final Setting<Double> pirateRadius = sgPirate.add(new DoubleSetting.Builder()
        .name("pirate-radius")
        .description("The explosion radius, in blocks - set this to the real value; the default is a placeholder guess (adjust it).")
        .defaultValue(4)
        .min(0.5)
        .sliderRange(1, 20)
        .visible(pirateRing::get)
        .build()
    );

    private final Setting<Double> pirateRange = sgPirate.add(new DoubleSetting.Builder()
        .name("pirate-range")
        .description("Only pirates within this many blocks of you get a ring.")
        .defaultValue(40)
        .min(5)
        .sliderRange(10, 100)
        .visible(pirateRing::get)
        .build()
    );

    private final Setting<Double> pirateHeight = sgPirate.add(new DoubleSetting.Builder()
        .name("pirate-height")
        .description("How far up into the air the ring's wall reaches.")
        .defaultValue(2)
        .min(0.5)
        .sliderRange(0.5, 10)
        .visible(pirateRing::get)
        .build()
    );

    private final Setting<SettingColor> pirateBaseColor = sgPirate.add(new ColorSetting.Builder()
        .name("pirate-base-color")
        .description("Ring color at ground level.")
        .defaultValue(new SettingColor(255, 170, 0, 150))
        .visible(pirateRing::get)
        .build()
    );

    private final Setting<SettingColor> pirateTopColor = sgPirate.add(new ColorSetting.Builder()
        .name("pirate-top-color")
        .description("Color the ring fades to at the top.")
        .defaultValue(new SettingColor(255, 230, 90, 0))
        .visible(pirateRing::get)
        .build()
    );

    private final Setting<PirateColorChange> pirateColorChange = sgPirate.add(new EnumSetting.Builder<PirateColorChange>()
        .name("pirate-color-change")
        .description("Off: the ring stays one color. When Inside: it turns the danger color while you're inside the radius. By Distance: it blends from the normal color (at the edge) to the danger color (right on top of it).")
        .defaultValue(PirateColorChange.Off)
        .visible(pirateRing::get)
        .build()
    );

    private final Setting<SettingColor> pirateDangerColor = sgPirate.add(new ColorSetting.Builder()
        .name("pirate-danger-color")
        .description("The color the ring turns for the color change above.")
        .defaultValue(new SettingColor(255, 30, 30, 200))
        .visible(() -> pirateRing.get() && pirateColorChange.get() != PirateColorChange.Off)
        .build()
    );

    private final Setting<PirateFlicker> pirateFlicker = sgPirate.add(new EnumSetting.Builder<PirateFlicker>()
        .name("pirate-flicker")
        .description("Makes the ring blink: Always, or only while you're inside its radius.")
        .defaultValue(PirateFlicker.Off)
        .visible(pirateRing::get)
        .build()
    );

    private final Setting<Double> pirateFlickerSpeed = sgPirate.add(new DoubleSetting.Builder()
        .name("pirate-flicker-speed")
        .description("Blinks per second.")
        .defaultValue(4)
        .min(0.5)
        .sliderRange(1, 15)
        .visible(() -> pirateRing.get() && pirateFlicker.get() != PirateFlicker.Off)
        .build()
    );

    // Own renderer (not the shared event one) so depth testing can be on for just this module -
    // the shared meshes draw with depth test off, i.e. on top of everything including walls.
    private final Renderer3D ring = new Renderer3D();

    private int trackedBossId = -1;
    private double lockedY;

    public BonesRings() {
        super(Categories.Bosses, "bones-rings", "Constant rings around Bones: aggro (stay out), cyclone reach, and teleport (stay in).");
        adminOnly = true; // component of Visualizer - never registered as its own module

        ring.lines.depthTest = true;
        ring.triangles.depthTest = true;
    }

    private boolean locked() {
        return !Modules.get().isCheeseUnlocked() || !CheeseControl.sectionBonesRings();
    }

    // Was: announced status changes (boss found/lost, in-fight/not) in chat for debugging why the
    // rings weren't showing - left as a no-op call site rather than ripping out every diag(...)
    // call, in case that debugging is needed again later.
    private String diagState = "";

    private void diag(String state, String message) {
        diagState = state;
    }

    @Override
    public boolean isActive() {
        Visualizer owner = Modules.get() != null ? Modules.get().get(Visualizer.class) : null;
        return owner != null && owner.isActive();
    }

    @Override
    public void onActivate() {
        diagState = "";
    }

    @Override
    public void onDeactivate() {
        trackedBossId = -1;
    }

    private boolean matchesBoss(Entity entity, String needle) {
        if (entity == mc.player || entity instanceof PlayerEntity || entity.isRemoved()) return false;
        if (!entity.hasCustomName() && !(entity instanceof LivingEntity)) return false;

        return entity.getName().getString().toLowerCase().contains(needle);
    }

    /**
     * Sticky: once a boss is picked it's kept for as long as it's alive and still matches, instead
     * of re-picking "the nearest match" every frame - with two matches (the mob and its floating
     * name-tag armor stand, say) that flipped between them as you moved, and the ring jumped
     * between their two positions.
     */
    private Entity findBoss() {
        String needle = bossName.get().trim().toLowerCase();
        if (needle.isEmpty()) return null;

        if (trackedBossId != -1) {
            Entity tracked = mc.world.getEntityById(trackedBossId);
            // A tracked name-tag armor stand is only a placeholder - re-search so the real mob
            // takes over as soon as it exists, then stick to that.
            if (tracked != null && !(tracked instanceof ArmorStandEntity) && matchesBoss(tracked, needle)) return tracked;
        }

        Entity best = null;
        boolean bestIsStand = true;
        double bestDist = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!matchesBoss(entity, needle)) continue;

            boolean stand = entity instanceof ArmorStandEntity;
            double dist = entity.squaredDistanceTo(mc.player);

            // A real mob wins over a floating name-tag armor stand; otherwise nearest.
            if (best == null || (bestIsStand && !stand) || (stand == bestIsStand && dist < bestDist)) {
                best = entity;
                bestIsStand = stand;
                bestDist = dist;
            }
        }

        return best;
    }

    /** Horizontal distance from you to Bones, or null if the module is off / he isn't around / nobody is in the fight (per Only In Fight). For HUD elements. */
    public Double getBossDistance() {
        if (!isActive() || locked() || mc.world == null || mc.player == null) return null;

        Entity boss = findBoss();
        if (boss == null) return null;
        if (onlyInFight.get() && !playerInFight(boss)) return null;

        return Math.hypot(mc.player.getX() - boss.getX(), mc.player.getZ() - boss.getZ());
    }

    public double getTeleportRadius() {
        return teleportRadius.get();
    }

    private boolean playerInFight(Entity boss) {
        double range = fightRange.get();
        double rangeSq = range * range;

        if (mc.player.squaredDistanceTo(boss) <= rangeSq) return true;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player && player.squaredDistanceTo(boss) <= rangeSq) return true;
        }

        return false;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ParticleS2CPacket packet) || locked() || !tpPath.get() || !CheeseControl.teleportPath()) return;

        Identifier id = Registries.PARTICLE_TYPE.getId(packet.getParameters().getType());
        if (id == null || !id.toString().equals(tpParticle.get().trim())) return;

        synchronized (tpSeen) {
            tpSeen.add(new TpPoint(packet.getX(), packet.getZ(), System.currentTimeMillis()));
            if (tpSeen.size() > 800) tpSeen.subList(0, tpSeen.size() - 800).clear();
        }
    }

    /** Looks for a fresh teleport ring in the seen particles and, if there is one, works out both ends. Needs the tracked boss. */
    private void updateTeleport(Entity boss, long now) {
        // Boss position history (~1.5s) - "from" is wherever he stood just before the ring showed up.
        bossHistory.add(new double[]{now, boss.getX(), boss.getZ()});
        bossHistory.removeIf(h -> h[0] < now - 1500);

        List<TpPoint> burst = new ArrayList<>();
        synchronized (tpSeen) {
            tpSeen.removeIf(t -> t.time < now - 1500);

            long newest = 0;
            for (TpPoint t : tpSeen) newest = Math.max(newest, t.time);
            if (newest == 0 || newest <= tpHandledAt || now - newest > 400) return;

            for (TpPoint t : tpSeen) if (t.time >= newest - 120) burst.add(t);
        }

        int n = burst.size();
        if (n < tpMinBurst.get() || n > tpMaxBurst.get()) return;

        // Circle fit (Kasa) in the horizontal plane.
        double mx = 0, mz = 0;
        for (TpPoint t : burst) { mx += t.x; mz += t.z; }
        mx /= n; mz /= n;

        double suu = 0, suv = 0, svv = 0, suuu = 0, svvv = 0, suvv = 0, svuu = 0;
        for (TpPoint t : burst) {
            double u = t.x - mx, v = t.z - mz;
            suu += u * u; suv += u * v; svv += v * v;
            suuu += u * u * u; svvv += v * v * v; suvv += u * v * v; svuu += v * u * u;
        }

        double det = suu * svv - suv * suv;
        if (Math.abs(det) < 1e-6) return;

        double uc = ((suuu + suvv) * svv - (svvv + svuu) * suv) / (2 * det);
        double vc = ((svvv + svuu) * suu - (suuu + suvv) * suv) / (2 * det);
        double cx = mx + uc, cz = mz + vc;
        double r = Math.sqrt(uc * uc + vc * vc + (suu + svv) / n);

        double res = 0;
        for (TpPoint t : burst) { double d = Math.hypot(t.x - cx, t.z - cz) - r; res += d * d; }
        if (r < 0.8 || Math.sqrt(res / n) > Math.max(0.8, r * 0.15)) return;

        tpHandledAt = burst.get(0).time;

        // Both ends: the circle's diameter is the jump, so the two ends are opposite each other through the center.
        // Bones is at one of them (or about to be) - the one where he stood just before is "from".
        double[] before = bossHistory.get(0);
        for (double[] h : bossHistory) if (h[0] <= burst.get(0).time - 150) before = h; else break;

        double bx = boss.getX(), bz = boss.getZ();
        // Use where he is now to pick the ring end nearest him, the other end is derived from the center.
        double nowEndX = bx, nowEndZ = bz;
        double otherX = 2 * cx - nowEndX, otherZ = 2 * cz - nowEndZ;

        double fromX, fromZ, toX, toZ;
        // Whichever of (boss now, mirrored point) is closer to where he stood before the ring appeared is the start.
        if (Math.hypot(nowEndX - before[1], nowEndZ - before[2]) <= Math.hypot(otherX - before[1], otherZ - before[2])) {
            fromX = nowEndX; fromZ = nowEndZ; toX = otherX; toZ = otherZ;
        } else {
            fromX = otherX; fromZ = otherZ; toX = nowEndX; toZ = nowEndZ;
        }

        tpMark = new double[]{fromX, fromZ, toX, toZ, lockHeight.get() ? lockedY : boss.getY(), now};
    }

    private void drawTeleportPath(long now) {
        double[] m = tpMark;
        if (m == null) return;
        if (now - (long) m[5] > tpLifetime.get()) { tpMark = null; return; }

        double y = m[4] + yOffset.get();
        double r = tpMarkerRadius.get();
        SettingColor from = tpFromColor.get(), to = tpToColor.get();

        drawRing(new Vec3d(m[0], y, m[1]), y, r, 1.2, from, new Color(from.r, from.g, from.b, 0));
        drawRing(new Vec3d(m[2], y, m[3]), y, r, 1.2, to, new Color(to.r, to.g, to.b, 0));

        // Line from start to end at the floor, with a stripe fading from the "from" color to the "to" color.
        int steps = 24;
        for (int i = 0; i < steps; i++) {
            double t0 = (double) i / steps, t1 = (double) (i + 1) / steps;
            Color c = new Color(
                (int) (from.r + (to.r - from.r) * t0), (int) (from.g + (to.g - from.g) * t0),
                (int) (from.b + (to.b - from.b) * t0), (int) (from.a + (to.a - from.a) * t0));
            ring.line(m[0] + (m[2] - m[0]) * t0, y + 0.05, m[1] + (m[3] - m[1]) * t0, m[0] + (m[2] - m[0]) * t1, y + 0.05, m[1] + (m[3] - m[1]) * t1, c);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        boolean aggro = bonesRing.get() && CheeseControl.aggroRing(), cyclone = cycloneRing.get() && CheeseControl.cycloneRing(), teleport = teleportRing.get() && CheeseControl.teleportRing();
        if (locked() || (!aggro && !cyclone && !teleport) || mc.world == null || mc.player == null) return;

        Entity boss = findBoss();
        if (boss == null) {
            trackedBossId = -1;
            diag("no-boss", "No entity named \"" + bossName.get() + "\" nearby (Boss Name setting) - waiting.");
            return;
        }

        if (boss.getId() != trackedBossId) {
            trackedBossId = boss.getId();
            lockedY = boss.getY();
        }

        if (onlyInFight.get() && !playerInFight(boss)) {
            diag("not-in-fight", "Found " + boss.getName().getString() + ", but nobody is within Fight Range (" + fightRange.get() + ") of it - turn Only In Fight off or raise the range.");
            return;
        }

        diag("drawing", "Found " + boss.getName().getString() + " - drawing (aggro " + aggro + ", cyclone " + cyclone + ", teleport " + teleport + ").");

        long nowMs = System.currentTimeMillis();
        boolean tpOn = tpPath.get() && CheeseControl.teleportPath();
        if (tpOn) updateTeleport(boss, nowMs);

        Vec3d pos = boss.getLerpedPos(event.tickDelta);
        double y0 = (lockHeight.get() ? lockedY : pos.y) + yOffset.get();

        ring.begin();

        if (aggro) drawRing(pos, y0, aggroRadius.get(), ringHeight.get(), baseColor.get(), topColor.get());
        if (cyclone) drawRing(pos, y0, cycloneRadius.get(), cycloneHeight.get(), cycloneBaseColor.get(), cycloneTopColor.get());

        if (tpOn) drawTeleportPath(nowMs);

        if (teleport) drawRing(pos, y0, teleportRadius.get(), teleportHeight.get(), teleportBaseColor.get(), teleportTopColor.get());

        ring.end();
        renderRing(event);
    }

    /** Draws this component's rings, hiding or not hiding what's behind them per the Visualizer's See Through settings. */
    private void renderRing(Render3DEvent event) {
        Visualizer owner = Modules.get().get(Visualizer.class);
        boolean blocks = owner == null || owner.ringBlocks("bones-rings");

        if (!blocks) com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        ring.render(event.matrices);
        if (!blocks) com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
    }

    @EventHandler
    private void onRenderPirates(Render3DEvent event) {
        if (locked() || !pirateRing.get() || !CheeseControl.pirateRings() || mc.world == null || mc.player == null) return;

        String needle = pirateName.get().trim().toLowerCase();
        if (needle.isEmpty()) return;

        double range = pirateRange.get();
        double rangeSq = range * range;
        double r = pirateRadius.get();

        ring.begin();
        boolean any = false;
        int drawn = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (drawn >= 60) break;
            if (entity == mc.player || entity instanceof PlayerEntity || entity.isRemoved()) continue;
            if (entity.squaredDistanceTo(mc.player) > rangeSq) continue;
            if (!entity.getName().getString().toLowerCase().contains(needle)) continue;

            Vec3d pos = entity.getLerpedPos(event.tickDelta);
            double dist = Math.hypot(mc.player.getX() - pos.x, mc.player.getZ() - pos.z);
            boolean inside = dist <= r;

            SettingColor base = pirateBaseColor.get();
            SettingColor danger = pirateDangerColor.get();
            Color bottom = base;

            switch (pirateColorChange.get()) {
                case WhenInside -> { if (inside) bottom = danger; }
                case ByDistance -> {
                    // 0 at the ring's edge (or beyond), 1 when standing on top of the pirate.
                    float t = (float) Math.max(0, Math.min(1, 1 - dist / r));
                    bottom = new Color(
                        (int) (base.r + (danger.r - base.r) * t),
                        (int) (base.g + (danger.g - base.g) * t),
                        (int) (base.b + (danger.b - base.b) * t),
                        (int) (base.a + (danger.a - base.a) * t)
                    );
                }
                default -> {}
            }

            PirateFlicker flicker = pirateFlicker.get();
            if (flicker == PirateFlicker.Always || (flicker == PirateFlicker.WhenInside && inside)) {
                long period = (long) (1000.0 / pirateFlickerSpeed.get());
                if ((System.currentTimeMillis() / Math.max(1, period / 2)) % 2 == 0) continue;
            }

            SettingColor topSetting = pirateTopColor.get();
            Color top = new Color(topSetting.r, topSetting.g, topSetting.b, topSetting.a);

            drawRing(pos, pos.y, r, pirateHeight.get(), bottom, top);
            any = true;
            drawn++;
        }

        ring.end();
        if (any) renderRing(event);
    }

    private void drawRing(Vec3d center, double y0, double r, double height, Color bottom, Color top) {
        double y1 = y0 + height;
        int n = segments.get();

        double px = center.x + r, pz = center.z;
        for (int i = 1; i <= n; i++) {
            double angle = i * 2 * Math.PI / n;
            double cx = center.x + Math.cos(angle) * r;
            double cz = center.z + Math.sin(angle) * r;

            // Vertex order is (bottom-left, top-left, top-right, bottom-right); the color params are
            // (topLeft, topRight, bottomRight, bottomLeft) - so bottom edge gets the base color and
            // the top edge the top color, a clean vertical gradient.
            ring.quad(
                px, y0, pz,
                px, y1, pz,
                cx, y1, cz,
                cx, y0, cz,
                top, top, bottom, bottom
            );

            if (baseLine.get()) ring.line(px, y0, pz, cx, y0, cz, bottom);

            px = cx;
            pz = cz;
        }
    }
}
