/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class NoRotate extends Module {
    private float lockedYaw, lockedPitch;

    public NoRotate() {
        super(Categories.Player, "no-rotate", "Locks your rotation client-side, regardless of what tries to change it.");
    }

    @Override
    public void onActivate() {
        lockedYaw = mc.player.getYaw();
        lockedPitch = mc.player.getPitch();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        mc.player.setYaw(lockedYaw);
        mc.player.setPitch(lockedPitch);
    }
}
