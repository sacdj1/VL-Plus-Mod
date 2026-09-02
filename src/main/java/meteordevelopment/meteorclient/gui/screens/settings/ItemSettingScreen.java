/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WItemWithLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.world.LegacyItems;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import org.apache.commons.lang3.StringUtils;

public class ItemSettingScreen extends WindowScreen {
    private final ItemSetting setting;

    private WTable table;

    private WTextBox filter;
    private String filterText = "";
    private boolean legacyOnly = true;

    public ItemSettingScreen(GuiTheme theme, ItemSetting setting) {
        super(theme, "Select item");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        add(VLItemPickerSection.build(theme, false,
            vlItem -> setting.get() == vlItem.baseItem,
            vlItem -> {
                setting.set(vlItem.baseItem);
                close();
            },
            vlItem -> {}
        )).expandX();

        WHorizontalList header = add(theme.horizontalList()).expandX().widget();

        filter = header.add(theme.textBox("")).minWidth(400).expandCellX().widget();
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

    public void initTable() {
        for (Item item : Registries.ITEM) {
            if (setting.filter != null && !setting.filter.test(item)) continue;
            if (item == Items.AIR) continue;
            if (legacyOnly && !LegacyItems.isLegacy(Registries.ITEM.getId(item).getPath())) continue;

            WItemWithLabel itemLabel = theme.itemWithLabel(item.getDefaultStack(), Names.get(item));
            if (!filterText.isEmpty() && !StringUtils.containsIgnoreCase(itemLabel.getLabelText(), filterText)) continue;
            table.add(itemLabel);

            WButton select = table.add(theme.button("Select")).expandCellX().right().widget();
            select.action = () -> {
                setting.set(item);
                close();
            };

            table.row();
        }
    }
}
