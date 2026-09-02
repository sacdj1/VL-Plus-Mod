/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.tabs.builtin;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.systems.vlitems.VLItem;
import meteordevelopment.meteorclient.systems.vlitems.VLItems;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class VLItemsTab extends Tab {
    public VLItemsTab() {
        super("VL Items");
    }

    @Override
    public TabScreen createScreen(GuiTheme theme) {
        return new VLItemsScreen(theme, this);
    }

    @Override
    public boolean isScreen(Screen screen) {
        return screen instanceof VLItemsScreen;
    }

    private static class VLItemsScreen extends WindowTabScreen {
        public VLItemsScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);
        }

        @Override
        public void initWidgets() {
            add(theme.label("Custom items list to add to normal item lists")).expandX();

            WTable table = add(theme.table()).expandX().minWidth(400).widget();
            initTable(table);

            add(theme.horizontalSeparator()).expandX();

            // Capture
            WButton capture = add(theme.button("Capture Held Item")).expandX().widget();
            capture.action = () -> {
                if (mc.player == null || mc.world == null) return;

                ItemStack stack = mc.player.getMainHandStack();
                if (stack.isEmpty()) {
                    ChatUtils.error("Not holding an item.");
                    return;
                }

                VLItem item = VLItem.fromStack(stack, mc.world.getRegistryManager());

                if (VLItems.get().add(item)) reload();
                else ChatUtils.error("That item is already in the pool.");
            };
        }

        private void initTable(WTable table) {
            table.clear();
            if (VLItems.get().isEmpty()) return;

            for (VLItem item : VLItems.get()) {
                ItemStack stack = mc.world != null ? item.buildStack(mc.world.getRegistryManager()) : item.baseItem.getDefaultStack();

                table.add(theme.itemWithLabel(stack, item.displayLabel)).expandCellX();

                WMinus remove = table.add(theme.minus()).right().widget();
                remove.action = () -> {
                    VLItems.get().remove(item);
                    reload();
                };

                table.row();
            }
        }

        @Override
        public boolean toClipboard() {
            return NbtUtils.toClipboard(VLItems.get());
        }

        @Override
        public boolean fromClipboard() {
            return NbtUtils.fromClipboard(VLItems.get());
        }
    }
}
