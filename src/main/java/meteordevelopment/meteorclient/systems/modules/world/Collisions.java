/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.shape.VoxelShapes;

import java.util.List;

public class Collisions extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("What blocks should be added collision box.")
        .defaultValue(
            Blocks.GLASS_PANE,
            Blocks.WHITE_STAINED_GLASS_PANE, Blocks.ORANGE_STAINED_GLASS_PANE, Blocks.MAGENTA_STAINED_GLASS_PANE, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE,
            Blocks.YELLOW_STAINED_GLASS_PANE, Blocks.LIME_STAINED_GLASS_PANE, Blocks.PINK_STAINED_GLASS_PANE, Blocks.GRAY_STAINED_GLASS_PANE,
            Blocks.LIGHT_GRAY_STAINED_GLASS_PANE, Blocks.CYAN_STAINED_GLASS_PANE, Blocks.PURPLE_STAINED_GLASS_PANE, Blocks.BLUE_STAINED_GLASS_PANE,
            Blocks.BROWN_STAINED_GLASS_PANE, Blocks.GREEN_STAINED_GLASS_PANE, Blocks.RED_STAINED_GLASS_PANE, Blocks.BLACK_STAINED_GLASS_PANE,
            Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL
        )
        .filter(this::blockFilter)
        .build()
    );

    private final Setting<Boolean> unloadedChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("unloaded-chunks")
        .description("Stops you from going into unloaded chunks.")
        .defaultValue(false)
        .build()
    );

    public Collisions() {
        super(Categories.World, "collisions", "Adds collision boxes to certain blocks/areas.");
    }

    @EventHandler
    private void onCollisionShape(CollisionShapeEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (!event.state.getFluidState().isEmpty()) return;
        if (blocks.get().contains(event.state.getBlock())) {
            event.shape = VoxelShapes.fullCube();
        }
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        int x = (int) (mc.player.getX() + event.movement.x) >> 4;
        int z = (int) (mc.player.getZ() + event.movement.z) >> 4;
        if (unloadedChunks.get() && !mc.world.getChunkManager().isChunkLoaded(x, z)) {
            ((IVec3d) event.movement).set(0, event.movement.y, 0);
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!unloadedChunks.get()) return;
        if (event.packet instanceof VehicleMoveC2SPacket packet) {
            if (!mc.world.getChunkManager().isChunkLoaded((int) packet.getX() >> 4, (int) packet.getZ() >> 4)) {
                mc.player.getVehicle().updatePosition(mc.player.getVehicle().prevX, mc.player.getVehicle().prevY, mc.player.getVehicle().prevZ);
                event.cancel();
            }
        } else if (event.packet instanceof PlayerMoveC2SPacket packet) {
            if (!mc.world.getChunkManager().isChunkLoaded((int) packet.getX(mc.player.getX()) >> 4, (int) packet.getZ(mc.player.getZ()) >> 4)) {
                event.cancel();
            }
        }
    }

    private boolean blockFilter(Block block) {
        return block instanceof PaneBlock || block instanceof AnvilBlock;
    }
}
