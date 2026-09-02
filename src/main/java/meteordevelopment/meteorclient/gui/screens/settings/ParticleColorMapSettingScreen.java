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
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.settings.ParticleKey;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
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

    private WTable table;
    private WSection blockSection, itemSection;

    private String filterText = "";
    private boolean legacyOnly = true;

    public ParticleColorMapSettingScreen(GuiTheme theme, Setting<Map<ParticleKey, SettingColor>> setting) {
        super(theme, "Select Particles and Colors");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        WHorizontalList header = add(theme.horizontalList()).expandX().widget();

        WTextBox filter = header.add(theme.textBox("")).minWidth(400).expandCellX().widget();
        filter.setFocused(true);
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

        table.add(theme.label(name)).expandCellX();

        WHorizontalList list = table.add(theme.horizontalList()).widget();

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

        // Particles not yet added - Block/Block Marker/Item need a specific block/item chosen, so
        // they're handled by their own categorized sections below instead of showing up here.
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
            table.add(theme.label(Names.get(particleType))).expandCellX();

            table.add(theme.plus()).expandCellX().right().widget().action = () -> {
                setting.get().put(ParticleKey.of(particleType), new SettingColor(255, 255, 255, 255));
                setting.onChanged();
                reload();
            };

            table.row();
        }
    }

    private void initBlockTable(WTable blockTable) {
        List<Block> all = new ArrayList<>();
        Registries.BLOCK.forEach(block -> {
            ParticleKey key = ParticleKey.ofBlock(ParticleTypes.BLOCK, block);
            if (setting.get().containsKey(key)) return;
            if (legacyOnly && !LegacyBlocks.isLegacy(Registries.BLOCK.getId(block).getPath())) return;

            String name = Names.get(block);
            if (StringUtils.containsIgnoreCase(name, filterText)) all.add(block);
        });
        all.sort(Comparator.comparing(Names::get));

        for (Block block : all) {
            ParticleKey key = ParticleKey.ofBlock(ParticleTypes.BLOCK, block);

            blockTable.add(theme.itemWithLabel(block.asItem().getDefaultStack(), Names.get(block))).expandCellX();

            blockTable.add(theme.plus()).expandCellX().right().widget().action = () -> {
                setting.get().put(key, new SettingColor(255, 255, 255, 255));
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

            itemTable.add(theme.itemWithLabel(item.getDefaultStack(), Names.get(item))).expandCellX();

            itemTable.add(theme.plus()).expandCellX().right().widget().action = () -> {
                setting.get().put(key, new SettingColor(255, 255, 255, 255));
                setting.onChanged();
                reload();
            };

            itemTable.row();
        }
    }
}
