/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
    public static final HudElementInfo<DpsHud> INFO = new HudElementInfo<>(Hud.GROUP, "dps-meter", "Tracks damage from invisible armor stand damage indicators and displays DPS.", DpsHud::new);

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final long DEDUP_WINDOW_MS = 500L;

    public enum Mode {Solo, Team}

    private record DamageEvent(long time, float damage) {}

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBackground = settings.createGroup("Background");
    private final SettingGroup sgScale = settings.createGroup("Scale");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Solo only counts damage from armor stands near you, Team counts all of them.")
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

    private final Deque<DamageEvent> events = new ArrayDeque<>();
    private final Map<Integer, Long> lastSeen = new HashMap<>();

    public DpsHud() {
        super(INFO);

        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!isActive() || mc.world == null || mc.player == null) return;
        if (!(event.packet instanceof EntityTrackerUpdateS2CPacket packet)) return;

        if (!(mc.world.getEntityById(packet.id()) instanceof ArmorStandEntity armorStand) || !armorStand.isInvisible()) return;
        if (mode.get() == Mode.Solo && mc.player.squaredDistanceTo(armorStand) > range.get() * range.get()) return;

        for (DataTracker.SerializedEntry<?> entry : packet.trackedValues()) {
            Float damage = tryExtractDamage(entry);
            if (damage == null) continue;

            long now = System.currentTimeMillis();
            Long last = lastSeen.get(packet.id());
            if (last != null && now - last < DEDUP_WINDOW_MS) continue;

            lastSeen.put(packet.id(), now);
            events.addLast(new DamageEvent(now, damage));

            if (lastSeen.size() > 512) lastSeen.entrySet().removeIf(e -> now - e.getValue() > 10_000L);
        }
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
        if (showMode.get()) label += mode.get() == Mode.Solo ? " [S]" : " [T]";

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
