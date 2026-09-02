/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.settings.AmbienceRegion;
import meteordevelopment.meteorclient.settings.BlockColorMapNbt;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import net.minecraft.nbt.NbtCompound;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class RegionListScreen extends WindowScreen {
    private final Setting<List<AmbienceRegion>> setting;

    private WTable table;

    public RegionListScreen(GuiTheme theme, Setting<List<AmbienceRegion>> setting) {
        super(theme, "Regions");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        table.clear();

        table.add(theme.label("Manually placed area overrides, higher priority than per-biome colors."));
        table.row();
        table.add(theme.label("Stand where you want the region to start (ideally the center of the area), then press Add."));
        table.row();
        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        for (AmbienceRegion region : List.copyOf(setting.get())) {
            String summary;
            if (region.pointMode) {
                summary = "Point (" + region.px + ", " + region.py + ", " + region.pz + ") r=" + region.radiusX + "/" + region.radiusY + "/" + region.radiusZ
                    + (region.biome.isEmpty() ? "" : " in " + region.biome);
            } else if (region.yOnly) {
                summary = "Y " + Math.min(region.y1, region.y2) + " to " + Math.max(region.y1, region.y2);
            } else {
                summary = "(" + region.x1 + ", " + region.y1 + ", " + region.z1 + ") to (" + region.x2 + ", " + region.y2 + ", " + region.z2 + ")";
            }

            table.add(theme.label(region.name)).expandCellX();
            table.add(theme.label(summary));

            WHorizontalList list = table.add(theme.horizontalList()).expandCellX().widget();

            var enabled = list.add(theme.checkbox(region.enabled)).right().widget();
            enabled.action = () -> {
                region.enabled = enabled.checked;
                setting.onChanged();
            };

            list.add(theme.button("Edit")).right().widget().action = () -> {
                RegionEditScreen screen = new RegionEditScreen(theme, region);
                screen.onClosed(() -> {
                    setting.onChanged();
                    initTable();
                });

                mc.setScreen(screen);
            };

            list.add(theme.button(GuiRenderer.COPY)).right().widget().action = () ->
                NbtUtils.toClipboard(region.name, BlockColorMapNbt.toTag(region.blocks));

            list.add(theme.button(GuiRenderer.PASTE)).right().widget().action = () -> {
                NbtCompound clipboard = NbtUtils.fromClipboard(BlockColorMapNbt.toTag(region.blocks));
                if (clipboard == null) return;

                region.blocks.clear();
                region.blocks.putAll(BlockColorMapNbt.fromTag(clipboard));
                setting.onChanged();

                initTable();
            };

            list.add(theme.minus()).right().widget().action = () -> {
                setting.get().remove(region);
                setting.onChanged();

                initTable();
            };

            table.row();
        }

        if (!setting.get().isEmpty()) table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.button("Add Region At My Position")).expandCellX().widget().action = () -> {
            if (mc.player == null) return;

            AmbienceRegion region = new AmbienceRegion();
            region.name = "Region " + (setting.get().size() + 1);
            region.dimension = mc.world.getRegistryKey().getValue().toString();
            region.x1 = region.x2 = mc.player.getBlockX();
            region.y1 = region.y2 = mc.player.getBlockY();
            region.z1 = region.z2 = mc.player.getBlockZ();

            setting.get().add(region);
            setting.onChanged();

            RegionEditScreen screen = new RegionEditScreen(theme, region);
            screen.onClosed(() -> {
                setting.onChanged();
                initTable();
            });

            mc.setScreen(screen);
        };
    }
}
