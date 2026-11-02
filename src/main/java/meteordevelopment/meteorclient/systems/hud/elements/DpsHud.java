/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Sums up floating damage numbers from nearby invisible armor stands (the classic
 * plugin/datapack way of showing damage indicators without a resource pack) over a rolling
 * time window and displays it as a DPS counter. Ported from a standalone mod of the same
 * purpose - see the "dps-meter-mc" project.
 */
public class DpsHud extends HudElement {

    @Override
    public boolean hasOwnBackground() {
        return true;
    }
    public static final HudElementInfo<DpsHud> INFO = new HudElementInfo<>(Hud.GROUP, "dps-meter", "Tracks damage from invisible armor stand damage indicators and displays DPS.", DpsHud::new);

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final long DEDUP_WINDOW_MS = 500L;

    public enum Mode {Solo, Team, OnHit}

    private record DamageEvent(long time, float damage) {}

    /** A recent attack/projectile-impact position - see Mode.OnHit. */
    private record HitRecord(double x, double y, double z, long timeMs) {
        boolean isNear(double ex, double ey, double ez, long now) {
            if (now - timeMs > 800L) return false;
            double dx = ex - x, dz = ez - z, dy = ey - y;
            return dx * dx + dz * dz < 36.0 && Math.abs(dy) < 5.0;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBackground = settings.createGroup("Background");
    private final SettingGroup sgScale = settings.createGroup("Scale");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Solo only counts damage from armor stands near you, Team counts all of them, On Hit only counts a damage number that shows up right after (and right where) you actually landed a hit - your own attacks and any projectile you fired that's disappeared (hit something).")
        .defaultValue(Mode.Solo)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How close a damage indicator needs to be to count, in Solo mode.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 32)
        .visible(() -> mode.get() == Mode.Solo)
        .build()
    );

    private final Setting<Boolean> vlMode = sgGeneral.add(new BoolSetting.Builder()
        .name("vl-mode")
        .description("Also filters by plausible damage range: your own Attack Damage attribute, adjusted by any \"+X% damage/dmg/atk/attack\" lines found in your armor's lore, times a wide margin either way to still allow crits/RNG - rejects a number way outside that as probably not yours. A heuristic, not exact - tell me if it's rejecting real hits or letting obvious outliers through and I'll adjust the margin.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> window = sgGeneral.add(new DoubleSetting.Builder()
        .name("window")
        .description("The rolling time window (in seconds) damage is averaged over.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> showMode = sgGeneral.add(new BoolSetting.Builder()
        .name("show-mode")
        .description("Shows the current mode next to the DPS value.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Text shadow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> activeColor = sgGeneral.add(new ColorSetting.Builder()
        .name("active-color")
        .description("Color used while DPS is above 0.")
        .defaultValue(new SettingColor(85, 255, 85))
        .build()
    );

    private final Setting<SettingColor> inactiveColor = sgGeneral.add(new ColorSetting.Builder()
        .name("inactive-color")
        .description("Color used while DPS is 0.")
        .defaultValue(new SettingColor(170, 170, 170))
        .build()
    );

    private final Setting<Boolean> background = sgBackground.add(new BoolSetting.Builder()
        .name("background")
        .description("Displays background.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgBackground.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color used for the background.")
        .visible(background::get)
        .defaultValue(new SettingColor(0, 0, 0, 128))
        .build()
    );

    private final Setting<Boolean> customScale = sgScale.add(new BoolSetting.Builder()
        .name("custom-scale")
        .description("Applies custom text scale rather than the global one.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> scale = sgScale.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Custom scale.")
        .visible(customScale::get)
        .defaultValue(1)
        .min(0.5)
        .sliderRange(0.5, 3)
        .build()
    );

    private static final Pattern PERCENT_PATTERN = Pattern.compile("([+-]?\\d+(?:\\.\\d+)?)\\s*%");

    private final Deque<DamageEvent> events = new ArrayDeque<>();
    private final Map<Integer, Long> lastSeen = new HashMap<>();

    // On Hit tracking
    private final List<HitRecord> recentHits = new ArrayList<>();
    private final Map<Integer, double[]> trackedProjectiles = new HashMap<>();

    public DpsHud() {
        super(INFO);

        MeteorClient.EVENT_BUS.subscribe(this);
    }

    // Your own melee attacks - the "you swung at it" half of On Hit mode.
    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!isActive() || mode.get() != Mode.OnHit || event.entity == null) return;

        recentHits.add(new HitRecord(event.entity.getX(), event.entity.getY(), event.entity.getZ(), System.currentTimeMillis()));
    }

    // Your own projectiles disappearing (hit something) - the ranged half of On Hit mode, plus
    // pruning old hit records regardless of mode so the list never grows unbounded.
    @EventHandler
    private void onTick(TickEvent.Post event) {
        long now = System.currentTimeMillis();
        recentHits.removeIf(h -> now - h.timeMs() > 2000L);

        if (!isActive() || mode.get() != Mode.OnHit || mc.world == null || mc.player == null) {
            if (!trackedProjectiles.isEmpty()) trackedProjectiles.clear();
            return;
        }

        Set<Integer> liveIds = new HashSet<>();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ProjectileEntity proj) || !mc.player.equals(proj.getOwner())) continue;

            liveIds.add(proj.getId());
            trackedProjectiles.put(proj.getId(), new double[]{proj.getX(), proj.getY(), proj.getZ()});
        }

        // Any tracked projectile no longer in the world just hit something.
        trackedProjectiles.keySet().removeIf(id -> {
            if (liveIds.contains(id)) return false;

            double[] pos = trackedProjectiles.get(id);
            recentHits.add(new HitRecord(pos[0], pos[1], pos[2], System.currentTimeMillis()));
            return true;
        });
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!isActive() || mc.world == null || mc.player == null) return;
        if (!(event.packet instanceof EntityTrackerUpdateS2CPacket packet)) return;

        if (!(mc.world.getEntityById(packet.id()) instanceof ArmorStandEntity armorStand) || !armorStand.isInvisible()) return;

        if (mode.get() == Mode.Solo) {
            if (mc.player.squaredDistanceTo(armorStand) > range.get() * range.get()) return;
        } else if (mode.get() == Mode.OnHit) {
            long now = System.currentTimeMillis();
            boolean matched = false;
            for (HitRecord h : recentHits) {
                if (h.isNear(armorStand.getX(), armorStand.getY(), armorStand.getZ(), now)) { matched = true; break; }
            }
            if (!matched) return;
        }
        // Team: no filter

        double vlMin = 0, vlMax = Double.MAX_VALUE;
        if (vlMode.get()) {
            double base = mc.player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            double effective = base * (1.0 + armorDamagePercent());
            vlMin = effective * 0.2;
            vlMax = effective * 4.0; // covers crits, enchants, RNG spikes
        }

        for (DataTracker.SerializedEntry<?> entry : packet.trackedValues()) {
            Float damage = tryExtractDamage(entry);
            if (damage == null) continue;
            if (vlMode.get() && (damage < vlMin || damage > vlMax)) continue;

            long now = System.currentTimeMillis();
            Long last = lastSeen.get(packet.id());
            if (last != null && now - last < DEDUP_WINDOW_MS) continue;

            lastSeen.put(packet.id(), now);
            events.addLast(new DamageEvent(now, damage));

            if (lastSeen.size() > 512) lastSeen.entrySet().removeIf(e -> now - e.getValue() > 10_000L);
        }
    }

    // Scans armor lore for ±X% damage modifiers (e.g. "+15% Damage", "-10% DMG"), summed across all
    // armor slots - for VL Mode's plausible-damage-range estimate.
    private double armorDamagePercent() {
        double total = 0;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;

            ItemStack armor = mc.player.getEquippedStack(slot);
            if (armor.isEmpty()) continue;

            LoreComponent lore = armor.get(DataComponentTypes.LORE);
            if (lore == null) continue;

            for (Text line : lore.lines()) {
                String text = line.getString().toLowerCase();
                if (!text.contains("damage") && !text.contains("dmg") && !text.contains("atk") && !text.contains("attack")) continue;

                Matcher m = PERCENT_PATTERN.matcher(text);
                if (m.find()) {
                    try { total += Double.parseDouble(m.group(1)) / 100.0; } catch (NumberFormatException ignored) {}
                }
            }
        }

        return total;
    }

    private static Float tryExtractDamage(DataTracker.SerializedEntry<?> entry) {
        if (!(entry.value() instanceof Optional<?> opt) || opt.isEmpty()) return null;
        if (!(opt.get() instanceof Text text)) return null;

        String stripped = text.getString().replaceAll("§[0-9a-fk-orA-FK-OR]", "");
        Matcher m = NUMBER_PATTERN.matcher(stripped);
        if (!m.find()) return null;

        try {
            float damage = Float.parseFloat(m.group());
            return damage > 0f ? damage : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private float getDps() {
        long now = System.currentTimeMillis();
        long windowMs = (long) (window.get() * 1000.0);

        while (!events.isEmpty() && now - events.peekFirst().time() > windowMs) events.pollFirst();

        float total = 0;
        for (DamageEvent e : events) total += e.damage();

        return (float) (total / window.get());
    }

    @Override
    public void render(HudRenderer renderer) {
        float dps = isInEditor() ? 12.3f : getDps();

        String label = "DPS: " + (dps >= 1000f ? String.format("%.2fk", dps / 1000f) : String.format("%.1f", dps));
        if (showMode.get()) label += switch (mode.get()) { case Solo -> " [S]"; case Team -> " [T]"; case OnHit -> " [H]"; };

        double textW = renderer.textWidth(label, shadow.get(), getScale());
        double textH = renderer.textHeight(shadow.get(), getScale());

        if (background.get()) renderer.quad(x - 2, y - 1, textW + 4, textH + 2, backgroundColor.get());

        Color color = dps > 0 ? activeColor.get() : inactiveColor.get();
        renderer.text(label, x, y, color, shadow.get(), getScale());

        setSize(textW, textH);
    }

    private double getScale() {
        return customScale.get() ? scale.get() : -1;
    }
}
