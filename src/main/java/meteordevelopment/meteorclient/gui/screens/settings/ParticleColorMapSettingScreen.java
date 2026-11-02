/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WQuad;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.settings.ParticleKey;
import meteordevelopment.meteorclient.settings.ParticleRecolor;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockSearch;
import meteordevelopment.meteorclient.utils.world.LegacyBlocks;
import meteordevelopment.meteorclient.utils.world.LegacyItems;
import meteordevelopment.meteorclient.utils.world.LegacyParticles;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ParticleColorMapSettingScreen extends WindowScreen {
    private final Setting<Map<ParticleKey, SettingColor>> setting;
    private final boolean colorsMatter;

    private WTable table;
    private WSection blockSection, itemSection;

    private String filterText = "";
    private boolean legacyOnly = true;

    public ParticleColorMapSettingScreen(GuiTheme theme, Setting<Map<ParticleKey, SettingColor>> setting, boolean colorsMatter) {
        super(theme, colorsMatter ? "Select Particles and Colors" : "Select Particles");

        this.setting = setting;
        this.colorsMatter = colorsMatter;
    }

    @Override
    public void initWidgets() {
        WHorizontalList header = add(theme.horizontalList()).expandX().widget();

        // reload() below rebuilds this whole screen - including this textbox itself - on every
        // single keystroke (to re-filter the particle list live). Constructing the new textbox
        // with a hardcoded "" instead of the current filterText wiped whatever was typed so far
        // back to blank on every character, making it look like typing just didn't work at all.
        WTextBox filter = header.add(theme.textBox(filterText)).minWidth(400).expandCellX().widget();
        filter.setFocused(true);
        filter.setCursorMax();
        filter.action = () -> {
            filterText = filter.get().trim();
            reload();
        };

        WCheckbox legacyCheckbox = header.add(theme.checkbox(legacyOnly)).right().widget();
        header.add(theme.label("Legacy")).right();
        legacyCheckbox.action = () -> {
            legacyOnly = legacyCheckbox.checked;
            reload();
        };

        table = add(theme.table()).expandX().widget();
        initTable();

        // Its own table, separate from the "selected" one above - sharing one table/column grid
        // with those wide mode-dropdown-and-slider rows was stretching this plain name+plus row's
        // own columns to match, leaving the + pinned at the window's far right edge regardless of
        // how short the particle's name was.
        WTable addTable = add(theme.table()).expandX().widget();
        initAddTable(addTable);

        blockSection = theme.section("Block Particles", blockSection != null && blockSection.isExpanded());
        add(blockSection).expandX();
        WTable blockTable = blockSection.add(theme.table()).expandX().widget();
        initBlockTable(blockTable);

        itemSection = theme.section("Item Particles", itemSection != null && itemSection.isExpanded());
        add(itemSection).expandX();
        WTable itemTable = itemSection.add(theme.table()).expandX().widget();
        initItemTable(itemTable);
    }

    private String displayName(ParticleKey key) {
        if (key.block != null) return Names.get(key.type) + " (" + Names.get(key.block) + ")";
        if (key.item != null) return Names.get(key.type) + " (" + Names.get(key.item) + ")";

        return Names.get(key.type);
    }

    private void addColorRow(WTable table, ParticleKey key, String name) {
        SettingColor color = setting.get().get(key);

        if (key.block != null) table.add(theme.itemWithLabel(key.block.asItem().getDefaultStack(), name)).expandCellX();
        else if (key.item != null) table.add(theme.itemWithLabel(key.item.getDefaultStack(), name)).expandCellX();
        else table.add(theme.label(name)).expandCellX();

        if (colorsMatter) {
            WHorizontalList list = table.add(theme.horizontalList()).widget();

            if (color instanceof ParticleRecolor recolor) {
                WDropdown<ParticleRecolor.Mode> modeDropdown = list.add(theme.dropdown(recolor.mode)).widget();
                modeDropdown.action = () -> {
                    recolor.mode = modeDropdown.get();
                    setting.onChanged();
                };
            }

            WQuad quad = list.add(theme.quad(color)).widget();

            WIntEdit r = list.add(theme.intEdit(color.r, 0, 255, 0, 255, false)).widget();
            WIntEdit g = list.add(theme.intEdit(color.g, 0, 255, 0, 255, false)).widget();
            WIntEdit b = list.add(theme.intEdit(color.b, 0, 255, 0, 255, false)).widget();
            WIntEdit a = list.add(theme.intEdit(color.a, 0, 255, 0, 255, false)).widget();

            Runnable update = () -> {
                color.r = r.get();
                color.g = g.get();
                color.b = b.get();
                color.a = a.get();
                color.validate();

                quad.color.set(color);
                setting.onChanged();
            };

            r.action = update;
            g.action = update;
            b.action = update;
            a.action = update;
        } else {
            table.add(theme.horizontalList());
        }

        WHorizontalList removeList = table.add(theme.horizontalList()).expandCellX().widget();
        removeList.add(theme.minus()).right().widget().action = () -> {
            setting.get().remove(key);
            setting.onChanged();
            reload();
        };

        table.row();
    }

    private void initTable() {
        // Selected particles (every kind mixed together)
        List<ParticleKey> selected = new ArrayList<>(setting.get().keySet());
        selected.sort(Comparator.comparing(this::displayName));

        for (ParticleKey key : selected) {
            String name = displayName(key);
            if (!StringUtils.containsIgnoreCase(name, filterText)) continue;

            addColorRow(table, key, name);
        }

        if (!selected.isEmpty()) table.add(theme.horizontalSeparator()).expandX();
        table.row();
    }

    // Particles not yet added - Block/Block Marker/Item need a specific block/item chosen, so
    // they're handled by their own categorized sections below instead of showing up here.
    private void initAddTable(WTable addTable) {
        List<ParticleType<?>> all = new ArrayList<>();
        Registries.PARTICLE_TYPE.forEach(particleType -> {
            if (particleType == ParticleTypes.BLOCK || particleType == ParticleTypes.BLOCK_MARKER || particleType == ParticleTypes.ITEM) return;
            if (setting.get().containsKey(ParticleKey.of(particleType))) return;
            if (legacyOnly && !LegacyParticles.isLegacy(Registries.PARTICLE_TYPE.getId(particleType).getPath())) return;

            String name = Names.get(particleType);
            if (StringUtils.containsIgnoreCase(name, filterText)) all.add(particleType);
        });
        all.sort(Comparator.comparing(Names::get));

        for (ParticleType<?> particleType : all) {
            addTable.add(theme.label(Names.get(particleType)));

            addTable.add(theme.plus()).widget().action = () -> {
                setting.get().put(ParticleKey.of(particleType), new ParticleRecolor(255, 255, 255, 255));
                setting.onChanged();
                reload();
            };

            addTable.row();
        }
    }

    private void initBlockTable(WTable blockTable) {
        List<Block> all = new ArrayList<>();
        Registries.BLOCK.forEach(block -> {
            ParticleKey key = ParticleKey.ofBlock(ParticleTypes.BLOCK, block);
            if (setting.get().containsKey(key)) return;
            if (legacyOnly && !LegacyBlocks.isLegacy(Registries.BLOCK.getId(block).getPath())) return;

            if (BlockSearch.matches(block, filterText)) all.add(block);
        });
        all.sort(Comparator.comparing(Names::get));

        for (Block block : all) {
            ParticleKey key = ParticleKey.ofBlock(ParticleTypes.BLOCK, block);

            blockTable.add(theme.itemWithLabel(block.asItem().getDefaultStack(), Names.get(block)));

            blockTable.add(theme.plus()).widget().action = () -> {
                setting.get().put(key, new ParticleRecolor(255, 255, 255, 255));
                setting.onChanged();
                reload();
            };

            blockTable.row();
        }
    }

    private void initItemTable(WTable itemTable) {
        List<Item> all = new ArrayList<>();
        Registries.ITEM.forEach(item -> {
            if (item == Items.AIR) return;

            ParticleKey key = ParticleKey.ofItem(ParticleTypes.ITEM, item);
            if (setting.get().containsKey(key)) return;
            if (legacyOnly && !LegacyItems.isLegacy(Registries.ITEM.getId(item).getPath())) return;

            String name = Names.get(item);
            if (StringUtils.containsIgnoreCase(name, filterText)) all.add(item);
        });
        all.sort(Comparator.comparing(Names::get));

        for (Item item : all) {
            ParticleKey key = ParticleKey.ofItem(ParticleTypes.ITEM, item);

            itemTable.add(theme.itemWithLabel(item.getDefaultStack(), Names.get(item)));

            itemTable.add(theme.plus()).widget().action = () -> {
                setting.get().put(key, new ParticleRecolor(255, 255, 255, 255));
                setting.onChanged();
                reload();
            };

            itemTable.row();
        }
    }
}
