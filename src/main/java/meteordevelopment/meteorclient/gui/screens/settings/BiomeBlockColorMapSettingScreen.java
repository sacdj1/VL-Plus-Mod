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
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.BlockColorMapNbt;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.WeightedColorEntry;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.world.LegacyBiomes;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.biome.Biome;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BiomeBlockColorMapSettingScreen extends WindowScreen {
    private final Setting<Map<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>>> setting;

    private WTable table;

    private String filterText = "";

    public BiomeBlockColorMapSettingScreen(GuiTheme theme, Setting<Map<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>>> setting) {
        super(theme, "Select Biomes");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        WTextBox filter = add(theme.textBox("")).minWidth(400).expandX().widget();
        filter.setFocused(true);
        filter.action = () -> {
            filterText = filter.get().trim();

            table.clear();
            initTable();
        };

        table = add(theme.table()).expandX().widget();

        initTable();
    }

    public static String displayName(RegistryKey<Biome> key) {
        return Utils.nameToTitle(key.getValue().getPath().replace('_', '-'));
    }

    private void initTable() {
        // Selected biomes
        List<RegistryKey<Biome>> selected = new ArrayList<>(setting.get().keySet());
        selected.sort(Comparator.comparing(BiomeBlockColorMapSettingScreen::displayName));

        for (RegistryKey<Biome> key : selected) {
            String name = displayName(key);
            if (!StringUtils.containsIgnoreCase(name, filterText)) continue;

            int blockCount = setting.get().get(key).size();

            table.add(theme.label(name)).expandCellX();

            WHorizontalList list = table.add(theme.horizontalList()).expandCellX().widget();

            Ambience ambience = Modules.get().get(Ambience.class);
            var enabled = list.add(theme.checkbox(ambience.isBiomeEnabled(key))).right().widget();
            enabled.action = () -> ambience.setBiomeEnabled(key, enabled.checked);

            list.add(theme.button("Blocks (" + blockCount + ")")).widget().action = () -> {
                BlockColorMapScreen screen = new BlockColorMapScreen(theme, name, "Biome", setting.get().get(key), setting::onChanged,
                    block -> ambience.isBlockEnabled(key, block), (block, en) -> ambience.setBlockEnabled(key, block, en));
                screen.onClosed(this::refresh);

                mc.setScreen(screen);
            };

            list.add(theme.button(GuiRenderer.COPY)).right().widget().action = () ->
                NbtUtils.toClipboard(name, BlockColorMapNbt.toTag(setting.get().get(key)));

            list.add(theme.button(GuiRenderer.PASTE)).right().widget().action = () -> {
                NbtCompound clipboard = NbtUtils.fromClipboard(BlockColorMapNbt.toTag(setting.get().get(key)));
                if (clipboard == null) return;

                Map<Block, List<WeightedColorEntry>> blocks = setting.get().get(key);
                blocks.clear();
                blocks.putAll(BlockColorMapNbt.fromTag(clipboard));
                setting.onChanged();

                table.clear();
                initTable();
            };

            list.add(theme.minus()).right().widget().action = () -> {
                setting.get().remove(key);
                setting.onChanged();

                table.clear();
                initTable();
            };

            table.row();
        }

        if (!selected.isEmpty()) table.add(theme.horizontalSeparator()).expandX();
        table.row();

        // Biomes not yet added (requires a loaded world to enumerate)
        if (mc.world == null) {
            table.add(theme.label("Join a world to add more biomes."));
            table.row();
            return;
        }

        Registry<Biome> biomes = mc.world.getRegistryManager().get(RegistryKeys.BIOME);
        boolean legacyOnly = Modules.get().get(Ambience.class).legacyBiomesOnly.get();

        List<RegistryKey<Biome>> all = new ArrayList<>();
        biomes.getIds().forEach(id -> {
            RegistryKey<Biome> key = RegistryKey.of(RegistryKeys.BIOME, id);
            if (setting.get().containsKey(key)) return;
            if (legacyOnly && !LegacyBiomes.isLegacy(id.getPath())) return;

            String name = displayName(key);
            if (StringUtils.containsIgnoreCase(name, filterText)) all.add(key);
        });
        all.sort(Comparator.comparing(BiomeBlockColorMapSettingScreen::displayName));

        for (RegistryKey<Biome> key : all) {
            table.add(theme.label(displayName(key))).expandCellX();

            table.add(theme.plus()).expandCellX().right().widget().action = () -> {
                setting.get().put(key, new LinkedHashMap<>());
                setting.onChanged();

                table.clear();
                initTable();
            };

            table.row();
        }
    }

    public void refresh() {
        table.clear();
        initTable();
    }
}
