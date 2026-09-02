/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ButtonSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.packet.BrandCustomPayload;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class BossDebug extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> logParticles = sgGeneral.add(new BoolSetting.Builder()
        .name("log-particles")
        .description("Logs each particle packet and the nearest entity to it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("search-radius")
        .description("How far to search for an entity near each particle.")
        .defaultValue(8)
        .range(1, 32)
        .sliderMax(32)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Minimum time in milliseconds between logs of the same particle type.")
        .defaultValue(200)
        .range(0, 5000)
        .sliderMax(2000)
        .build()
    );

    private final Setting<Void> scanServer = sgGeneral.add(new ButtonSetting.Builder()
        .name("scan-server")
        .description("Prints server address, brand, tab list header/footer and active boss bars.")
        .buttonText("Scan Server")
        .action(this::printScan)
        .build()
    );

    private String serverBrand = "unknown";
    private Text tabHeader, tabFooter;
    private final Map<UUID, String> bossBars = new LinkedHashMap<>();
    private final Map<String, Long> lastLogged = new LinkedHashMap<>();

    private final BossBarS2CPacket.Consumer bossBarConsumer = new BossBarS2CPacket.Consumer() {
        @Override
        public void add(UUID uuid, Text name, float health, BossBar.Color color, BossBar.Style style, boolean darkenSky, boolean thickenFog, boolean music) {
            bossBars.put(uuid, name.getString());
        }

        @Override
        public void remove(UUID uuid) {
            bossBars.remove(uuid);
        }

        @Override
        public void updateName(UUID uuid, Text name) {
            bossBars.put(uuid, name.getString());
        }
    };

    public BossDebug() {
        super(Categories.Debug, "boss-debug", "Logs particles-to-entity associations and server info for reverse engineering boss fights.");

        hidden = true;
    }

    @Override
    public void onActivate() {
        serverBrand = "unknown";
        tabHeader = null;
        tabFooter = null;
        bossBars.clear();
        lastLogged.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof CustomPayloadS2CPacket packet && packet.payload() instanceof BrandCustomPayload brand) {
            serverBrand = brand.brand();
        }
        else if (event.packet instanceof PlayerListHeaderS2CPacket packet) {
            tabHeader = packet.header();
            tabFooter = packet.footer();
        }
        else if (event.packet instanceof BossBarS2CPacket packet) {
            packet.accept(bossBarConsumer);
        }
        else if (logParticles.get() && event.packet instanceof ParticleS2CPacket packet) {
            handleParticle(packet);
        }
    }

    private void handleParticle(ParticleS2CPacket packet) {
        if (mc.world == null) return;

        Identifier id = Registries.PARTICLE_TYPE.getId(packet.getParameters().getType());
        if (id == null) return;

        String key = id.toString();

        long now = System.currentTimeMillis();
        Long last = lastLogged.get(key);
        if (last != null && now - last < cooldown.get()) return;
        lastLogged.put(key, now);

        double x = packet.getX(), y = packet.getY(), z = packet.getZ();
        Entity nearest = findNearestEntity(x, y, z, radius.get());

        if (nearest != null) {
            double dist = nearest.getPos().distanceTo(new Vec3d(x, y, z));
            info("Particle §b%s§r at (%.1f, %.1f, %.1f) near §e%s§r (%.1f blocks)", key, x, y, z, describeEntity(nearest), dist);
        } else {
            info("Particle §b%s§r at (%.1f, %.1f, %.1f) - nothing within %.0f blocks", key, x, y, z, radius.get());
        }
    }

    private Entity findNearestEntity(double x, double y, double z, double maxDist) {
        Vec3d pos = new Vec3d(x, y, z);

        Entity nearest = null;
        double nearestDist = maxDist;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;

            double dist = entity.getPos().distanceTo(pos);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entity;
            }
        }

        return nearest;
    }

    private String describeEntity(Entity entity) {
        Identifier id = EntityType.getId(entity.getType());
        String base = id != null ? id.getPath() : entity.getType().toString();

        if (entity.hasCustomName() && entity.getCustomName() != null) base += " \"" + entity.getCustomName().getString() + "\"";

        return base;
    }

    private void printScan() {
        info("--- Server Scan ---");

        if (mc.getCurrentServerEntry() != null) info("Address: %s", mc.getCurrentServerEntry().address);
        info("Brand: %s", serverBrand);

        if (tabHeader != null && !tabHeader.getString().isEmpty()) info("Tab Header: %s", tabHeader.getString());
        if (tabFooter != null && !tabFooter.getString().isEmpty()) info("Tab Footer: %s", tabFooter.getString());

        if (bossBars.isEmpty()) {
            info("Boss Bars: none active");
        } else {
            for (String name : bossBars.values()) info("Boss Bar: %s", name);
        }
    }
}
