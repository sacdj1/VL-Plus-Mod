/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.NamedArea;
import meteordevelopment.meteorclient.settings.Setting;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Named X/Z box areas for Discord Presence's zone.current - plot boundaries from your map, give
 * each a name, and whichever one your current position falls inside (first match wins) is what
 * shows up. No name matches at all -> the default area name setting right above this screen.
 */
public class NamedAreaListScreen extends WindowScreen {
    private final Setting<List<NamedArea>> setting;

    private WTable table;

    public NamedAreaListScreen(GuiTheme theme, Setting<List<NamedArea>> setting) {
        super(theme, "Named Areas");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        table.clear();

        table.add(theme.label("Each area is a box on the X/Z plane (Y and dimension don't matter) - the first one your position falls inside wins.", 400));
        table.row();
        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        for (NamedArea area : List.copyOf(setting.get())) {
            WTextBox name = table.add(theme.textBox(area.name)).expandCellX().widget();
            name.action = () -> {
                area.name = name.get();
                setting.onChanged();
            };

            table.row();

            table.add(theme.label("Corner 1"));

            WHorizontalList corner1 = table.add(theme.horizontalList()).expandCellX().widget();
            coord(corner1, "X", () -> area.x1, v -> area.x1 = v);
            coord(corner1, "Z", () -> area.z1, v -> area.z1 = v);
            corner1.add(theme.button("Snap")).right().widget().action = () -> {
                if (mc.player == null) return;
                area.x1 = mc.player.getBlockX();
                area.z1 = mc.player.getBlockZ();

                table.clear();
                initTable();
            };

            table.row();

            table.add(theme.label("Corner 2"));

            WHorizontalList corner2 = table.add(theme.horizontalList()).expandCellX().widget();
            coord(corner2, "X", () -> area.x2, v -> area.x2 = v);
            coord(corner2, "Z", () -> area.z2, v -> area.z2 = v);
            corner2.add(theme.button("Snap")).right().widget().action = () -> {
                if (mc.player == null) return;
                area.x2 = mc.player.getBlockX();
                area.z2 = mc.player.getBlockZ();

                table.clear();
                initTable();
            };

            WHorizontalList actions = table.add(theme.horizontalList()).expandCellX().widget();
            actions.add(theme.minus()).right().widget().action = () -> {
                setting.get().remove(area);
                setting.onChanged();

                table.clear();
                initTable();
            };

            table.row();
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        table.add(theme.button("Add Area At My Position")).expandCellX().widget().action = () -> {
            NamedArea area = new NamedArea();
            area.name = "Area " + (setting.get().size() + 1);

            if (mc.player != null) {
                area.x1 = area.x2 = mc.player.getBlockX();
                area.z1 = area.z2 = mc.player.getBlockZ();
            }

            setting.get().add(area);
            setting.onChanged();

            table.clear();
            initTable();
        };
    }

    private interface IntGetter {
        int get();
    }

    private interface IntSetter {
        void set(int value);
    }

    private void coord(WHorizontalList list, String label, IntGetter get, IntSetter set) {
        list.add(theme.label(label));

        WIntEdit edit = list.add(theme.intEdit(get.get(), -30000000, 30000000, true)).widget();
        edit.action = () -> set.set(edit.get());
    }
}
