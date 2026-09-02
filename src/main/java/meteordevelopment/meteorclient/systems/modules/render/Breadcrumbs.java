/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.dimension.DimensionType;

import java.util.ArrayDeque;
import java.util.Queue;

public class Breadcrumbs extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("The color of the Breadcrumbs trail.")
        .defaultValue(new SettingColor(225, 25, 25))
        .build()
    );

    private final Setting<Integer> maxSections = sgGeneral.add(new IntSetting.Builder()
        .name("max-sections")
        .description("The maximum number of sections.")
        .defaultValue(1000)
        .min(1)
        .sliderRange(1, 5000)
        .build()
    );

    private final Setting<Double> sectionLength = sgGeneral.add(new DoubleSetting.Builder()
        .name("section-length")
        .description("The section length in blocks.")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(1)
        .build()
    );

    private final Setting<Double> lineWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("line-width")
        .description("Width of the trail in blocks. Rendered as flat ribbons rather than GL lines, since line width beyond 1px isn't reliably supported by graphics drivers.")
        .defaultValue(0.1)
        .min(0.02)
        .sliderRange(0.02, 1)
        .build()
    );

    private final Setting<Boolean> keepBetweenSessions = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-between-sessions")
        .description("Keeps the trail saved across disconnects, relogs and game restarts, instead of clearing it whenever the module deactivates - useful if you get kicked or disconnected and want to pick the trail back up.")
        .defaultValue(false)
        .build()
    );

    private final Pool<Section> sectionPool = new Pool<>(Section::new);
    private final Queue<Section> sections = new ArrayDeque<>();

    private Section section;

    private DimensionType lastDimension;

    public Breadcrumbs() {
        super(Categories.Render, "breadcrumbs", "Displays a trail behind where you have walked.");
    }

    @Override
    public void onActivate() {
        section = sectionPool.get();
        section.set1();

        lastDimension = mc.world.getDimension();
    }

    @Override
    public void onDeactivate() {
        if (keepBetweenSessions.get()) return;

        for (Section section : sections) sectionPool.free(section);
        sections.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (lastDimension != mc.world.getDimension()) {
            for (Section sec : sections) sectionPool.free(sec);
            sections.clear();
        }

        if (isFarEnough(section.x1, section.y1, section.z1)) {
            section.set2();

            if (sections.size() >= maxSections.get()) {
                Section section = sections.poll();
                if (section != null) sectionPool.free(section);
            }

            sections.add(section);
            section = sectionPool.get();
            section.set1();
        }

        lastDimension = mc.world.getDimension();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double halfWidth = lineWidth.get() / 2;

        for (Section section : sections) {
            renderSegment(event, section.x1, section.y1, section.z1, section.x2, section.y2, section.z2, halfWidth);
        }
    }

    private void renderSegment(Render3DEvent event, double x1, double y1, double z1, double x2, double y2, double z2, double halfWidth) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return;

        // Perpendicular to the segment, in the horizontal plane (cross product with world up).
        double nx = -dz, nz = dx;
        double nLen = Math.sqrt(nx * nx + nz * nz);

        if (nLen < 1e-6) {
            // Segment is (near) vertical - cross with a horizontal axis instead.
            nx = 1;
            nz = 0;
            nLen = 1;
        }

        nx = nx / nLen * halfWidth;
        nz = nz / nLen * halfWidth;

        event.renderer.quad(
            x1 + nx, y1, z1 + nz,
            x2 + nx, y2, z2 + nz,
            x2 - nx, y2, z2 - nz,
            x1 - nx, y1, z1 - nz,
            color.get()
        );
    }

    private boolean isFarEnough(double x, double y, double z) {
        return Math.abs(mc.player.getX() - x) >= sectionLength.get() || Math.abs(mc.player.getY() - y) >= sectionLength.get() || Math.abs(mc.player.getZ() - z) >= sectionLength.get();
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        if (tag == null) return null;

        if (keepBetweenSessions.get()) {
            NbtList sectionsTag = new NbtList();

            for (Section sec : sections) {
                NbtCompound secTag = new NbtCompound();
                secTag.putFloat("x1", sec.x1);
                secTag.putFloat("y1", sec.y1);
                secTag.putFloat("z1", sec.z1);
                secTag.putFloat("x2", sec.x2);
                secTag.putFloat("y2", sec.y2);
                secTag.putFloat("z2", sec.z2);
                sectionsTag.add(secTag);
            }

            tag.put("breadcrumbSections", sectionsTag);
        }

        return tag;
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        for (Section sec : sections) sectionPool.free(sec);
        sections.clear();

        if (tag.contains("breadcrumbSections")) {
            for (NbtElement e : tag.getList("breadcrumbSections", 10)) {
                NbtCompound secTag = (NbtCompound) e;

                Section sec = sectionPool.get();
                sec.x1 = secTag.getFloat("x1");
                sec.y1 = secTag.getFloat("y1");
                sec.z1 = secTag.getFloat("z1");
                sec.x2 = secTag.getFloat("x2");
                sec.y2 = secTag.getFloat("y2");
                sec.z2 = secTag.getFloat("z2");

                sections.add(sec);
            }
        }

        return super.fromTag(tag);
    }

    private class Section {
        public float x1, y1, z1;
        public float x2, y2, z2;

        public void set1() {
            x1 = (float) mc.player.getX();
            y1 = (float) mc.player.getY();
            z1 = (float) mc.player.getZ();
        }

        public void set2() {
            x2 = (float) mc.player.getX();
            y2 = (float) mc.player.getY();
            z2 = (float) mc.player.getZ();
        }
    }
}
