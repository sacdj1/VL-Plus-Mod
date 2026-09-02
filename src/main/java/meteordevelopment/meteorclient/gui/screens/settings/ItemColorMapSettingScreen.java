/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WQuad;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.LegacyItems;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ItemColorMapSettingScreen extends WindowScreen {
    private final Setting<Map<Item, SettingColor>> setting;

    private WTable table;

    private String filterText = "";
    private boolean legacyOnly = true;

    public ItemColorMapSettingScreen(GuiTheme theme, Setting<Map<Item, SettingColor>> setting) {
        super(theme, "Select Items and Colors");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        add(VLItemPickerSection.build(theme, false,
            vlItem -> setting.get().containsKey(vlItem.baseItem),
            vlItem -> {
                setting.get().put(vlItem.baseItem, new SettingColor(255, 255, 255, 255));
                setting.onChanged();
                reload();
            },
            vlItem -> {
                setting.get().remove(vlItem.baseItem);
                setting.onChanged();
                reload();
            }
        )).expandX();

        WHorizontalList header = add(theme.horizontalList()).expandX().widget();

        WTextBox filter = header.add(theme.textBox("")).minWidth(400).expandCellX().widget();
        filter.setFocused(true);
        filter.action = () -> {
            filterText = filter.get().trim();

            table.clear();
            initTable();
        };

        WCheckbox legacyCheckbox = header.add(theme.checkbox(legacyOnly)).right().widget();
        header.add(theme.label("Legacy Items")).right();
        legacyCheckbox.action = () -> {
            legacyOnly = legacyCheckbox.checked;

            table.clear();
            initTable();
        };

        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        // Selected items
        List<Item> selected = new ArrayList<>(setting.get().keySet());
        selected.sort(Comparator.comparing(Names::get));

        for (Item item : selected) {
            String name = Names.get(item);
            if (!StringUtils.containsIgnoreCase(name, filterText)) continue;

            SettingColor color = setting.get().get(item);

            table.add(theme.itemWithLabel(item.getDefaultStack(), name)).expandCellX();

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
                setting.get().remove(item);
                setting.onChanged();

                table.clear();
                initTable();
            };

            table.row();
        }

        if (!selected.isEmpty()) table.add(theme.horizontalSeparator()).expandX();
        table.row();

        // Items not yet added
        List<Item> all = new ArrayList<>();
        Registries.ITEM.forEach(item -> {
            if (item == Items.AIR || setting.get().containsKey(item)) return;
            if (legacyOnly && !LegacyItems.isLegacy(Registries.ITEM.getId(item).getPath())) return;

            String name = Names.get(item);
            if (StringUtils.containsIgnoreCase(name, filterText)) all.add(item);
        });
        all.sort(Comparator.comparing(Names::get));

        for (Item item : all) {
            table.add(theme.itemWithLabel(item.getDefaultStack(), Names.get(item))).expandCellX();

            table.add(theme.plus()).expandCellX().right().widget().action = () -> {
                setting.get().put(item, new SettingColor(255, 255, 255, 255));
                setting.onChanged();

                table.clear();
                initTable();
            };

            table.row();
        }
    }
}
