/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.misc.DebugLog;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recolors particles client-side - originally for censoring the redstone-colored "blood" particle
 * (ParticleTypes.DAMAGE_INDICATOR) that spawns when a mob or player is hit, generalized to work on
 * any particle type. Hit Detection's "Hit Particles" list is the one selector for which particle
 * type(s) it's ever allowed to touch, in every mode - Mode only adds an extra trigger condition on
 * top of that (On Hit: none, Near/Hurt Entities: also requires proximity to a living/recently-hurt
 * entity). Add your server's own AOE/ability particle to that list if it isn't vanilla's damage
 * indicator - for catching an unknown particle type near a hit regardless of type, see Radius Clear.
 *
 * Split into two independent sections: Hit Detection (an auto-detection heuristic) and General
 * Particles (an explicit per-type list, always active independently of Hit Detection). Both sections
 * use the same list shape - each listed particle type has its own color and blend mode, so different
 * types can be recolored differently at once. A type can be covered by both at once - if it's also
 * listed in General Particles, that entry wins over whatever Hit Detection's own list would have
 * picked for it.
 */
public class ParticleColor extends Module {
    public enum HitMode {
        OnHit,
        NearEntities,
        HurtEntities
    }

    public enum RadiusClearTrigger {
        NearEntities,
        HurtEntities,
        NearEntityTypes
    }

    public enum RadiusClearScope {
        All,
        Listed
    }

    private final SettingGroup sgHitDetection = settings.createGroup("Hit Detection");
    private final SettingGroup sgBlending = settings.createGroup("Blending");
    private final SettingGroup sgGeneralParticles = settings.createGroup("General Particles");
    private final SettingGroup sgRadiusClear = settings.createGroup("Radius Clear");
    private final SettingGroup sgPermClear = settings.createGroup("Perm Clear");

    // Blending

    private final Setting<Integer> overallAlpha = sgBlending.add(new IntSetting.Builder()
        .name("overall-alpha")
        .description("Extra transparency applied to every recolored particle, separate from the tint color's own alpha. That alpha controls how strongly the tint blends into the particle's original color (0 = untouched original color, 255 = fully replaced) - this one controls how see-through the end result is.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    private final Setting<Integer> contrast = sgBlending.add(new IntSetting.Builder()
        .name("contrast")
        .description("Pushes every recolored particle's final color away from (above 100%) or toward (below 100%) mid-gray before it's applied. Colorize mode can only ever land on the picked color's sprite-corrected AVERAGE - individual pixels still carry some of the original texture's own shading/hue, since that mode keeps the real texture instead of discarding it. Raising Contrast exaggerates the tint relative to that leftover shading, which is the closest this can get to full-strength color without discarding the texture outright (Override mode already does that, at the cost of losing the texture's real detail).")
        .defaultValue(100)
        .range(0, 300)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Boolean> overrideLighting = sgBlending.add(new BoolSetting.Builder()
        .name("override-lighting")
        .description("Forces every recolored particle to render at a fixed light level instead of sampling the real light at its position - so it doesn't look dim/tinted dark in a cave or at night.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> lightLevel = sgBlending.add(new IntSetting.Builder()
        .name("light-level")
        .description("Light level (0-15, both sky and block light) used when Override Lighting is on. 15 = full bright.")
        .defaultValue(15)
        .range(0, 15)
        .sliderRange(0, 15)
        .visible(overrideLighting::get)
        .build()
    );

    // Hit Detection

    private final Setting<Boolean> hitDetectionEnabled = sgHitDetection.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Recolors/hides particles matching the mode below.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> removeBlood = sgHitDetection.add(new BoolSetting.Builder()
        .name("remove-blood")
        .description("Instead of recoloring the hit particle, makes it fully invisible. Only ever affects the actual blood/hit particle itself, regardless of mode below - for clearing every particle near a hit, see Radius Clear.")
        .defaultValue(false)
        .visible(hitDetectionEnabled::get)
        .build()
    );

    private final Setting<HitMode> hitDetectionMode = sgHitDetection.add(new EnumSetting.Builder<HitMode>()
        .name("mode")
        .description("Only affects when a particle from Hit Particles below gets caught, not which particles - that's always Hit Particles' job. On Hit matches it anywhere, no proximity needed. Near Entities also requires it to spawn close to a living entity. Hurt Entities is like Near Entities, but also requires that entity to have actually lost health recently - more precise, avoids catching it near an untouched entity.")
        .defaultValue(HitMode.OnHit)
        .visible(hitDetectionEnabled::get)
        .build()
    );

    private final Setting<Map<ParticleKey, SettingColor>> hitParticleTypes = sgHitDetection.add(new ParticleColorMapSetting.Builder()
        .name("hit-particles")
        .description("Which particle type(s) Hit Detection touches, in every mode, and the color/mode to recolor each with - add your server's own AOE/ability particle here if it isn't vanilla's damage indicator. Defaults to vanilla's own hit particle.")
        .defaultValue(Map.of(ParticleKey.of(ParticleTypes.DAMAGE_INDICATOR), new ParticleRecolor(255, 255, 255, 255)))
        .visible(hitDetectionEnabled::get)
        .build()
    );

    private final Setting<Double> radius = sgHitDetection.add(new DoubleSetting.Builder()
        .name("radius")
        .description("How close a particle needs to spawn to a living entity to count, in Near Entities/Hurt Entities mode.")
        .defaultValue(2)
        .min(0.5)
        .sliderRange(0.5, 8)
        .visible(() -> hitDetectionEnabled.get() && (hitDetectionMode.get() == HitMode.NearEntities || hitDetectionMode.get() == HitMode.HurtEntities))
        .build()
    );

    private final Setting<Boolean> disregardPlayers = sgHitDetection.add(new BoolSetting.Builder()
        .name("disregard-players")
        .description("Ignores players entirely when checking proximity in Near Entities/Hurt Entities mode - a hit particle only counts if it's near/on a non-player entity, so your own hits/hurts (or other players') never trigger it.")
        .defaultValue(false)
        .visible(() -> hitDetectionEnabled.get() && (hitDetectionMode.get() == HitMode.NearEntities || hitDetectionMode.get() == HitMode.HurtEntities))
        .build()
    );

    private final Setting<Integer> hurtWindow = sgHitDetection.add(new IntSetting.Builder()
        .name("hurt-window")
        .description("How recently (in milliseconds) an entity needs to have lost health to still count as \"hurt\", in Hurt Entities mode.")
        .defaultValue(500)
        .range(0, 5000)
        .sliderRange(100, 2000)
        .visible(() -> hitDetectionEnabled.get() && hitDetectionMode.get() == HitMode.HurtEntities)
        .build()
    );

    private boolean hurtEntitiesModeUsed() {
        return (hitDetectionEnabled.get() && hitDetectionMode.get() == HitMode.HurtEntities)
            || (radiusClearEnabled.get() && radiusClearTrigger.get() == RadiusClearTrigger.HurtEntities);
    }

    private final Setting<Boolean> trackNametagHealth = sgHitDetection.add(new BoolSetting.Builder()
        .name("track-nametag-health")
        .description("Also counts as \"hurt\" (Hurt Entities mode, and Radius Clear's Hurt Entities trigger) whenever a nearby entity's own displayed name shows its health dropping, parsed with the pattern below - on top of the usual real health/hit-animation check. Needed for damage that doesn't trigger a normal hit (e.g. a bleed/DoT tick) on a server where a mob's \"real\" health is a custom number shown in its nametag rather than actual vanilla HP, so the normal check never fires for it.")
        .defaultValue(false)
        .visible(this::hurtEntitiesModeUsed)
        .build()
    );

    private final Setting<String> nametagHealthPattern = sgHitDetection.add(new StringSetting.Builder()
        .name("nametag-health-pattern")
        .description("Regex with one capture group for current health, run against each nearby entity's own displayed name. Same idea as Damage Numbers HUD's own Nametag Pattern, but tracked completely separately, for every nearby entity instead of just yourself.")
        .defaultValue("(\\d+(?:\\.\\d+)?)\\s*❤")
        .visible(() -> hurtEntitiesModeUsed() && trackNametagHealth.get())
        .build()
    );

    // General Particles

    private final Setting<Boolean> generalEnabled = sgGeneralParticles.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Recolors specific particle types with their own color, independently of Hit Detection above. A type listed here overrides whatever color Hit Detection would have given it.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Map<ParticleKey, SettingColor>> particleColors = sgGeneralParticles.add(new ParticleColorMapSetting.Builder()
        .name("particles")
        .description("Which particle types to recolor, and the color for each. Set alpha to 0 to hide a specific type entirely.")
        .visible(generalEnabled::get)
        .build()
    );

    // Radius Clear

    private final Setting<Boolean> radiusClearEnabled = sgRadiusClear.add(new BoolSetting.Builder()
        .name("radius-clear")
        .description("Radius Clear on/off: hides every particle (or just the ones listed below) spawned near an entity, independently of Hit Detection and General Particles above. Useful on its own for clearing out Wither-spawned particle clutter during a boss fight - see Near Entity Types below.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Keybind> radiusClearKey = sgRadiusClear.add(new KeybindSetting.Builder()
        .name("radius-clear-key")
        .description("Key that toggles Radius Clear on/off while Particle Color is enabled.")
        .defaultValue(Keybind.none())
        .action(() -> {
            radiusClearEnabled.set(!radiusClearEnabled.get());
            info("Radius Clear %s.", radiusClearEnabled.get() ? "on" : "off");
        })
        .build()
    );

    private final Setting<RadiusClearTrigger> radiusClearTrigger = sgRadiusClear.add(new EnumSetting.Builder<RadiusClearTrigger>()
        .name("trigger")
        .description("Near Entities clears particles near any living entity, all the time. Hurt Entities only clears particles near an entity that recently lost health. Near Entity Types only clears particles near one of the specific entity types picked below (e.g. Withers), regardless of health/hurt state.")
        .defaultValue(RadiusClearTrigger.HurtEntities)
        .visible(radiusClearEnabled::get)
        .build()
    );

    private final Setting<java.util.Set<net.minecraft.entity.EntityType<?>>> radiusClearEntityTypes = sgRadiusClear.add(new EntityTypeListSetting.Builder()
        .name("entity-types")
        .description("Which entity types count for Near Entity Types - particles are cleared near any living entity of one of these types, regardless of whether they're hurt.")
        .defaultValue(net.minecraft.entity.EntityType.WITHER)
        .visible(() -> radiusClearEnabled.get() && radiusClearTrigger.get() == RadiusClearTrigger.NearEntityTypes)
        .build()
    );

    private final Setting<Double> radiusClearRadius = sgRadiusClear.add(new DoubleSetting.Builder()
        .name("radius")
        .description("How close a particle needs to spawn to a qualifying entity to be cleared.")
        .defaultValue(3)
        .min(0.5)
        .sliderRange(0.5, 8)
        .visible(radiusClearEnabled::get)
        .build()
    );

    private final Setting<Integer> radiusClearHurtWindow = sgRadiusClear.add(new IntSetting.Builder()
        .name("hurt-window")
        .description("How recently (in milliseconds) an entity needs to have lost health to still count as \"hurt\".")
        .defaultValue(500)
        .range(0, 5000)
        .sliderRange(100, 2000)
        .visible(() -> radiusClearEnabled.get() && radiusClearTrigger.get() == RadiusClearTrigger.HurtEntities)
        .build()
    );

    private final Setting<RadiusClearScope> radiusClearScope = sgRadiusClear.add(new EnumSetting.Builder<RadiusClearScope>()
        .name("scope")
        .description("All clears every particle in radius. Listed only clears the particle types picked below.")
        .defaultValue(RadiusClearScope.All)
        .visible(radiusClearEnabled::get)
        .build()
    );

    private final Setting<Map<ParticleKey, SettingColor>> radiusClearParticles = sgRadiusClear.add(new ParticleColorMapSetting.Builder()
        .name("particles")
        .description("Which particle types to clear in Listed scope. Colors picked here are ignored - only which particles are listed matters.")
        .visible(() -> radiusClearEnabled.get() && radiusClearScope.get() == RadiusClearScope.Listed)
        .colorsMatter(false)
        .build()
    );

    // Perm Clear - a Wither (or anything else you add) makes so many particles that even a
    // generous Radius Clear radius can still leave some visible (its skull attacks travel well
    // past any sane radius before they explode) - this drops the proximity requirement entirely:
    // while a matching entity exists ANYWHERE in the loaded world, its particles are cleared no
    // matter how far from you they spawn. Deliberately separate from Radius Clear above rather
    // than another one of its own triggers, since "no distance limit at all" is a meaningfully
    // different, more aggressive thing than "near an entity" ever was.

    private final Setting<Boolean> permClearEnabled = sgPermClear.add(new BoolSetting.Builder()
        .name("perm-clear")
        .description("Perm Clear on/off: hides every particle (or just the ones listed below) while any entity of the types below exists anywhere in the loaded world - no distance limit, unlike Radius Clear above.")
        .defaultValue(false)
        .build()
    );

    private final Setting<java.util.Set<net.minecraft.entity.EntityType<?>>> permClearEntityTypes = sgPermClear.add(new EntityTypeListSetting.Builder()
        .name("entity-types")
        .description("While any living entity of one of these types exists anywhere in the loaded world, its particles are cleared regardless of distance.")
        .defaultValue(net.minecraft.entity.EntityType.WITHER)
        .visible(permClearEnabled::get)
        .build()
    );

    private final Setting<RadiusClearScope> permClearScope = sgPermClear.add(new EnumSetting.Builder<RadiusClearScope>()
        .name("scope")
        .description("All clears every particle while a qualifying entity exists. Listed only clears the particle types picked below.")
        .defaultValue(RadiusClearScope.All)
        .visible(permClearEnabled::get)
        .build()
    );

    private final Setting<Map<ParticleKey, SettingColor>> permClearParticles = sgPermClear.add(new ParticleColorMapSetting.Builder()
        .name("particles")
        .description("Which particle types to clear in Listed scope. Colors picked here are ignored - only which particles are listed matters.")
        .visible(() -> permClearEnabled.get() && permClearScope.get() == RadiusClearScope.Listed)
        .colorsMatter(false)
        .build()
    );

    // Updated once per tick (not once per particle - a busy fight can spawn dozens within the
    // same tick, no need to re-scan the entire world's entities for each one).
    private boolean permClearEntityPresent = false;

    // Hurt tracking, for Hurt Entities mode

    // Any hurt record older than this is useless - both hurt-window settings cap at 5000ms.
    private static final long HURT_TIMESTAMP_TTL_MS = 10_000;

    // The entity's position AT the moment it was hurt, captured immediately - not looked up later,
    // since a killed entity can be removed from the world before its own death particles spawn,
    // which would make it impossible to find via a live entity query at that point.
    private record HurtRecord(long timestamp, double x, double y, double z, float width, float height, boolean isPlayer) {
    }

    private final Map<Integer, Float> lastHealth = new HashMap<>();
    private final Map<Integer, Integer> lastHurtTime = new HashMap<>();
    private final Map<Integer, Float> lastNametagHealth = new HashMap<>();
    private final Map<Integer, HurtRecord> hurtRecords = new HashMap<>();
    private final Set<Integer> seenThisTick = new HashSet<>();

    // Near Entities tracking - a snapshot of every living entity's position/hitbox, rebuilt once per
    // tick in onTick() instead of once per particle. isNearEntity() used to call
    // world.getEntitiesByClass() fresh for every single matching particle - fine for an occasional
    // particle, but a busy fight easily spawns dozens of hit particles within the same tick, each one
    // repeating a full world entity-area query (own internal traversal + list allocation) for
    // essentially the same answer. One tick-cached snapshot, checked per particle like the already-
    // cached hurtRecords below, removes that per-particle cost entirely.
    private record NearRecord(double x, double y, double z, float width, float height, boolean isPlayer) {
    }

    private final List<NearRecord> nearEntities = new ArrayList<>();
    private final List<NearRecord> nearEntityTypeMatches = new ArrayList<>();

    public ParticleColor() {
        super(Categories.Render, "particle-color", "Recolors particles, such as the blood/hit particle, client-side.");

        // Display name only - keeping the internal id "particle-color" so existing saved settings
        // and .particle-color commands keep working unchanged.
        title = "Particle Adjust";
    }

    // First recolor of each particle type per activation, told in chat (capped) so "nothing changes"
    // can be told apart from "wrong particle/settings" - silent by default, only actually prints
    // through DebugLog's gate (needs .dev AND its own switch on), so normal use never sees this.
    private final Set<String> announced = new HashSet<>();

    public void noteRecolor(ParticleEffect parameters, ParticleRecolor.Mode mode, Color color, boolean general) {
        if (!DebugLog.particleRecolorEnabled() || announced.size() >= 6) return;

        String key = ParticleKey.from(parameters).toSaveString();
        if (!announced.add(key)) return;

        DebugLog.log("Recolored %s (%s list, mode %s, color %d,%d,%d, blend %d, overall alpha %d).", key, general ? "General" : "Hit Detection", mode, color.r, color.g, color.b, color.a, overallAlpha.get());
    }

    @Override
    public void onActivate() {
        announced.clear();
        lastHealth.clear();
        lastHurtTime.clear();
        lastNametagHealth.clear();
        hurtRecords.clear();
        nearEntities.clear();
    }

    // Catches the one case the tick-based scan in onTick() below can't: a killing blow. That scan
    // only notices a hurt by comparing this tick's hurtTime/health against last tick's - but on a
    // kill, the server can send the hurt packet AND the entity's removal within the same client tick,
    // so by the time onTick() next runs and iterates mc.world.getEntities(), the entity is already
    // gone and was never compared against at all, silently dropping the hit that mattered most.
    // EntityDamageS2CPacket (what actually drives the hurt flash/hurtTime, replacing the old generic
    // entity-status byte in modern versions) is handled here instead, synchronously as it's received -
    // always before any later removal packet for the same kill - so the position gets captured
    // immediately regardless of what happens to the entity a moment later.
    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityDamageS2CPacket packet)) return;
        if (mc.world == null) return;

        boolean needHurtTracking = (hitDetectionEnabled.get() && hitDetectionMode.get() == HitMode.HurtEntities)
            || (radiusClearEnabled.get() && radiusClearTrigger.get() == RadiusClearTrigger.HurtEntities);
        if (!needHurtTracking) return;

        if (!(mc.world.getEntityById(packet.entityId()) instanceof LivingEntity living)) return;

        hurtRecords.put(living.getId(), new HurtRecord(System.currentTimeMillis(), living.getX(), living.getY(), living.getZ(), living.getWidth(), living.getHeight(), living instanceof net.minecraft.entity.player.PlayerEntity));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null) {
            permClearEntityPresent = false;
            return;
        }

        if (!permClearEnabled.get()) {
            permClearEntityPresent = false;
        } else {
            boolean found = false;
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof LivingEntity && permClearEntityTypes.get().contains(entity.getType())) {
                    found = true;
                    break;
                }
            }
            permClearEntityPresent = found;
        }

        boolean needHurtTracking = (hitDetectionEnabled.get() && hitDetectionMode.get() == HitMode.HurtEntities)
            || (radiusClearEnabled.get() && radiusClearTrigger.get() == RadiusClearTrigger.HurtEntities);
        boolean needNearTracking = (hitDetectionEnabled.get() && hitDetectionMode.get() == HitMode.NearEntities)
            || (radiusClearEnabled.get() && radiusClearTrigger.get() == RadiusClearTrigger.NearEntities);
        boolean needNearTypeTracking = radiusClearEnabled.get() && radiusClearTrigger.get() == RadiusClearTrigger.NearEntityTypes;

        if (!needHurtTracking) {
            if (!lastHealth.isEmpty()) lastHealth.clear();
            if (!lastHurtTime.isEmpty()) lastHurtTime.clear();
            if (!hurtRecords.isEmpty()) hurtRecords.clear();
        }

        if (!needNearTracking) {
            if (!nearEntities.isEmpty()) nearEntities.clear();
        }

        if (!needNearTypeTracking) {
            if (!nearEntityTypeMatches.isEmpty()) nearEntityTypeMatches.clear();
        }

        if (!needHurtTracking && !needNearTracking && !needNearTypeTracking) return;

        // Entities that leave range/despawn are never removed from these maps otherwise - left
        // unchecked they grow for the entire session, one entry per entity ever seen.
        seenThisTick.clear();
        nearEntities.clear();
        nearEntityTypeMatches.clear();

        // Compiled once per tick, not once per entity below - trackNametagHealth.get() is already a
        // per-entity regex match on top of the raw-health/hurtTime checks, no need to also re-parse
        // the pattern string itself for every single nearby entity.
        boolean nametagTracking = needHurtTracking && trackNametagHealth.get();
        Pattern nametagPattern = null;
        if (nametagTracking) {
            try {
                nametagPattern = Pattern.compile(nametagHealthPattern.get());
            } catch (Exception ignored) {
                nametagTracking = false;
            }
        }
        if (!nametagTracking && !lastNametagHealth.isEmpty()) lastNametagHealth.clear();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;

            if (needNearTracking) {
                nearEntities.add(new NearRecord(living.getX(), living.getY(), living.getZ(), living.getWidth(), living.getHeight(), living instanceof net.minecraft.entity.player.PlayerEntity));
            }

            if (needNearTypeTracking && radiusClearEntityTypes.get().contains(living.getType())) {
                nearEntityTypeMatches.add(new NearRecord(living.getX(), living.getY(), living.getZ(), living.getWidth(), living.getHeight(), false));
            }

            if (!needHurtTracking) continue;

            int id = living.getId();
            seenThisTick.add(id);

            float health = living.getHealth();
            Float prev = lastHealth.put(id, health);

            // Normally a hurt is "health dropped since last tick" - but an entity that dies before
            // ever being tracked (e.g. one-shot the instant it comes into view/render distance) has
            // no previous reading to compare against, so treat "not at full health" as evidence of
            // a hurt on first observation instead of silently missing it.
            boolean justHurt = prev != null ? health < prev : health < living.getMaxHealth();

            // Servers with their own health system (mobs whose HP lives in a name tag) keep the vanilla health value
            // untouched until they finally die - so a health drop only ever caught dying enemies. The client's own
            // damage flash (hurtTime, set by the hurt animation packet on every hit) is what's there for every hit:
            // a rise from the previous tick means "just got hit".
            int hurtTime = living.hurtTime;
            Integer prevHurtTime = lastHurtTime.put(id, hurtTime);
            if (hurtTime > (prevHurtTime != null ? prevHurtTime : 0)) justHurt = true;

            // Catches damage that changes neither of the above - a DoT tick (e.g. a bleed effect)
            // applied server-side without a normal hit/knockback packet, on a mob whose "real" health
            // is a custom nametag number rather than actual vanilla HP (see Track Nametag Health).
            if (nametagTracking) {
                Matcher m = nametagPattern.matcher(living.getName().getString());
                if (m.find()) {
                    try {
                        float nametagHealth = Float.parseFloat(m.group(1));
                        Float prevNametagHealth = lastNametagHealth.put(id, nametagHealth);
                        if (prevNametagHealth != null && nametagHealth < prevNametagHealth) justHurt = true;
                    } catch (NumberFormatException ignored) {}
                }
            }

            if (justHurt) hurtRecords.put(id, new HurtRecord(System.currentTimeMillis(), living.getX(), living.getY(), living.getZ(), living.getWidth(), living.getHeight(), living instanceof net.minecraft.entity.player.PlayerEntity));
        }

        if (needHurtTracking) {
            lastHealth.keySet().retainAll(seenThisTick);
            lastHurtTime.keySet().retainAll(seenThisTick);
            lastNametagHealth.keySet().retainAll(seenThisTick);

            long now = System.currentTimeMillis();
            hurtRecords.values().removeIf(record -> now - record.timestamp() > HURT_TIMESTAMP_TTL_MS);
        }
    }

    private boolean matchesHit(ParticleEffect parameters, double x, double y, double z) {
        // The Hit Particles list is the one selector for *which* particles Hit Detection can ever
        // touch, in every mode. Mode only adds an extra trigger condition on top of that: On Hit
        // has none (the type match alone is enough), Near/Hurt Entities also require proximity.
        if (!hitParticleTypes.get().containsKey(ParticleKey.from(parameters))) return false;

        return switch (hitDetectionMode.get()) {
            case OnHit -> true;
            case NearEntities -> isNearEntity(nearEntities, x, y, z, radius.get(), disregardPlayers.get());
            case HurtEntities -> isNearHurtEntity(x, y, z, radius.get(), hurtWindow.get(), disregardPlayers.get());
        };
    }

    private boolean matchesRadiusClear(ParticleEffect parameters, double x, double y, double z) {
        if (radiusClearScope.get() == RadiusClearScope.Listed && !radiusClearParticles.get().containsKey(ParticleKey.from(parameters))) return false;

        return switch (radiusClearTrigger.get()) {
            case HurtEntities -> isNearHurtEntity(x, y, z, radiusClearRadius.get(), radiusClearHurtWindow.get(), false);
            case NearEntityTypes -> isNearEntity(nearEntityTypeMatches, x, y, z, radiusClearRadius.get(), false);
            case NearEntities -> isNearEntity(nearEntities, x, y, z, radiusClearRadius.get(), false);
        };
    }

    // Checked against a tick-cached snapshot (see nearEntities'/nearEntityTypeMatches' own field
    // comments) instead of a live world.getEntitiesByClass() query - this runs once per matching
    // particle, and a busy fight can spawn dozens of those within the same tick, so a fresh world
    // query per particle adds up fast.
    private boolean isNearEntity(List<NearRecord> records, double x, double y, double z, double r, boolean disregardPlayers) {
        for (NearRecord record : records) {
            if (disregardPlayers && record.isPlayer()) continue;

            double halfWidth = record.width() / 2.0;
            Box box = new Box(
                record.x() - halfWidth - r, record.y() - r, record.z() - halfWidth - r,
                record.x() + halfWidth + r, record.y() + record.height() + r, record.z() + halfWidth + r
            );

            if (box.contains(x, y, z)) return true;
        }

        return false;
    }

    private boolean isNearHurtEntity(double x, double y, double z, double r, long window, boolean disregardPlayers) {
        // Checks recorded hurt events directly, rather than querying which entities are currently
        // alive nearby - a killed entity can be removed from the world before its own death
        // particles spawn, so a live-entity query would never find it at exactly the moment that
        // matters most. The position was captured at hurt-time in onTick, so this works regardless
        // of whether the entity still exists.
        long now = System.currentTimeMillis();

        for (HurtRecord record : hurtRecords.values()) {
            if (now - record.timestamp() > window) continue;
            if (disregardPlayers && record.isPlayer()) continue;

            // Checked against the entity's own hitbox (as it was at hurt-time), expanded by r, not a
            // bare point at its feet - a hit particle on a large boss can spawn several blocks from
            // its feet-position origin, well outside a small fixed radius from that single point,
            // which made Hurt Entities silently miss real hits on anything bigger than a player.
            double halfWidth = record.width() / 2.0;
            Box box = new Box(
                record.x() - halfWidth - r, record.y() - r, record.z() - halfWidth - r,
                record.x() + halfWidth + r, record.y() + record.height() + r, record.z() + halfWidth + r
            );

            if (box.contains(x, y, z)) return true;
        }

        return false;
    }

    public boolean isGeneral(ParticleEffect parameters) {
        return isGeneralMatch(parameters);
    }

    private boolean isGeneralMatch(ParticleEffect parameters) {
        return generalEnabled.get() && particleColors.get().containsKey(ParticleKey.from(parameters));
    }

    public boolean shouldClearInRadius(ParticleEffect parameters, double x, double y, double z) {
        if (!isActive() || !radiusClearEnabled.get()) return false;

        return matchesRadiusClear(parameters, x, y, z);
    }

    /** No position needed - Perm Clear doesn't care where the particle spawned, only whether a qualifying entity exists anywhere right now (see permClearEntityPresent, updated once per tick). */
    public boolean shouldPermClear(ParticleEffect parameters) {
        if (!isActive() || !permClearEnabled.get() || !permClearEntityPresent) return false;

        return permClearScope.get() == RadiusClearScope.All || permClearParticles.get().containsKey(ParticleKey.from(parameters));
    }

    public boolean shouldHide(ParticleEffect parameters, double x, double y, double z) {
        if (!isActive()) return false;
        if (isGeneralMatch(parameters)) return false;
        if (!hitDetectionEnabled.get() || !removeBlood.get()) return false;

        return matchesHit(parameters, x, y, z);
    }

    public boolean shouldRecolor(ParticleEffect parameters, double x, double y, double z) {
        if (!isActive()) return false;
        if (isGeneralMatch(parameters)) return true;
        if (!hitDetectionEnabled.get() || removeBlood.get()) return false;

        return matchesHit(parameters, x, y, z);
    }

    /** How the matched entry's tint gets applied - always the entry's own mode, from whichever list matched (General Particles or Hit Particles). Each entry controls its own color/mode independently - there's no single shared control. */
    public ParticleRecolor.Mode getRecolorMode(ParticleEffect parameters) {
        ParticleKey key = ParticleKey.from(parameters);
        SettingColor entry = isGeneralMatch(parameters) ? particleColors.get().get(key) : hitParticleTypes.get().get(key);
        return entry instanceof ParticleRecolor recolor ? recolor.mode : ParticleRecolor.Mode.Colorize;
    }

    public Color getColor(ParticleEffect parameters) {
        ParticleKey key = ParticleKey.from(parameters);
        SettingColor entry = isGeneralMatch(parameters) ? particleColors.get().get(key) : hitParticleTypes.get().get(key);
        return entry != null ? entry : Color.WHITE;
    }

    /** The matched entry itself (for HueShift mode's own Speed/Range, which live on the entry, not as a shared module-level setting) - null if nothing matched or the match isn't a ParticleRecolor. */
    public ParticleRecolor getRecolorEntry(ParticleEffect parameters) {
        ParticleKey key = ParticleKey.from(parameters);
        SettingColor entry = isGeneralMatch(parameters) ? particleColors.get().get(key) : hitParticleTypes.get().get(key);
        return entry instanceof ParticleRecolor recolor ? recolor : null;
    }

    /** How see-through the final recolored particle is, independent of the tint's own blend strength. */
    public float getOverallAlpha() {
        return overallAlpha.get() / 255f;
    }

    /** Contrast factor (1 = unchanged) applied to the final recolored color, pushing it away from/toward mid-gray. */
    public float getContrast() {
        return contrast.get() / 100f;
    }

    public boolean overrideLighting() {
        return overrideLighting.get();
    }

    /** Packed sky+block light, both set to Light Level - matches LightmapTextureManager.pack(block, sky) = (block << 4) | (sky << 20). */
    public int getPackedLightOverride() {
        int level = lightLevel.get();
        return (level << 4) | (level << 20);
    }

}
