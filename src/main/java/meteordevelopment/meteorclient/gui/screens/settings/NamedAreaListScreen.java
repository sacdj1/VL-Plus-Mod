/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.NamedArea;
import meteordevelopment.meteorclient.settings.Setting;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Named areas for Discord Presence's zone.current - each one is a name plus one or more X/Z boxes
 * (Y and dimension don't matter), so a single zone can be built out of several disconnected or
 * irregularly-shaped pieces instead of being stuck with exactly one rectangle (same reasoning as
 * AmbienceRegion's own region list). Whichever area has a box your current position falls inside
 * (first match wins) is what shows up; Visualize draws that area's boxes in-world.
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

        table.add(theme.label("Each area is one or more boxes on the X/Z plane - the first area with a box your position falls inside wins.", 400));
        table.row();
        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        for (NamedArea area : List.copyOf(setting.get())) {
            WHorizontalList header = table.add(theme.horizontalList()).expandCellX().widget();

            WTextBox name = header.add(theme.textBox(area.name)).expandCellX().widget();
            name.action = () -> {
                area.name = name.get();
                setting.onChanged();
            };

            header.add(theme.label("Visualize"));
            WCheckbox visualize = header.add(theme.checkbox(area.visualize)).widget();
            visualize.action = () -> {
                area.visualize = visualize.checked;
                setting.onChanged();
            };

            header.add(theme.minus()).widget().action = () -> {
                setting.get().remove(area);
                setting.onChanged();

                table.clear();
                initTable();
            };

            table.row();

            for (NamedArea.Box box : List.copyOf(area.boxes)) {
                table.add(theme.label("Corner 1")).expandCellX();

                WHorizontalList corner1 = table.add(theme.horizontalList()).expandCellX().widget();
                coord(corner1, "X", () -> box.x1, v -> box.x1 = v);
                coord(corner1, "Z", () -> box.z1, v -> box.z1 = v);
                corner1.add(theme.button("Snap")).right().widget().action = () -> {
                    if (mc.player == null) return;
                    box.x1 = mc.player.getBlockX();
                    box.z1 = mc.player.getBlockZ();

                    table.clear();
                    initTable();
                };

                table.row();

                table.add(theme.label("Corner 2"));

                WHorizontalList corner2 = table.add(theme.horizontalList()).expandCellX().widget();
                coord(corner2, "X", () -> box.x2, v -> box.x2 = v);
                coord(corner2, "Z", () -> box.z2, v -> box.z2 = v);
                corner2.add(theme.button("Snap")).right().widget().action = () -> {
                    if (mc.player == null) return;
                    box.x2 = mc.player.getBlockX();
                    box.z2 = mc.player.getBlockZ();

                    table.clear();
                    initTable();
                };

                WHorizontalList boxActions = table.add(theme.horizontalList()).expandCellX().widget();
                boxActions.add(theme.minus()).right().widget().action = () -> {
                    area.boxes.remove(box);
                    setting.onChanged();

                    table.clear();
                    initTable();
                };

                table.row();
            }

            table.add(theme.button("Add Box At My Position")).expandCellX().widget().action = () -> {
                NamedArea.Box box = new NamedArea.Box();

                if (mc.player != null) {
                    box.x1 = box.x2 = mc.player.getBlockX();
                    box.z1 = box.z2 = mc.player.getBlockZ();
                }

                area.boxes.add(box);
                setting.onChanged();

                table.clear();
                initTable();
            };

            table.row();
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        table.add(theme.button("Add Area")).expandCellX().widget().action = () -> {
            NamedArea area = new NamedArea();
            area.name = "Area " + (setting.get().size() + 1);

            NamedArea.Box box = new NamedArea.Box();
            if (mc.player != null) {
                box.x1 = box.x2 = mc.player.getBlockX();
                box.z1 = box.z2 = mc.player.getBlockZ();
            }
            area.boxes.add(box);

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
