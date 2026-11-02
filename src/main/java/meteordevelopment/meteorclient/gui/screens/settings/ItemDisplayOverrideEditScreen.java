/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WDoubleEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.ItemDisplayOverride;

public class ItemDisplayOverrideEditScreen extends WindowScreen {
    private final ItemDisplayOverride o;
    private WTable table;

    public ItemDisplayOverrideEditScreen(GuiTheme theme, ItemDisplayOverride o) {
        super(theme, "Edit Override");

        this.o = o;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();

        buildRows();
    }

    private void buildRows() {
        table.clear();

        table.add(theme.label("Label"));
        WTextBox label = table.add(theme.textBox(o.label)).expandX().widget();
        label.action = () -> { if (!label.get().isBlank()) o.label = label.get(); };
        table.row();

        table.add(theme.label("Name contains"));
        WTextBox name = table.add(theme.textBox(o.nameMatch)).expandX().widget();
        name.action = () -> o.nameMatch = name.get();
        table.row();

        table.add(theme.label("Lore line contains"));
        WTextBox lore = table.add(theme.textBox(o.loreMatch)).expandX().widget();
        lore.action = () -> o.loreMatch = lore.get();
        table.row();

        table.add(theme.label("Mode"));
        var modeDropdown = table.add(theme.dropdown(o.mode)).expandCellX().widget();
        modeDropdown.action = () -> o.mode = modeDropdown.get();
        table.row();

        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.label("Translation (in the same units the model's own JSON \"translation\" uses)", 400));
        table.row();
        addVec3Row("X", o.translateX, v -> o.translateX = v, -16, 16);
        addVec3Row("Y", o.translateY, v -> o.translateY = v, -16, 16);
        addVec3Row("Z", o.translateZ, v -> o.translateZ = v, -16, 16);

        table.add(theme.label("Rotation (degrees)"));
        table.row();
        addVec3Row("X", o.rotateX, v -> o.rotateX = v, -180, 180);
        addVec3Row("Y", o.rotateY, v -> o.rotateY = v, -180, 180);
        addVec3Row("Z", o.rotateZ, v -> o.rotateZ = v, -180, 180);

        table.add(theme.label("Scale (Add mode multiplies instead of adding - 1 means unchanged; Override replaces it outright)", 400));
        table.row();
        addVec3Row("X", o.scaleX, v -> o.scaleX = v, -4, 4);
        addVec3Row("Y", o.scaleY, v -> o.scaleY = v, -4, 4);
        addVec3Row("Z", o.scaleZ, v -> o.scaleZ = v, -4, 4);

        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.button("Back")).expandX().widget().action = this::close;
    }

    private void addVec3Row(String axisLabel, double value, java.util.function.DoubleConsumer onChanged, double min, double max) {
        table.add(theme.label(axisLabel));
        WDoubleEdit edit = table.add(theme.doubleEdit(value, min, max)).expandX().widget();
        edit.action = () -> onChanged.accept(edit.get());
        table.row();
    }
}
