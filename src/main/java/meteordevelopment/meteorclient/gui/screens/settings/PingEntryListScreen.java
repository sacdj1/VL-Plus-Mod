/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WDoubleEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.PingEntry;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Custom ping definitions - the main way pings are made. Each one has a name (for picking it in
 * Action Wheel/Ping Wheel), optional text and/or symbol shown at the ping, and a list of colors -
 * one color is static, more than one flashes/cycles between them at Flash Speed. Duration is this
 * ping's own lifetime once fired.
 */
public class PingEntryListScreen extends WindowScreen {
    private final Setting<List<PingEntry>> setting;

    private WTable table;

    public PingEntryListScreen(GuiTheme theme, Setting<List<PingEntry>> setting) {
        super(theme, "Pings");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        table.clear();

        for (PingEntry entry : List.copyOf(setting.get())) {
            WHorizontalList header = table.add(theme.horizontalList()).expandCellX().widget();

            header.add(theme.label("Name"));
            WTextBox name = header.add(theme.textBox(entry.name)).expandCellX().widget();
            name.action = () -> {
                entry.name = name.get();
                setting.onChanged();
            };

            header.add(theme.minus()).widget().action = () -> {
                setting.get().remove(entry);
                setting.onChanged();

                table.clear();
                initTable();
            };

            table.row();

            table.add(theme.label("Text"));
            WTextBox text = table.add(theme.textBox(entry.text)).expandX().widget();
            text.action = () -> {
                entry.text = text.get();
                setting.onChanged();
            };
            table.row();

            table.add(theme.label("Symbol"));
            WTextBox symbol = table.add(theme.textBox(entry.symbol)).expandX().widget();
            symbol.action = () -> {
                entry.symbol = symbol.get();
                setting.onChanged();
            };
            table.row();

            table.add(theme.label("Flash Speed (flashes/sec, only matters with 2+ colors)"));
            WDoubleEdit flashSpeed = table.add(theme.doubleEdit(entry.flashSpeed, 0.1, 20)).expandX().widget();
            flashSpeed.action = () -> {
                entry.flashSpeed = flashSpeed.get();
                setting.onChanged();
            };
            table.row();

            table.add(theme.label("Duration (seconds)"));
            WDoubleEdit duration = table.add(theme.doubleEdit(entry.duration, 0.5, 120)).expandX().widget();
            duration.action = () -> {
                entry.duration = duration.get();
                setting.onChanged();
            };
            table.row();

            table.add(theme.label("Colors"));
            table.row();

            WTable colorsTable = table.add(theme.table()).expandX().widget();
            fillColors(colorsTable, entry);
            table.row();

            table.add(theme.button("Add Color")).expandCellX().widget().action = () -> {
                entry.colors.add(new SettingColor(255, 255, 255));
                setting.onChanged();

                colorsTable.clear();
                fillColors(colorsTable, entry);
            };

            table.row();
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        table.add(theme.button("Add Ping")).expandCellX().widget().action = () -> {
            setting.get().add(new PingEntry());
            setting.onChanged();

            table.clear();
            initTable();
        };
    }

    private void fillColors(WTable colorsTable, PingEntry entry) {
        int i = 0;
        for (SettingColor color : entry.colors) {
            int index = i;

            colorsTable.add(theme.quad(color)).widget();

            WButton edit = colorsTable.add(theme.button(GuiRenderer.EDIT)).widget();
            edit.action = () -> {
                ColorSetting temp = new ColorSetting("color", "", new SettingColor(color), newColor -> {
                    entry.colors.get(index).set(newColor);
                    setting.onChanged();
                }, null, null);
                temp.set(color);

                mc.setScreen(new ColorSettingScreen(theme, temp));
            };

            // Always leave at least one color behind - an empty list has nothing for the flash
            // logic to pick from, and a static single-color ping is the common case anyway.
            if (entry.colors.size() > 1) {
                colorsTable.add(theme.minus()).expandCellX().right().widget().action = () -> {
                    entry.colors.remove(index);
                    setting.onChanged();

                    colorsTable.clear();
                    fillColors(colorsTable, entry);
                };
            }

            colorsTable.row();
            i++;
        }
    }
}
