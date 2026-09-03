/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recolors particles client-side - originally for censoring the redstone-colored "blood" particle
 * (ParticleTypes.DAMAGE_INDICATOR) that spawns when a mob or player is hit, generalized to work on
 * any particle type. Hit Detection's "Hit Particles" list is the one selector for which particle
 * type(s) it's ever allowed to touch, in every mode - Mode only adds an extra trigger condition on
 * top of that (On Hit: none, Near/Hurt Entities: also requires proximity to a living/recently-hurt
 * entity). Add your server's own AOE/ability particle to that list if it isn't vanilla's damage
 * indicator - for catching an unknown particle type near a hit regardless of type, see Radius Clear.
 *
 * Split into two independent sections: Hit Detection (an auto-detection heuristic sharing one
 * color/mode) and General Particles (an explicit per-type list, always active independently of
 * Hit Detection). A particle type can be covered by both at once - if it's also listed in General
 * Particles, that explicit color wins over whatever Hit Detection would have picked, which is the
 * way to give one specific particle type its own color distinct from the shared hit-detection color.
 */
public class ParticleColor extends Module {
    public enum HitMode {
        OnHit,
        NearEntities,
        HurtEntities
    }

    public enum ColorMode {
        Static,
        Rainbow,
        Gradient,
        Flashing,
        HueShift
    }

    public enum RainbowTransition {
        Soft,
        Hard
    }

    public enum RadiusClearTrigger {
        NearEntities,
        HurtEntities
    }

    public enum RadiusClearScope {
        All,
        Listed
    }

    private final SettingGroup sgHitDetection = settings.createGroup("Hit Detection");
    private final SettingGroup sgStatic = settings.createGroup("Color Mode (Static)");
    private final SettingGroup sgRainbow = settings.createGroup("Color Mode (Rainbow)");
    private final SettingGroup sgGradient = settings.createGroup("Color Mode (Gradient)");
    private final SettingGroup sgFlashing = settings.createGroup("Color Mode (Flashing)");
    private final SettingGroup sgHueShift = settings.createGroup("Color Mode (Hue Shift)");
    private final SettingGroup sgBlending = settings.createGroup("Blending");
    private final SettingGroup sgGeneralParticles = settings.createGroup("General Particles");
    private final SettingGroup sgRadiusClear = settings.createGroup("Radius Clear");

    // Blending

    private final Setting<Integer> overallAlpha = sgBlending.add(new IntSetting.Builder()
        .name("overall-alpha")
        .description("Extra transparency applied to every recolored particle, separate from the tint color's own alpha. That alpha controls how strongly the tint blends into the particle's original color (0 = untouched original color, 255 = fully replaced) - this one controls how see-through the end result is.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
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
        .description("Which particle type(s) Hit Detection is allowed to touch, in every mode - add your server's own AOE/ability particle here if it isn't vanilla's damage indicator. Colors picked here are ignored, only which particles are listed matters. Defaults to vanilla's own hit particle.")
        .defaultValue(Map.of(ParticleKey.of(ParticleTypes.DAMAGE_INDICATOR), new SettingColor(255, 255, 255, 255)))
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

    private final Setting<Integer> hurtWindow = sgHitDetection.add(new IntSetting.Builder()
        .name("hurt-window")
        .description("How recently (in milliseconds) an entity needs to have lost health to still count as \"hurt\", in Hurt Entities mode.")
        .defaultValue(500)
        .range(0, 5000)
        .sliderRange(100, 2000)
        .visible(() -> hitDetectionEnabled.get() && hitDetectionMode.get() == HitMode.HurtEntities)
        .build()
    );

    private final Setting<ColorMode> colorMode = sgHitDetection.add(new EnumSetting.Builder<ColorMode>()
        .name("color-mode")
        .description("How the recolor color is picked.")
        .defaultValue(ColorMode.Static)
        .visible(hitDetectionEnabled::get)
        .build()
    );

    // Static

    private final Setting<SettingColor> staticColor = sgStatic.add(new ColorSetting.Builder()
        .name("color")
        .description("The color (and alpha) to tint matching particles.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(() -> hitDetectionEnabled.get() && colorMode.get() == ColorMode.Static)
        .build()
    );

    // Rainbow

    private final Setting<RainbowTransition> rainbowTransition = sgRainbow.add(new EnumSetting.Builder<RainbowTransition>()
        .name("transition")
        .description("Soft smoothly cycles through every hue. Hard jumps between a fixed set of hues with no blending.")
        .defaultValue(RainbowTransition.Soft)
        .visible(() -> hitDetectionEnabled.get() && colorMode.get() == ColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> rainbowSteps = sgRainbow.add(new IntSetting.Builder()
        .name("hard-steps")
        .description("How many distinct hues to jump between, in Hard mode.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 16)
        .visible(() -> hitDetectionEnabled.get() && colorMode.get() == ColorMode.Rainbow && rainbowTransition.get() == RainbowTransition.Hard)
        .build()
    );

    private final Setting<Double> rainbowSpeed = sgRainbow.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the rainbow cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> hitDetectionEnabled.get() && colorMode.get() == ColorMode.Rainbow)
        .build()
    );

    private final Setting<Integer> rainbowAlpha = sgRainbow.add(new IntSetting.Builder()
        .name("alpha")
        .description("Alpha (opacity) for the rainbow colors.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .visible(() -> hitDetectionEnabled.get() && colorMode.get() == ColorMode.Rainbow)
        .build()
    );

    // Gradient

    private final Setting<List<SettingColor>> gradientColors = sgGradient.add(new ColorListSetting.Builder()
        .name("colors")
        .description("The colors to cycle between. Needs at least 2.")
        .defaultValue(List.of(new SettingColor(255, 0, 0, 255), new SettingColor(0, 0, 255, 255)))
        .visible(() -> hitDetectionEnabled.get() && colorMode.get() == ColorMode.Gradient)
        .build()
    );

    private final Setting<Double> gradientSpeed = sgGradient.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the gradient cycles.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> hitDetectionEnabled.get() && colorMode.get() == ColorMode.Gradient)
        .build()
    );

    // Flashing

    private final Setting<List<TimedColorEntry>> flashColors = sgFlashing.add(new TimedColorListSetting.Builder()
        .name("colors")
        .description("The colors to hard-switch between, each with its own hold duration in game ticks. Needs at least 2.")
        .defaultValue(List.of(new TimedColorEntry(new SettingColor(255, 0, 0, 255), 10), new TimedColorEntry(new SettingColor(0, 0, 255, 255), 10)))
        .visible(() -> hitDetectionEnabled.get() && colorMode.get() == ColorMode.Flashing)
        .build()
    );

    // Hue Shift

    private final Setting<SettingColor> hueShiftBaseColor = sgHueShift.add(new ColorSetting.Builder()
        .name("base-color")
        .description("Starting color - its hue continuously rotates, keeping its saturation/brightness/alpha, unlike Rainbow which always uses full saturation/brightness regardless of the base color.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(() -> hitDetectionEnabled.get() && colorMode.get() == ColorMode.HueShift)
        .build()
    );

    private final Setting<Double> hueShiftSpeed = sgHueShift.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the hue rotates.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> hitDetectionEnabled.get() && colorMode.get() == ColorMode.HueShift)
        .build()
    );

    private final Setting<Double> hueShiftRange = sgHueShift.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far the hue swings from the base color, in degrees each direction, instead of cycling continuously - keeps it reading as a variation of the base color rather than looking like Rainbow mode. 180 = swings the full way around.")
        .defaultValue(30)
        .range(0, 180)
        .sliderRange(0, 180)
        .visible(() -> hitDetectionEnabled.get() && colorMode.get() == ColorMode.HueShift)
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
        .name("enabled")
        .description("Hides every particle (or just the ones listed below) spawned near an entity, independently of Hit Detection and General Particles above.")
        .defaultValue(false)
        .build()
    );

    private final Setting<RadiusClearTrigger> radiusClearTrigger = sgRadiusClear.add(new EnumSetting.Builder<RadiusClearTrigger>()
        .name("trigger")
        .description("Near Entities clears particles near any living entity, all the time. Hurt Entities only clears particles near an entity that recently lost health.")
        .defaultValue(RadiusClearTrigger.HurtEntities)
        .visible(radiusClearEnabled::get)
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
        .build()
    );

    // Hurt tracking, for Hurt Entities mode

    // Any hurt record older than this is useless - both hurt-window settings cap at 5000ms.
    private static final long HURT_TIMESTAMP_TTL_MS = 10_000;

    // The entity's position AT the moment it was hurt, captured immediately - not looked up later,
    // since a killed entity can be removed from the world before its own death particles spawn,
    // which would make it impossible to find via a live entity query at that point.
    private record HurtRecord(long timestamp, double x, double y, double z) {
    }

    private final Map<Integer, Float> lastHealth = new HashMap<>();
    private final Map<Integer, HurtRecord> hurtRecords = new HashMap<>();
    private final Set<Integer> seenThisTick = new HashSet<>();

    public ParticleColor() {
        super(Categories.Render, "particle-color", "Recolors particles, such as the blood/hit particle, client-side.");

        // Display name only - keeping the internal id "particle-color" so existing saved settings
        // and .particle-color commands keep working unchanged.
        title = "Particle Adjust";
    }

    @Override
    public void onActivate() {
        lastHealth.clear();
        hurtRecords.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null) return;

        boolean needHurtTracking = (hitDetectionEnabled.get() && hitDetectionMode.get() == HitMode.HurtEntities)
            || (radiusClearEnabled.get() && radiusClearTrigger.get() == RadiusClearTrigger.HurtEntities);

        if (!needHurtTracking) {
            if (!lastHealth.isEmpty()) lastHealth.clear();
            if (!hurtRecords.isEmpty()) hurtRecords.clear();
            return;
        }

        // Entities that leave range/despawn are never removed from these maps otherwise - left
        // unchecked they grow for the entire session, one entry per entity ever seen.
        seenThisTick.clear();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;

            int id = living.getId();
            seenThisTick.add(id);

            float health = living.getHealth();
            Float prev = lastHealth.put(id, health);

            // Normally a hurt is "health dropped since last tick" - but an entity that dies before
            // ever being tracked (e.g. one-shot the instant it comes into view/render distance) has
            // no previous reading to compare against, so treat "not at full health" as evidence of
            // a hurt on first observation instead of silently missing it.
            boolean justHurt = prev != null ? health < prev : health < living.getMaxHealth();
            if (justHurt) hurtRecords.put(id, new HurtRecord(System.currentTimeMillis(), living.getX(), living.getY(), living.getZ()));
        }

        lastHealth.keySet().retainAll(seenThisTick);

        long now = System.currentTimeMillis();
        hurtRecords.values().removeIf(record -> now - record.timestamp() > HURT_TIMESTAMP_TTL_MS);
    }

    private boolean matchesHit(ParticleEffect parameters, double x, double y, double z) {
        // The Hit Particles list is the one selector for *which* particles Hit Detection can ever
        // touch, in every mode. Mode only adds an extra trigger condition on top of that: On Hit
        // has none (the type match alone is enough), Near/Hurt Entities also require proximity.
        if (!hitParticleTypes.get().containsKey(ParticleKey.from(parameters))) return false;

        return switch (hitDetectionMode.get()) {
            case OnHit -> true;
            case NearEntities -> isNearEntity(x, y, z, radius.get());
            case HurtEntities -> isNearHurtEntity(x, y, z, radius.get(), hurtWindow.get());
        };
    }

    private boolean matchesRadiusClear(ParticleEffect parameters, double x, double y, double z) {
        if (radiusClearScope.get() == RadiusClearScope.Listed && !radiusClearParticles.get().containsKey(ParticleKey.from(parameters))) return false;

        return radiusClearTrigger.get() == RadiusClearTrigger.HurtEntities
            ? isNearHurtEntity(x, y, z, radiusClearRadius.get(), radiusClearHurtWindow.get())
            : isNearEntity(x, y, z, radiusClearRadius.get());
    }

    private boolean isNearEntity(double x, double y, double z, double r) {
        if (mc.world == null) return false;

        Box box = new Box(x - r, y - r, z - r, x + r, y + r, z + r);

        return !mc.world.getEntitiesByClass(LivingEntity.class, box, entity -> true).isEmpty();
    }

    private boolean isNearHurtEntity(double x, double y, double z, double r, long window) {
        // Checks recorded hurt events directly, rather than querying which entities are currently
        // alive nearby - a killed entity can be removed from the world before its own death
        // particles spawn, so a live-entity query would never find it at exactly the moment that
        // matters most. The position was captured at hurt-time in onTick, so this works regardless
        // of whether the entity still exists.
        long now = System.currentTimeMillis();

        for (HurtRecord record : hurtRecords.values()) {
            if (now - record.timestamp() > window) continue;

            double dx = x - record.x(), dy = y - record.y(), dz = z - record.z();
            if (dx * dx + dy * dy + dz * dz <= r * r) return true;
        }

        return false;
    }

    private boolean isGeneralMatch(ParticleEffect parameters) {
        return generalEnabled.get() && particleColors.get().containsKey(ParticleKey.from(parameters));
    }

    public boolean shouldClearInRadius(ParticleEffect parameters, double x, double y, double z) {
        if (!isActive() || !radiusClearEnabled.get()) return false;

        return matchesRadiusClear(parameters, x, y, z);
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

    public Color getColor(ParticleEffect parameters) {
        if (isGeneralMatch(parameters)) return particleColors.get().get(ParticleKey.from(parameters));

        return switch (colorMode.get()) {
            case Static -> staticColor.get();
            case Rainbow -> getRainbowColor();
            case Gradient -> getGradientColor();
            case Flashing -> getFlashColor();
            case HueShift -> getHueShiftColor();
        };
    }

    /** How see-through the final recolored particle is, independent of the tint's own blend strength. */
    public float getOverallAlpha() {
        return overallAlpha.get() / 255f;
    }

    private Color getRainbowColor() {
        double time = System.currentTimeMillis() / 1000.0 * rainbowSpeed.get() * 60.0;

        float hue;
        if (rainbowTransition.get() == RainbowTransition.Soft) {
            hue = (float) (time % 360.0);
        } else {
            int steps = rainbowSteps.get();
            int step = (int) (time / (360.0 / steps)) % steps;
            hue = step * (360f / steps);
        }

        Color color = Color.fromHsv(hue, 1, 1);
        color.a = rainbowAlpha.get();
        return color;
    }

    private Color getGradientColor() {
        List<SettingColor> colors = gradientColors.get();
        if (colors.isEmpty()) return Color.WHITE;
        if (colors.size() == 1) return colors.get(0);

        double time = (System.currentTimeMillis() / 1000.0 * gradientSpeed.get()) % colors.size();
        int index = (int) time;
        float progress = (float) (time - index);

        SettingColor from = colors.get(index);
        SettingColor to = colors.get((index + 1) % colors.size());

        return new Color(
            (int) (from.r + (to.r - from.r) * progress),
            (int) (from.g + (to.g - from.g) * progress),
            (int) (from.b + (to.b - from.b) * progress),
            (int) (from.a + (to.a - from.a) * progress)
        );
    }

    private Color getFlashColor() {
        List<TimedColorEntry> entries = flashColors.get();
        if (entries.isEmpty()) return Color.WHITE;
        if (entries.size() == 1) return entries.get(0).color;

        long totalTicks = 0;
        for (TimedColorEntry entry : entries) totalTicks += Math.max(1, entry.ticks);

        long tick = System.currentTimeMillis() / 50; // ~1 game tick, assuming a stable 20 TPS
        long pos = tick % totalTicks;

        long accumulated = 0;
        for (TimedColorEntry entry : entries) {
            accumulated += Math.max(1, entry.ticks);
            if (pos < accumulated) return entry.color;
        }

        return entries.get(entries.size() - 1).color;
    }

    private Color getHueShiftColor() {
        SettingColor base = hueShiftBaseColor.get();
        float[] hsb = java.awt.Color.RGBtoHSB(base.r, base.g, base.b, null);

        double time = System.currentTimeMillis() / 1000.0 * hueShiftSpeed.get();
        double offset = Math.sin(time) * hueShiftRange.get();
        float hue = (float) (((hsb[0] * 360.0 + offset) % 360.0 + 360.0) % 360.0);

        Color color = Color.fromHsv(hue, hsb[1], hsb[2]);
        color.a = base.a;
        return color;
    }
}
