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
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

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
            BlockColorMapScreen screen = new BlockColorMapScreen(theme, region.name, "Region", region.blocks, () -> {},
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
        coordRow(table, "Corner 2", () -> region.x2, v -> region.x2 = v, () -> region.y2, v -> region.y2 = v, () -> region.z2, v -> region.z2 = v);
    }

    private void initPointMode(WTable table) {
        table.add(theme.label("Point: (" + region.px + ", " + region.py + ", " + region.pz + ")" +
            (region.biome.isEmpty() ? " - any biome" : " - biome: " + region.biome)));
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

        table.add(theme.label("Radius"));

        WHorizontalList radiusRow = table.add(theme.horizontalList()).expandCellX().widget();

        radiusRow.add(theme.label("Width (X)"));
        WIntEdit rx = radiusRow.add(theme.intEdit(region.radiusX, 1, 1000, true)).widget();
        rx.action = () -> region.radiusX = rx.get();

        radiusRow.add(theme.label("Height (Y)"));
        WIntEdit ry = radiusRow.add(theme.intEdit(region.radiusY, 1, 1000, true)).widget();
        ry.action = () -> region.radiusY = ry.get();

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
}
