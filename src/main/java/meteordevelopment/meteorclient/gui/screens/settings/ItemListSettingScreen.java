/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.world.LegacyItems;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.function.Predicate;

public class ItemListSettingScreen extends RegistryListSettingScreen<Item> {
    public ItemListSettingScreen(GuiTheme theme, ItemListSetting setting) {
        super(theme, "Select Items", setting, setting.get(), Registries.ITEM);
    }

    @Override
    public void initWidgets() {
        add(VLItemPickerSection.build(theme, false,
            vlItem -> collection.contains(vlItem.baseItem),
            vlItem -> {
                if (collection.add(vlItem.baseItem)) { setting.onChanged(); reload(); }
            },
            vlItem -> {
                if (collection.remove(vlItem.baseItem)) { setting.onChanged(); reload(); }
            }
        )).expandX();

        super.initWidgets();
    }

    @Override
    protected boolean includeValue(Item value) {
        Predicate<Item> filter = ((ItemListSetting) setting).filter;
        if (filter != null && !filter.test(value)) return false;

        return value != Items.AIR;
    }

    @Override
    protected WWidget getValueWidget(Item value) {
        return theme.itemWithLabel(value.getDefaultStack());
    }

    @Override
    protected String getValueName(Item value) {
        return Names.get(value);
    }

    @Override
    protected boolean supportsLegacyFilter() {
        return true;
    }

    @Override
    protected boolean isLegacyValue(Item value) {
        return LegacyItems.isLegacy(Registries.ITEM.getId(value).getPath());
    }
}
