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
import meteordevelopment.meteorclient.settings.VLItemSetting;
import meteordevelopment.meteorclient.systems.vlitems.VLItem;
import meteordevelopment.meteorclient.systems.vlitems.VLItemChoice;
import meteordevelopment.meteorclient.systems.vlitems.VLItems;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.world.LegacyItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import org.apache.commons.lang3.StringUtils;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class VLItemSettingScreen extends WindowScreen {
    private final VLItemSetting setting;

    private WTable table;

    private String filterText = "";
    private boolean legacyOnly = true;

    public VLItemSettingScreen(GuiTheme theme, VLItemSetting setting) {
        super(theme, "Select item");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
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
        RegistryWrapper.WrapperLookup registries = mc.world != null ? mc.world.getRegistryManager() : null;
        boolean any = false;

        // VL items
        for (VLItem vlItem : VLItems.get()) {
            if (setting.filter != null && !setting.filter.test(vlItem.baseItem)) continue;
            if (!filterText.isEmpty() && !StringUtils.containsIgnoreCase(vlItem.displayLabel, filterText)) continue;

            any = true;

            ItemStack stack = registries != null ? vlItem.buildStack(registries) : vlItem.baseItem.getDefaultStack();
            table.add(theme.itemWithLabel(stack, vlItem.displayLabel));

            WButton select = table.add(theme.button("Select")).expandCellX().right().widget();
            select.action = () -> {
                setting.set(VLItemChoice.of(vlItem));
                close();
            };

            table.row();
        }

        if (any) table.add(theme.horizontalSeparator()).expandX();
        table.row();

        // Vanilla items
        for (Item item : Registries.ITEM) {
            if (setting.filter != null && !setting.filter.test(item)) continue;
            if (item == Items.AIR) continue;
            if (legacyOnly && !LegacyItems.isLegacy(Registries.ITEM.getId(item).getPath())) continue;

            WItemWithLabel itemLabel = theme.itemWithLabel(item.getDefaultStack(), Names.get(item));
            if (!filterText.isEmpty() && !StringUtils.containsIgnoreCase(itemLabel.getLabelText(), filterText)) continue;
            table.add(itemLabel);

            WButton select = table.add(theme.button("Select")).expandCellX().right().widget();
            select.action = () -> {
                setting.set(VLItemChoice.of(item));
                close();
            };

            table.row();
        }
    }
}
