/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.systems.vlitems.VLItem;
import meteordevelopment.meteorclient.systems.vlitems.VLItems;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;

import java.util.function.Consumer;
import java.util.function.Predicate;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Shared "VL Items" collapsible section, reused by every item picker screen. Since the underlying
 * setting types here (Set/Map/single Item) can only ever store a plain vanilla Item, picking a VL
 * item from this section operates on its base item - two VL items sharing the same base item are
 * indistinguishable here, unlike VLItemSettingScreen (used by ItemHud), which tracks the captured
 * VLItem itself and can tell them apart.
 */
public class VLItemPickerSection {
    private VLItemPickerSection() {
    }

    public static WSection build(GuiTheme theme, boolean expanded, Predicate<VLItem> isSelected, Consumer<VLItem> onSelect, Consumer<VLItem> onDeselect) {
        WSection section = theme.section("VL Items", expanded);
        WTable table = section.add(theme.table()).expandX().widget();

        if (VLItems.get().isEmpty()) {
            table.add(theme.label("No VL items captured yet - see the VL Items tab."));
            return section;
        }

        RegistryWrapper.WrapperLookup registries = mc.world != null ? mc.world.getRegistryManager() : null;

        for (VLItem item : VLItems.get()) {
            ItemStack stack = registries != null ? item.buildStack(registries) : item.baseItem.getDefaultStack();
            table.add(theme.itemWithLabel(stack, item.displayLabel)).expandCellX();

            boolean selected = isSelected.test(item);
            WPressable button = table.add(selected ? theme.minus() : theme.plus()).expandCellX().right().widget();
            button.action = () -> {
                if (selected) onDeselect.accept(item);
                else onSelect.accept(item);
            };

            table.row();
        }

        return section;
    }
}
