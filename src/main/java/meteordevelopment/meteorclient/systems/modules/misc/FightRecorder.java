/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.RenderBossBarEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Dev tool: records a boss fight (or anything else) to a file so it can be studied afterwards -
 * every particle packet (type, what block/item it's made of, position, count/speed/offsets),
 * entity spawns/removals, chat, titles/action bars, boss bars, sounds, explosions and block updates
 * (placed/broken/flowing - lava spreading/receding, fire spreading, etc.), plus a periodic snapshot
 * of the entities around you (names, positions, invisibility). One JSON object per line in
 * .minecraft/vlplus-recordings/recording-<time>.jsonl, timestamps in milliseconds from when
 * recording started. Turn the module on to start, off to stop - bind it to a key to do that
 * mid-fight.
 */
public class FightRecorder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> recordParticles = sgGeneral.add(new BoolSetting.Builder()
        .name("particles")
        .description("Records every particle packet.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> recordSnapshots = sgGeneral.add(new BoolSetting.Builder()
        .name("entity-snapshots")
        .description("Periodically records the entities around you (type, name, position, invisibility).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> snapshotTicks = sgGeneral.add(new IntSetting.Builder()
        .name("snapshot-interval")
        .description("Ticks between entity snapshots.")
        .defaultValue(10)
        .range(1, 100)
        .sliderRange(2, 40)
        .visible(recordSnapshots::get)
        .build()
    );

    private final Setting<Double> snapshotRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("snapshot-radius")
        .description("How far around you entities are included in snapshots.")
        .defaultValue(60)
        .min(5)
        .sliderRange(10, 150)
        .visible(recordSnapshots::get)
        .build()
    );

    private final Setting<Boolean> recordBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("block-updates")
        .description("Records every block change (a block placed/broken/flowing - lava spreading or receding, fire spreading, etc.) with its position and new block id. Nothing scans proactively - this is purely event-driven off the same block-update packets the client already gets, so it's cheap even at Everything. Meant for studying floor hazards (lava/fire) after the fact the same way particle recordings already get studied.")
        .defaultValue(true)
        .build()
    );

    private final Setting<java.util.List<String>> blockFilter = sgGeneral.add(new meteordevelopment.meteorclient.settings.StringListSetting.Builder()
        .name("block-filter")
        .description("Only records updates for these block ids (one per line, e.g. minecraft:lava). Empty = record every block change.")
        .defaultValue(new java.util.ArrayList<>())
        .visible(recordBlocks::get)
        .build()
    );

    private final Setting<Integer> maxMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("max-minutes")
        .description("Stops the recording by itself after this many minutes, so a forgotten recording can't fill the disk.")
        .defaultValue(20)
        .range(1, 120)
        .sliderRange(1, 60)
        .build()
    );

    private BufferedWriter out;
    private long startMs;
    private int tickCounter;
    private Path file;

    // Last (name + color + style) string recorded per boss bar uuid - RenderBossBarEvent.BossText
    // fires every frame for every currently-shown boss bar, so this is needed to only write a
    // "bossbar" line when something about it actually changed, instead of once per frame per bar.
    private final java.util.Map<java.util.UUID, String> lastBossBarState = new java.util.HashMap<>();

    public FightRecorder() {
        super(Categories.Debug, "fight-recorder", "Records particles, entities, chat, sounds and more during a boss fight to a file for later analysis.", "recorder", "boss-recorder", "logger");
        hidden = true;
    }

    @Override
    public void onActivate() {
        try {
            Path dir = mc.runDirectory.toPath().resolve("vlplus-recordings");
            Files.createDirectories(dir);
            file = dir.resolve("recording-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".jsonl");
            out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            startMs = System.currentTimeMillis();
            tickCounter = 0;
            lastBossBarState.clear();
            info("Recording to %s", file.getFileName());
        } catch (IOException e) {
            error("Couldn't start recording: %s", e.getMessage());
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        synchronized (this) {
            if (out != null) {
                try { out.close(); } catch (IOException ignored) {}
                out = null;
            }
        }

        if (file != null) info("Saved recording: %s", file.toAbsolutePath());
    }

    private static String q(String s) {
        if (s == null) return "null";

        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }

    private synchronized void write(String type, String fields) {
        if (out == null) return;

        try {
            out.write("{\"t\":" + (System.currentTimeMillis() - startMs) + ",\"type\":" + q(type) + (fields.isEmpty() ? "" : "," + fields) + "}\n");
        } catch (IOException e) {
            try { out.close(); } catch (IOException ignored) {}
            out = null;
        }
    }

    private static String pos(double x, double y, double z) {
        return String.format(java.util.Locale.ROOT, "\"x\":%.2f,\"y\":%.2f,\"z\":%.2f", x, y, z);
    }

    private void writeBlock(BlockPos pos, BlockState state) {
        if (!recordBlocks.get()) return;

        String id = Registries.BLOCK.getId(state.getBlock()).toString();

        java.util.List<String> filter = blockFilter.get();
        if (!filter.isEmpty()) {
            boolean match = false;
            for (String entry : filter) if (entry.trim().equalsIgnoreCase(id)) { match = true; break; }
            if (!match) return;
        }

        write("block", "\"x\":" + pos.getX() + ",\"y\":" + pos.getY() + ",\"z\":" + pos.getZ() + ",\"id\":" + q(id));
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (out == null) return;

        Object p = event.packet;

        if (p instanceof ParticleS2CPacket packet) {
            if (!recordParticles.get()) return;

            Identifier id = Registries.PARTICLE_TYPE.getId(packet.getParameters().getType());
            String detail = "";
            if (packet.getParameters() instanceof BlockStateParticleEffect bs) detail = Registries.BLOCK.getId(bs.getBlockState().getBlock()).toString();
            else if (packet.getParameters() instanceof ItemStackParticleEffect is) detail = Registries.ITEM.getId(is.getItemStack().getItem()).toString();

            write("particle", "\"id\":" + q(String.valueOf(id)) + ",\"of\":" + q(detail) + "," + pos(packet.getX(), packet.getY(), packet.getZ())
                + String.format(java.util.Locale.ROOT, ",\"count\":%d,\"speed\":%.3f,\"off\":[%.3f,%.3f,%.3f],\"long\":%b", packet.getCount(), packet.getSpeed(), packet.getOffsetX(), packet.getOffsetY(), packet.getOffsetZ(), packet.isLongDistance()));
        }
        else if (p instanceof EntitySpawnS2CPacket packet) {
            write("spawn", "\"eid\":" + packet.getEntityId() + ",\"entity\":" + q(Registries.ENTITY_TYPE.getId(packet.getEntityType()).toString()) + "," + pos(packet.getX(), packet.getY(), packet.getZ()));
        }
        else if (p instanceof EntitiesDestroyS2CPacket packet) {
            write("despawn", "\"eids\":" + packet.getEntityIds());
        }
        else if (p instanceof TitleS2CPacket packet) {
            write("title", "\"text\":" + q(packet.text().getString()));
        }
        else if (p instanceof SubtitleS2CPacket packet) {
            write("subtitle", "\"text\":" + q(packet.text().getString()));
        }
        else if (p instanceof OverlayMessageS2CPacket packet) {
            write("actionbar", "\"text\":" + q(packet.text().getString()));
        }
        else if (p instanceof BossBarS2CPacket) {
            // No per-packet detail here - RenderBossBarEvent.BossText (onBossBar below) already
            // captures name/color/style/percent straight off the real ClientBossBar object, change-
            // only, which is both easier to read here and more reliable than decoding the packet's
            // own add/update/remove action variants by hand.
        }
        else if (p instanceof PlaySoundS2CPacket packet) {
            write("sound", "\"id\":" + q(packet.getSound().getKey().map(k -> k.getValue().toString()).orElseGet(() -> packet.getSound().value().getId().toString())) + "," + pos(packet.getX(), packet.getY(), packet.getZ())
                + String.format(java.util.Locale.ROOT, ",\"vol\":%.2f,\"pitch\":%.2f", packet.getVolume(), packet.getPitch()));
        }
        else if (p instanceof ExplosionS2CPacket packet) {
            write("explosion", pos(packet.getX(), packet.getY(), packet.getZ()));
        }
        else if (p instanceof BlockUpdateS2CPacket packet) {
            writeBlock(packet.getPos(), packet.getState());
        }
        else if (p instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates(this::writeBlock);
        }
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        if (out == null) return;
        write("chat", "\"text\":" + q(event.getMessage().getString()));
    }

    @EventHandler
    private void onBossBar(RenderBossBarEvent.BossText event) {
        if (out == null) return;

        java.util.UUID id = event.bossBar.getUuid();
        String name = event.name.getString();
        String color = event.bossBar.getColor().name();
        float percent = event.bossBar.getPercent();

        String state = name + "|" + color;
        if (state.equals(lastBossBarState.get(id))) return;
        lastBossBarState.put(id, state);

        write("bossbar", "\"name\":" + q(name) + ",\"color\":" + q(color) + String.format(java.util.Locale.ROOT, ",\"percent\":%.3f", percent));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (out == null || mc.world == null || mc.player == null) return;

        if (System.currentTimeMillis() - startMs > maxMinutes.get() * 60_000L) {
            info("Max recording length reached.");
            toggle();
            return;
        }

        if (!recordSnapshots.get() || ++tickCounter < snapshotTicks.get()) return;
        tickCounter = 0;

        double radiusSq = snapshotRadius.get() * snapshotRadius.get();
        StringBuilder sb = new StringBuilder("\"me\":{" + pos(mc.player.getX(), mc.player.getY(), mc.player.getZ()) + "},\"entities\":[");
        boolean first = true;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || entity.squaredDistanceTo(mc.player) > radiusSq) continue;
            if (!(entity instanceof LivingEntity) && !(entity instanceof ArmorStandEntity) && !entity.hasCustomName()) continue;

            if (!first) sb.append(',');
            first = false;

            sb.append("{\"eid\":").append(entity.getId())
                .append(",\"type\":").append(q(Registries.ENTITY_TYPE.getId(entity.getType()).toString()))
                .append(",\"name\":").append(q(entity.getName().getString()))
                .append(",").append(pos(entity.getX(), entity.getY(), entity.getZ()))
                .append(",\"invisible\":").append(entity.isInvisible())
                .append(",\"player\":").append(entity instanceof PlayerEntity)
                .append('}');
        }

        sb.append(']');
        write("snapshot", sb.toString());

        try { out.flush(); } catch (IOException ignored) {}
    }
}
