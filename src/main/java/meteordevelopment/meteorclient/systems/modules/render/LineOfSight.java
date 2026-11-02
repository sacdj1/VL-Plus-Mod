/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Optional;

/**
 * Draws an actual 3D line from your own eyes out to wherever your line of sight currently ends -
 * an entity's hitbox, terrain, or just open air at Sight Line Range - plus an optional small
 * wireframe "crosshair" marker at that exact point. A pure aiming aid, not tied to reach, weapons,
 * or hit cooldown the way Target Indicator's own symbol is.
 */
public class LineOfSight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Color of the line.")
        .defaultValue(new SettingColor(255, 255, 255, 120))
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far the line extends into open air if it hits nothing at all.")
        .defaultValue(32.0)
        .min(1)
        .sliderRange(1, 128)
        .build()
    );

    private final Setting<Boolean> showHitMarker = sgGeneral.add(new BoolSetting.Builder()
        .name("show-hit-marker")
        .description("Draws a small wireframe box (a \"crosshair\" marker) at the exact point the line above currently ends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> hitMarkerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("hit-marker-color")
        .description("Color of the hit marker.")
        .defaultValue(new SettingColor(255, 40, 40))
        .visible(showHitMarker::get)
        .build()
    );

    private final Setting<Double> hitMarkerSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("hit-marker-size")
        .description("Size of the hit marker, in blocks.")
        .defaultValue(0.1)
        .min(0.02)
        .sliderRange(0.02, 1)
        .visible(showHitMarker::get)
        .build()
    );

    public LineOfSight() {
        super(Categories.Camera, "line-of-sight", "Draws a line from your eyes to wherever you're looking, plus a marker at the exact point it hits.", "los", "crosshair-line", "aim-line");
    }

    /** Whichever's closer: an entity's hitbox or terrain - end-of-range in open air otherwise. */
    private Vec3d findSightEndpoint(Vec3d start, Vec3d end, double maxDistance) {
        double closestDistSq = maxDistance * maxDistance;
        Vec3d closest = end;

        HitResult blockHit = mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            double distSq = start.squaredDistanceTo(blockHit.getPos());
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = blockHit.getPos();
            }
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entity.isAlive()) continue;

            Optional<Vec3d> hit = entity.getBoundingBox().expand(0.1).raycast(start, end);
            if (hit.isEmpty()) continue;

            double distSq = start.squaredDistanceTo(hit.get());
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = hit.get();
            }
        }

        return closest;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Vec3d start = mc.player.getCameraPosVec(event.tickDelta);
        Vec3d look = mc.player.getRotationVec(event.tickDelta);
        Vec3d end = start.add(look.multiply(range.get()));

        Vec3d hitPoint = findSightEndpoint(start, end, range.get());

        event.renderer.line(start.x, start.y, start.z, hitPoint.x, hitPoint.y, hitPoint.z, lineColor.get());

        if (showHitMarker.get()) {
            double s = hitMarkerSize.get();
            event.renderer.boxLines(hitPoint.x - s, hitPoint.y - s, hitPoint.z - s, hitPoint.x + s, hitPoint.y + s, hitPoint.z + s, hitMarkerColor.get(), 0);
        }
    }
}
