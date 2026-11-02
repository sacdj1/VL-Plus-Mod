/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Marks hostile mobs that are actually aggro'd on something, above their head - a symbol (just "it's
 * aggro"), the target's name, or both. vanilla only syncs a mob's real target to the client for a few
 * entity types (guardians, and a handful of others), so most of the time this falls back to a heuristic:
 * a living entity nearby that the mob's body is currently turned towards, within Heuristic Look Angle -
 * good enough to catch "this thing is facing/chasing someone", not a guaranteed read of its real AI state.
 */
public class Awareness extends Module {
    public enum Mode {
        Symbol,
        Name,
        Both
    }

    public enum ScaleMode {
        Constant,
        Distance
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDisplay = settings.createGroup("Display");
    private final SettingGroup sgScale = settings.createGroup("Scaling");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Only checks mobs within this many blocks of you.")
        .defaultValue(40)
        .min(1)
        .sliderRange(5, 100)
        .build()
    );

    private final Setting<Boolean> hostileOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("hostile-only")
        .description("Only checks hostile mobs. Off: any non-player mob.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> whitelist = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("whitelist")
        .description("Only checks these entity types. Leave empty to check every qualifying mob.")
        .onlyAttackable()
        .build()
    );

    private final Setting<List<String>> nameWhitelist = sgGeneral.add(new StringListSetting.Builder()
        .name("name-whitelist")
        .description("Only checks mobs whose name (case-insensitive, anywhere in it) contains one of these - one per line. Leave empty to not filter by name. For picking out one specific boss.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<Double> heuristicAngle = sgGeneral.add(new DoubleSetting.Builder()
        .name("heuristic-look-angle")
        .description("For mobs that don't sync a real target to the client (most of them): how far off, in degrees, the mob's body can be from pointing straight at a candidate before it stops counting as \"aggro'd on them\".")
        .defaultValue(35)
        .min(1)
        .sliderRange(5, 90)
        .build()
    );

    private final Setting<Double> heuristicRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("heuristic-candidate-range")
        .description("For the heuristic: how far from the mob a player/entity can be and still be considered as a possible target.")
        .defaultValue(12)
        .min(1)
        .sliderRange(3, 40)
        .build()
    );

    private final Setting<Mode> mode = sgDisplay.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Symbol: just marks that it's aggro'd on something. Name: shows who. Both: symbol above the name.")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<String> symbol = sgDisplay.add(new StringSetting.Builder()
        .name("symbol")
        .description("The symbol shown above an aggro'd mob.")
        .defaultValue("!")
        .visible(() -> mode.get() != Mode.Name)
        .build()
    );

    private final Setting<SettingColor> symbolColor = sgDisplay.add(new ColorSetting.Builder()
        .name("symbol-color")
        .description("Color of the symbol.")
        .defaultValue(new SettingColor(255, 60, 60))
        .visible(() -> mode.get() != Mode.Name)
        .build()
    );

    private final Setting<SettingColor> nameColor = sgDisplay.add(new ColorSetting.Builder()
        .name("name-color")
        .description("Color of the target's name.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> mode.get() != Mode.Symbol)
        .build()
    );

    private final Setting<Boolean> youColor = sgDisplay.add(new BoolSetting.Builder()
        .name("highlight-you")
        .description("Uses a separate color (below) when the mob is aggro'd on you specifically.")
        .defaultValue(true)
        .visible(() -> mode.get() != Mode.Symbol)
        .build()
    );

    private final Setting<SettingColor> youNameColor = sgDisplay.add(new ColorSetting.Builder()
        .name("you-color")
        .description("Name color when it's aggro'd on you.")
        .defaultValue(new SettingColor(255, 80, 80))
        .visible(() -> mode.get() != Mode.Symbol && youColor.get())
        .build()
    );

    private final Setting<Double> yOffset = sgDisplay.add(new DoubleSetting.Builder()
        .name("y-offset")
        .description("Extra height above the mob's own height the marker sits at.")
        .defaultValue(0.5)
        .sliderRange(-1, 3)
        .build()
    );

    private final Setting<ScaleMode> scaleMode = sgScale.add(new EnumSetting.Builder<ScaleMode>()
        .name("scale-mode")
        .description("Constant: always the same size on screen, however far away. Distance: scales between a near and far size.")
        .defaultValue(ScaleMode.Distance)
        .build()
    );

    private final Setting<Double> constantScale = sgScale.add(new DoubleSetting.Builder()
        .name("constant-scale")
        .defaultValue(1.5)
        .min(0.1)
        .sliderRange(0.2, 5)
        .visible(() -> scaleMode.get() == ScaleMode.Constant)
        .build()
    );

    private final Setting<Double> nearDistance = sgScale.add(new DoubleSetting.Builder()
        .name("near-distance")
        .description("At this distance (or closer), the marker is Near Scale.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 30)
        .visible(() -> scaleMode.get() == ScaleMode.Distance)
        .build()
    );

    private final Setting<Double> nearScale = sgScale.add(new DoubleSetting.Builder()
        .name("near-scale")
        .defaultValue(2.2)
        .min(0.1)
        .sliderRange(0.2, 6)
        .visible(() -> scaleMode.get() == ScaleMode.Distance)
        .build()
    );

    private final Setting<Double> farDistance = sgScale.add(new DoubleSetting.Builder()
        .name("far-distance")
        .description("At this distance (or further), the marker is Far Scale.")
        .defaultValue(40)
        .min(1)
        .sliderRange(5, 100)
        .visible(() -> scaleMode.get() == ScaleMode.Distance)
        .build()
    );

    private final Setting<Double> farScale = sgScale.add(new DoubleSetting.Builder()
        .name("far-scale")
        .defaultValue(0.6)
        .min(0.05)
        .sliderRange(0.1, 3)
        .visible(() -> scaleMode.get() == ScaleMode.Distance)
        .build()
    );

    public Awareness() {
        super(Categories.Render, "awareness", "Marks hostile mobs that are actually aggro'd on something - a symbol, the target's name, or both.", "aggro-indicator", "mob-target");
    }

    private boolean qualifies(Entity entity) {
        if (!(entity instanceof MobEntity mob) || entity instanceof PlayerEntity) return false;
        if (hostileOnly.get() && !(entity instanceof HostileEntity)) return false;
        if (!whitelist.get().isEmpty() && !whitelist.get().contains(mob.getType())) return false;

        List<String> names = nameWhitelist.get();
        if (names.isEmpty()) return true;

        String entityName = mob.getName().getString().toLowerCase(Locale.ROOT);
        for (String name : names) {
            String needle = name.trim().toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && entityName.contains(needle)) return true;
        }

        return false;
    }

    /** The mob's real synced target if vanilla happens to sync one for this entity type, otherwise a look-direction guess. */
    private LivingEntity findTarget(MobEntity mob) {
        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive()) return target;

        double rangeSq = heuristicRange.get() * heuristicRange.get();
        double maxAngle = heuristicAngle.get();
        float bodyYaw = mob.getBodyYaw();

        LivingEntity best = null;
        double bestAngle = maxAngle;

        for (Entity candidate : mc.world.getEntities()) {
            if (!(candidate instanceof LivingEntity living) || candidate == mob || !living.isAlive()) continue;
            if (mob.squaredDistanceTo(living) > rangeSq) continue;

            double dx = living.getX() - mob.getX(), dz = living.getZ() - mob.getZ();
            if (dx * dx + dz * dz < 0.01) continue;

            double toward = Math.toDegrees(Math.atan2(-dx, dz));
            double diff = MathHelper.wrapDegrees(toward - bodyYaw);
            double angle = Math.abs(diff);

            if (angle < bestAngle) { bestAngle = angle; best = living; }
        }

        return best;
    }

    private double scaleFor(double dist) {
        if (scaleMode.get() == ScaleMode.Constant) return constantScale.get();

        double near = nearDistance.get(), far = Math.max(nearDistance.get() + 0.01, farDistance.get());
        double t = MathHelper.clamp((dist - near) / (far - near), 0, 1);
        return nearScale.get() + (farScale.get() - nearScale.get()) * t;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;

        TextRenderer text = TextRenderer.get();
        double rangeSq = range.get() * range.get();

        for (Entity entity : mc.world.getEntities()) {
            if (!qualifies(entity)) continue;
            if (mc.player.squaredDistanceTo(entity) > rangeSq) continue;

            MobEntity mob = (MobEntity) entity;
            LivingEntity target = findTarget(mob);
            if (target == null) continue;

            double dist = PlayerUtils.distanceToCamera(entity.getX(), entity.getY(), entity.getZ());
            Vector3d pos = new Vector3d(entity.getX(), entity.getY() + entity.getHeight() + yOffset.get(), entity.getZ());
            if (!NametagUtils.to2D(pos, scaleFor(dist), false)) continue;

            boolean you = target == mc.player;
            String name = you ? "You" : target.getName().getString();

            NametagUtils.begin(pos);
            text.begin();

            double y = 0;

            if (mode.get() != Mode.Name) {
                String s = symbol.get();
                text.render(s, -text.getWidth(s) / 2, y, symbolColor.get(), true);
                y += text.getHeight();
            }

            if (mode.get() != Mode.Symbol) {
                Color color = you && youColor.get() ? youNameColor.get() : nameColor.get();
                text.render(name, -text.getWidth(name) / 2, y, color, true);
            }

            text.end();
            NametagUtils.end();
        }
    }
}
