/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.AmbiencePreset;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;

import java.util.List;

public class PresetListScreen extends WindowScreen {
    private final Ambience ambience;

    private WTable table;
    private String newPresetName = "";

    public PresetListScreen(GuiTheme theme, Ambience ambience) {
        super(theme, "Ambience Presets");

        this.ambience = ambience;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        table.clear();

        table.add(theme.label("Snapshots of every override below (sky/world colors, per-biome colors, regions, global blocks) - save the current look, or switch between saved ones.", 400));
        table.row();
        table.add(theme.label("Auto Season (in the main settings) uses its own four fixed slots instead of these - see Season Presets.", 400));
        table.row();
        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        for (AmbiencePreset preset : List.copyOf(ambience.presets.get())) {
            WTextBox name = table.add(theme.textBox(preset.name)).expandCellX().widget();
            name.action = () -> {
                preset.name = name.get();
                ambience.presets.onChanged();
            };

            WHorizontalList list = table.add(theme.horizontalList()).expandCellX().widget();

            list.add(theme.button("Apply")).right().widget().action = () -> ambience.applyPreset(preset);

            list.add(theme.button("Overwrite With Current")).right().widget().action = () -> {
                ambience.captureInto(preset.data);
                ambience.presets.onChanged();
            };

            list.add(theme.minus()).right().widget().action = () -> {
                ambience.presets.get().remove(preset);
                ambience.presets.onChanged();

                initTable();
            };

            table.row();
        }

        if (!ambience.presets.get().isEmpty()) table.add(theme.horizontalSeparator()).expandX();
        table.row();

        WTextBox nameBox = table.add(theme.textBox(newPresetName)).expandCellX().widget();
        nameBox.action = () -> newPresetName = nameBox.get();

        table.add(theme.button("Save Current As New Preset")).expandCellX().widget().action = () -> {
            String name = newPresetName.trim();
            if (name.isEmpty()) name = "Preset " + (ambience.presets.get().size() + 1);

            ambience.presets.get().add(ambience.capturePreset(name));
            ambience.presets.onChanged();

            newPresetName = "";
            initTable();
        };
    }
}
