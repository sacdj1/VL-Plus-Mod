/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.settings.ItemDisplayOverride;
import meteordevelopment.meteorclient.settings.Setting;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ItemDisplayOverrideListScreen extends WindowScreen {
    private final Setting<List<ItemDisplayOverride>> setting;

    private WTable table;

    public ItemDisplayOverrideListScreen(GuiTheme theme, Setting<List<ItemDisplayOverride>> setting) {
        super(theme, "Item Display Overrides");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        table.clear();

        table.add(theme.label("Matches by name/lore (either blank skips that check) - the first matching, enabled entry wins.", 400));
        table.row();
        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        for (ItemDisplayOverride o : List.copyOf(setting.get())) {
            String label = o.label.isBlank() ? "(unnamed)" : o.label;
            table.add(theme.label(label)).expandCellX();

            WHorizontalList list = table.add(theme.horizontalList()).expandCellX().widget();

            var enabled = list.add(theme.checkbox(o.enabled)).right().widget();
            enabled.action = () -> o.enabled = enabled.checked;

            list.add(theme.button("Edit")).right().widget().action = () -> {
                ItemDisplayOverrideEditScreen screen = new ItemDisplayOverrideEditScreen(theme, o);
                screen.onClosed(() -> {
                    setting.onChanged();
                    initTable();
                });

                mc.setScreen(screen);
            };

            list.add(theme.minus()).right().widget().action = () -> {
                setting.get().remove(o);
                setting.onChanged();

                initTable();
            };

            table.row();
        }

        if (!setting.get().isEmpty()) table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.button("Add Override")).expandX().widget().action = () -> {
            ItemDisplayOverride o = new ItemDisplayOverride();
            o.label = "Override " + (setting.get().size() + 1);

            setting.get().add(o);
            setting.onChanged();

            ItemDisplayOverrideEditScreen screen = new ItemDisplayOverrideEditScreen(theme, o);
            screen.onClosed(() -> {
                setting.onChanged();
                initTable();
            });

            mc.setScreen(screen);
        };
    }
}
