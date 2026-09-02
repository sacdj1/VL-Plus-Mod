/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import meteordevelopment.meteorclient.utils.world.LegacyBiomes;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.biome.Biome;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * A simple browsable/searchable biome picker - lets you pick a biome by name from the full
 * registry, rather than needing to physically stand in it.
 */
public class BiomeSelectScreen extends WindowScreen {
    private final Consumer<RegistryKey<Biome>> onSelect;

    private WTable table;
    private String filterText = "";

    public BiomeSelectScreen(GuiTheme theme, Consumer<RegistryKey<Biome>> onSelect) {
        super(theme, "Select Biome");

        this.onSelect = onSelect;
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

    private void initTable() {
        if (mc.world == null) {
            table.add(theme.label("Join a world to pick a biome."));
            return;
        }

        Registry<Biome> biomes = mc.world.getRegistryManager().get(RegistryKeys.BIOME);
        boolean legacyOnly = Modules.get().get(Ambience.class).legacyBiomesOnly.get();

        List<RegistryKey<Biome>> all = new ArrayList<>();
        biomes.getIds().forEach(id -> {
            if (legacyOnly && !LegacyBiomes.isLegacy(id.getPath())) return;

            RegistryKey<Biome> key = RegistryKey.of(RegistryKeys.BIOME, id);
            String name = BiomeBlockColorMapSettingScreen.displayName(key);
            if (StringUtils.containsIgnoreCase(name, filterText)) all.add(key);
        });
        all.sort(Comparator.comparing(BiomeBlockColorMapSettingScreen::displayName));

        for (RegistryKey<Biome> key : all) {
            table.add(theme.button(BiomeBlockColorMapSettingScreen.displayName(key))).expandCellX().widget().action = () -> {
                onSelect.accept(key);
                close();
            };

            table.row();
        }
    }
}
