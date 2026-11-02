/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.AmbienceRegion;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.function.Consumer;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class RegionEditScreen extends WindowScreen {
    private final AmbienceRegion region;

    public RegionEditScreen(GuiTheme theme, AmbienceRegion region) {
        super(theme, "Edit Region");

        this.region = region;
    }

    @Override
    public void initWidgets() {
        WTable table = add(theme.table()).expandX().widget();

        table.add(theme.label("Name"));
        WTextBox name = table.add(theme.textBox(region.name)).expandX().widget();
        name.action = () -> region.name = name.get();
        table.row();

        table.add(theme.label("Dimension: " + dimensionDisplayName(region.dimension)));

        WHorizontalList dimRow = table.add(theme.horizontalList()).expandCellX().widget();

        dimRow.add(theme.button("Overworld")).right().widget().action = () -> setDimension(World.OVERWORLD.getValue().toString());
        dimRow.add(theme.button("Nether")).right().widget().action = () -> setDimension(World.NETHER.getValue().toString());
        dimRow.add(theme.button("End")).right().widget().action = () -> setDimension(World.END.getValue().toString());
        dimRow.add(theme.button("My Current")).right().widget().action = () ->
            setDimension(mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "");
        dimRow.add(theme.button("Any")).right().widget().action = () -> setDimension("");
        table.row();

        WHorizontalList modeRow = table.add(theme.horizontalList()).expandCellX().widget();
        modeRow.add(theme.label("Point Mode (a biome-shaped area around one point, instead of a box)"));
        var pointMode = modeRow.add(theme.checkbox(region.pointMode)).widget();
        pointMode.action = () -> {
            region.pointMode = pointMode.checked;

            // Switching modes alone never touched px/py/pz - a region whose point was never snapped
            // (still at the Java default 0,0,0) silently matched nothing anywhere near a normal
            // world position once Point Mode was turned on, with no indication why. Snap it to
            // the player now instead, the same one-time convenience "Add Region At My Position"
            // already gives box mode's corners.
            if (region.pointMode && region.px == 0 && region.py == 0 && region.pz == 0 && mc.player != null) {
                region.px = mc.player.getBlockX();
                region.py = mc.player.getBlockY();
                region.pz = mc.player.getBlockZ();
            }

            clear();
            initWidgets();
        };
        table.row();

        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        if (region.pointMode) {
            initPointMode(table);
        } else {
            initBoxMode(table);
        }

        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.button("Colors (" + region.blocks.size() + " blocks)")).expandCellX().widget().action = () -> {
            // Adding/editing a block here only ever mutates region.blocks itself - the "regions"
            // Setting object's own reference never changes, so its onChanged(val -> reload()) never
            // fires on its own. Without forcing a reload here too, a newly-added block (especially
            // one that wasn't recolorable anywhere before, so Sodium's own color-provider wrapper
            // only exists once Ambience says this block is "configured anywhere") silently never
            // got the chunk re-mesh it needed to actually show up - same reload() every other
            // override setting already gets automatically.
            BlockColorMapScreen screen = new BlockColorMapScreen(theme, region.name, "Region", region.blocks, () -> Modules.get().get(Ambience.class).reload(),
                block -> !region.disabledBlocks.contains(block),
                (block, en) -> {
                    if (en) region.disabledBlocks.remove(block);
                    else region.disabledBlocks.add(block);
                });
            mc.setScreen(screen);
        };
    }

    private void initBoxMode(WTable table) {
        WHorizontalList yOnlyRow = table.add(theme.horizontalList()).expandCellX().widget();
        yOnlyRow.add(theme.label("Y Range Only (ignore X/Z, e.g. cave vs surface)"));
        var yOnly = yOnlyRow.add(theme.checkbox(region.yOnly)).widget();
        yOnly.action = () -> region.yOnly = yOnly.checked;
        table.row();

        table.add(theme.button("Snap Both Corners To My Position")).expandCellX().widget().action = () -> {
            if (mc.player == null) return;

            region.x1 = region.x2 = mc.player.getBlockX();
            region.y1 = region.y2 = mc.player.getBlockY();
            region.z1 = region.z2 = mc.player.getBlockZ();

            clear();
            initWidgets();
        };
        table.row();

        coordRow(table, "Corner 1", () -> region.x1, v -> region.x1 = v, () -> region.y1, v -> region.y1 = v, () -> region.z1, v -> region.z1 = v);
        snapRow(table, v -> { region.x1 = v.getX(); region.y1 = v.getY(); region.z1 = v.getZ(); });

        coordRow(table, "Corner 2", () -> region.x2, v -> region.x2 = v, () -> region.y2, v -> region.y2 = v, () -> region.z2, v -> region.z2 = v);
        snapRow(table, v -> { region.x2 = v.getX(); region.y2 = v.getY(); region.z2 = v.getZ(); });
    }

    private void initPointMode(WTable table) {
        table.add(theme.label("Point" + (region.biome.isEmpty() ? " - any biome" : " - biome: " + region.biome)));
        table.row();

        coordRow(table, "Point", () -> region.px, v -> region.px = v, () -> region.py, v -> region.py = v, () -> region.pz, v -> region.pz = v);

        table.add(theme.label("Shape"));
        var shape = table.add(theme.dropdown(region.pointShape)).expandCellX().widget();
        shape.action = () -> {
            region.pointShape = shape.get();

            clear();
            initWidgets();
        };
        table.row();

        table.add(theme.button("Snap Point To My Position")).expandCellX().widget().action = () -> {
            if (mc.player == null) return;

            region.px = mc.player.getBlockX();
            region.py = mc.player.getBlockY();
            region.pz = mc.player.getBlockZ();

            clear();
            initWidgets();
        };
        table.row();

        table.add(theme.label("Biome: " + biomeDisplayName(region.biome)));

        WHorizontalList biomeRow = table.add(theme.horizontalList()).expandCellX().widget();

        biomeRow.add(theme.button("Select Biome...")).right().widget().action = () -> {
            BiomeSelectScreen screen = new BiomeSelectScreen(theme, key -> {
                region.biome = key.getValue().toString();

                clear();
                initWidgets();
            });

            mc.setScreen(screen);
        };

        biomeRow.add(theme.button("Use My Current Biome")).right().widget().action = () -> {
            if (mc.world == null || mc.player == null) return;

            RegistryEntry<Biome> biomeEntry = mc.world.getBiome(BlockPos.ofFloored(mc.player.getPos()));
            region.biome = biomeEntry.getKey().map(key -> key.getValue().toString()).orElse("");

            clear();
            initWidgets();
        };

        biomeRow.add(theme.button("Any Biome")).right().widget().action = () -> {
            region.biome = "";

            clear();
            initWidgets();
        };
        table.row();

        boolean cylinderCoords = region.pointShape == AmbienceRegion.PointShape.Cylinder && region.cylinderHeightMode == AmbienceRegion.CylinderHeightMode.Coords;

        if (region.pointShape == AmbienceRegion.PointShape.Cylinder) {
            table.add(theme.label("Height Mode"));
            var heightMode = table.add(theme.dropdown(region.cylinderHeightMode)).expandCellX().widget();
            heightMode.action = () -> {
                region.cylinderHeightMode = heightMode.get();

                // Switching into Coords for the first time on a region that's never used it before
                // (both still at the Java default 0) silently matched nothing anywhere near the
                // point - snap it to the point's own current half-height range instead, the same
                // one-time convenience every other mode switch here already gives.
                if (region.cylinderHeightMode == AmbienceRegion.CylinderHeightMode.Coords && region.cylinderMinY == 0 && region.cylinderMaxY == 0) {
                    region.cylinderMinY = region.py - region.radiusY;
                    region.cylinderMaxY = region.py + region.radiusY;
                }

                clear();
                initWidgets();
            };
            table.row();
        }

        if (cylinderCoords) {
            table.add(theme.label("Height Range (Y)"));

            WHorizontalList heightRow = table.add(theme.horizontalList()).expandCellX().widget();

            heightRow.add(theme.label("Min"));
            WIntEdit minY = heightRow.add(theme.intEdit(region.cylinderMinY, -2032, 2032, true)).widget();
            minY.action = () -> region.cylinderMinY = minY.get();

            heightRow.add(theme.label("Max"));
            WIntEdit maxY = heightRow.add(theme.intEdit(region.cylinderMaxY, -2032, 2032, true)).widget();
            maxY.action = () -> region.cylinderMaxY = maxY.get();

            table.row();
        }

        table.add(theme.label("Radius"));

        WHorizontalList radiusRow = table.add(theme.horizontalList()).expandCellX().widget();

        radiusRow.add(theme.label("Width (X)"));
        WIntEdit rx = radiusRow.add(theme.intEdit(region.radiusX, 1, 1000, true)).widget();
        rx.action = () -> region.radiusX = rx.get();

        if (!cylinderCoords) {
            radiusRow.add(theme.label(region.pointShape == AmbienceRegion.PointShape.Cylinder ? "Half-Height (Y)" : "Height (Y)"));
            WIntEdit ry = radiusRow.add(theme.intEdit(region.radiusY, 1, 1000, true)).widget();
            ry.action = () -> region.radiusY = ry.get();
        }

        radiusRow.add(theme.label("Length (Z)"));
        WIntEdit rz = radiusRow.add(theme.intEdit(region.radiusZ, 1, 1000, true)).widget();
        rz.action = () -> region.radiusZ = rz.get();

        table.row();
    }

    private void setDimension(String dimensionId) {
        region.dimension = dimensionId;

        clear();
        initWidgets();
    }

    private static String dimensionDisplayName(String dimensionId) {
        if (dimensionId.isEmpty()) return "Any";
        if (dimensionId.equals(World.OVERWORLD.getValue().toString())) return "Overworld";
        if (dimensionId.equals(World.NETHER.getValue().toString())) return "Nether";
        if (dimensionId.equals(World.END.getValue().toString())) return "End";

        return dimensionId;
    }

    private static String biomeDisplayName(String biomeId) {
        if (biomeId.isEmpty()) return "Any";

        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(biomeId);
        if (id == null) return biomeId;

        return BiomeBlockColorMapSettingScreen.displayName(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.BIOME, id));
    }

    private interface IntGetter {
        int get();
    }

    private interface IntSetter {
        void set(int value);
    }

    private void coordRow(WTable table, String label, IntGetter xGet, IntSetter xSet, IntGetter yGet, IntSetter ySet, IntGetter zGet, IntSetter zSet) {
        table.add(theme.label(label));

        WHorizontalList row = table.add(theme.horizontalList()).expandCellX().widget();

        row.add(theme.label("X"));
        WIntEdit x = row.add(theme.intEdit(xGet.get(), -30000000, 30000000, true)).widget();
        x.action = () -> xSet.set(x.get());

        row.add(theme.label("Y"));
        WIntEdit y = row.add(theme.intEdit(yGet.get(), -2032, 2032, true)).widget();
        y.action = () -> ySet.set(y.get());

        row.add(theme.label("Z"));
        WIntEdit z = row.add(theme.intEdit(zGet.get(), -30000000, 30000000, true)).widget();
        z.action = () -> zSet.set(z.get());

        table.row();
    }

    /** A lone "Snap to My Position" button for the corner row right above it - stand at the spot, click, move on to the other corner. */
    private void snapRow(WTable table, Consumer<BlockPos> apply) {
        table.add(theme.label(""));

        table.add(theme.button("Snap To My Position")).expandCellX().widget().action = () -> {
            if (mc.player == null) return;

            apply.accept(mc.player.getBlockPos());

            clear();
            initWidgets();
        };

        table.row();
    }
}
