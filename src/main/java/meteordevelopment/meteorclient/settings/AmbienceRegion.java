/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A manually-placed area override for Ambience - takes priority over per-biome colors, so you can
 * recolor a specific spot (or a whole Y range, e.g. to make a cave read differently than the
 * biome on the surface above it) regardless of what biome it's actually in.
 *
 * Two modes: a box (two corners, optionally Y-range-only), or a point + radius (sphere/ellipsoid
 * or cylinder - see PointShape) that also only matches the same biome the point was in when placed
 * (so it follows that biome's own shape near the point instead of a flat box/sphere/cylinder).
 */
public class AmbienceRegion {
    public String name = "Region";
    public String dimension = ""; // empty = any dimension
    public boolean enabled = true;
    // Separate from enabled - lets Ambience's Visualize Regions option show only a chosen subset
    // of your regions (e.g. just the one you're currently placing) without disabling the rest.
    public boolean visualize = true;
    public final Map<Block, List<WeightedColorEntry>> blocks = new LinkedHashMap<>();
    // Blocks temporarily toggled off without losing their configured colors.
    public final Set<Block> disabledBlocks = new LinkedHashSet<>();

    // Box mode (pointMode = false)
    public int x1, y1, z1, x2, y2, z2;
    public boolean yOnly; // ignore x/z, only the y1..y2 range matters

    // Point mode (pointMode = true) - an area around the point: radiusX = width (east/west),
    // radiusY = height (up/down), radiusZ = length (north/south). Two shapes share these same
    // 3 radii, just combined differently - see pointShape.
    public boolean pointMode;
    public PointShape pointShape = PointShape.Sphere;
    public int px, py, pz;
    public int radiusX = 32, radiusY = 32, radiusZ = 32;
    public String biome = ""; // biome id at the point when it was placed - "" matches any biome

    // Cylinder only - how its vertical extent is defined. HalfHeight (default) stays centered on
    // the point and grows symmetrically via radiusY, same as every other radius here. Coords
    // instead takes two explicit Y levels directly - useful when you already know exactly which
    // height range you want (e.g. "Y 60 to Y 80") rather than working out a center + half-height
    // that lands on it.
    public CylinderHeightMode cylinderHeightMode = CylinderHeightMode.HalfHeight;
    public int cylinderMinY, cylinderMaxY;

    public enum CylinderHeightMode {
        HalfHeight,
        Coords
    }

    public enum PointShape {
        /** Equal radii give a sphere; unequal give an ellipsoid - all 3 axes fall off together. */
        Sphere,
        /** radiusX/radiusZ form a (possibly elliptical) footprint in the X/Z plane; radiusY is a flat half-height, not a falloff - a straight-sided column instead of curving inward toward the top/bottom. */
        Cylinder
    }

    /** Cylinder + Coords: its real min Y, taken directly from cylinderMinY/MaxY. Everything else: py - radiusY, same half-height math as every other axis. Lets callers that need this region's actual vertical extent (the visualizer's own wireframe/scans included) stay correct regardless of which height mode a cylinder is using, instead of each one re-deriving this same branch itself. */
    public int effectiveMinY() {
        if (pointMode && pointShape == PointShape.Cylinder && cylinderHeightMode == CylinderHeightMode.Coords) {
            return Math.min(cylinderMinY, cylinderMaxY);
        }
        return py - radiusY;
    }

    /** See effectiveMinY(). */
    public int effectiveMaxY() {
        if (pointMode && pointShape == PointShape.Cylinder && cylinderHeightMode == CylinderHeightMode.Coords) {
            return Math.max(cylinderMinY, cylinderMaxY);
        }
        return py + radiusY;
    }

    public boolean contains(String dimensionId, BlockPos pos, String biomeIdAtPos) {
        if (!enabled) return false;
        if (!dimension.isEmpty() && !dimension.equals(dimensionId)) return false;

        if (pointMode) {
            double dx = (pos.getX() - px) / (double) Math.max(radiusX, 1);
            double dz = (pos.getZ() - pz) / (double) Math.max(radiusZ, 1);

            if (pointShape == PointShape.Cylinder) {
                if (dx * dx + dz * dz > 1.0) return false;

                if (cylinderHeightMode == CylinderHeightMode.Coords) {
                    int minY = Math.min(cylinderMinY, cylinderMaxY), maxY = Math.max(cylinderMinY, cylinderMaxY);
                    if (pos.getY() < minY || pos.getY() > maxY) return false;
                } else if (Math.abs(pos.getY() - py) > Math.max(radiusY, 1)) {
                    return false;
                }
            } else {
                double dy = (pos.getY() - py) / (double) Math.max(radiusY, 1);
                if (dx * dx + dy * dy + dz * dz > 1.0) return false;
            }

            return biome.isEmpty() || biome.equals(biomeIdAtPos);
        }

        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        if (pos.getY() < minY || pos.getY() > maxY) return false;
        if (yOnly) return true;

        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("name", name);
        tag.putString("dimension", dimension);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("visualize", visualize);

        tag.putInt("x1", x1);
        tag.putInt("y1", y1);
        tag.putInt("z1", z1);
        tag.putInt("x2", x2);
        tag.putInt("y2", y2);
        tag.putInt("z2", z2);
        tag.putBoolean("yOnly", yOnly);

        tag.putBoolean("pointMode", pointMode);
        tag.putString("pointShape", pointShape.name());
        tag.putInt("px", px);
        tag.putInt("py", py);
        tag.putInt("pz", pz);
        tag.putInt("radiusX", radiusX);
        tag.putInt("radiusY", radiusY);
        tag.putInt("radiusZ", radiusZ);
        tag.putString("biome", biome);
        tag.putString("cylinderHeightMode", cylinderHeightMode.name());
        tag.putInt("cylinderMinY", cylinderMinY);
        tag.putInt("cylinderMaxY", cylinderMaxY);

        NbtCompound blocksTag = new NbtCompound();
        for (Map.Entry<Block, List<WeightedColorEntry>> entry : blocks.entrySet()) {
            Identifier blockId = Registries.BLOCK.getId(entry.getKey());

            NbtList colorsTag = new NbtList();
            for (WeightedColorEntry color : entry.getValue()) colorsTag.add(color.toTag());

            blocksTag.put(blockId.toString(), colorsTag);
        }
        tag.put("blocks", blocksTag);

        NbtList disabledTag = new NbtList();
        for (Block block : disabledBlocks) disabledTag.add(NbtString.of(Registries.BLOCK.getId(block).toString()));
        tag.put("disabledBlocks", disabledTag);

        return tag;
    }

    public static AmbienceRegion fromTag(NbtCompound tag) {
        AmbienceRegion region = new AmbienceRegion();

        region.name = tag.getString("name");
        region.dimension = tag.getString("dimension");
        region.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        region.visualize = !tag.contains("visualize") || tag.getBoolean("visualize");

        region.x1 = tag.getInt("x1");
        region.y1 = tag.getInt("y1");
        region.z1 = tag.getInt("z1");
        region.x2 = tag.getInt("x2");
        region.y2 = tag.getInt("y2");
        region.z2 = tag.getInt("z2");
        region.yOnly = tag.getBoolean("yOnly");

        region.pointMode = tag.getBoolean("pointMode");
        try {
            region.pointShape = PointShape.valueOf(tag.getString("pointShape"));
        } catch (IllegalArgumentException e) {
            region.pointShape = PointShape.Sphere;
        }
        region.px = tag.getInt("px");
        region.py = tag.getInt("py");
        region.pz = tag.getInt("pz");
        int legacyRadius = tag.contains("radius") ? tag.getInt("radius") : 32;
        region.radiusX = tag.contains("radiusX") ? tag.getInt("radiusX") : legacyRadius;
        region.radiusY = tag.contains("radiusY") ? tag.getInt("radiusY") : legacyRadius;
        region.radiusZ = tag.contains("radiusZ") ? tag.getInt("radiusZ") : legacyRadius;
        region.biome = tag.getString("biome");
        try {
            region.cylinderHeightMode = CylinderHeightMode.valueOf(tag.getString("cylinderHeightMode"));
        } catch (IllegalArgumentException e) {
            region.cylinderHeightMode = CylinderHeightMode.HalfHeight;
        }
        region.cylinderMinY = tag.getInt("cylinderMinY");
        region.cylinderMaxY = tag.getInt("cylinderMaxY");

        NbtCompound blocksTag = tag.getCompound("blocks");
        for (String blockIdStr : blocksTag.getKeys()) {
            Block block = Registries.BLOCK.get(Identifier.of(blockIdStr));
            if (block == null) continue;

            List<WeightedColorEntry> colors = new ArrayList<>();
            for (NbtElement e : blocksTag.getList(blockIdStr, NbtElement.COMPOUND_TYPE)) {
                colors.add(WeightedColorEntry.fromTag((NbtCompound) e));
            }

            region.blocks.put(block, colors);
        }

        for (NbtElement e : tag.getList("disabledBlocks", NbtElement.STRING_TYPE)) {
            Block block = Registries.BLOCK.get(Identifier.of(e.asString()));
            if (block != null) region.disabledBlocks.add(block);
        }

        return region;
    }
}
