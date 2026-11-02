/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.QuickAction;

public class QuickActionEditScreen extends WindowScreen {
    private final QuickAction action;
    private WTable table;

    public QuickActionEditScreen(GuiTheme theme, QuickAction action) {
        super(theme, "Edit Action");

        this.action = action;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();

        buildRows();
    }

    // Rebuildable (rather than laid out once) so the Command/Module-specific rows below show/hide
    // live as Type changes, instead of always showing both at once.
    private void buildRows() {
        table.clear();

        table.add(theme.label("Label"));
        WTextBox label = table.add(theme.textBox(action.label)).expandX().widget();
        label.action = () -> { if (!label.get().isBlank()) action.label = label.get(); };
        table.row();

        table.add(theme.label("Type"));
        var typeDropdown = table.add(theme.dropdown(action.type)).expandCellX().widget();
        typeDropdown.action = () -> {
            action.type = typeDropdown.get();
            buildRows();
        };
        table.row();

        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        if (action.type == QuickAction.Type.Command) {
            table.add(theme.label("Command (\";\" separates several, run in order, as if typed in chat)", 400));
            WTextBox command = table.add(theme.textBox(action.command)).expandX().widget();
            command.action = () -> action.command = command.get();
            table.row();
        }
        else if (action.type == QuickAction.Type.Module) {
            table.add(theme.label("Module id (its internal name, not its display title - e.g. \"fullbright\")", 400));
            WTextBox moduleId = table.add(theme.textBox(action.moduleId)).expandX().widget();
            moduleId.action = () -> action.moduleId = moduleId.get();
            table.row();

            table.add(theme.label("The button shows the module's own title and on/off color, not Label above - Label is only shown if the module id doesn't match anything.", 400));
            table.row();
        }
        else {
            table.add(theme.label("Only the Action Wheel actually navigates into this - selecting it there opens its own ring of these nested entries, one layer deeper, instead of firing anything. Quick Actions' plain button list has no layering and just skips Submenu entries.", 400));
            table.row();

            table.add(theme.button("Edit Sub-Actions (" + action.children.size() + ")")).expandX().widget().action = () -> {
                QuickActionListScreen screen = new QuickActionListScreen(theme, "Edit " + (action.label.isBlank() ? "Submenu" : action.label), action.children, () -> {});
                screen.onClosed(this::buildRows);
                meteordevelopment.meteorclient.MeteorClient.mc.setScreen(screen);
            };
            table.row();
        }

        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        if (action.type != QuickAction.Type.Submenu) {
            table.add(theme.label("Close Menu On Click"));
            var closeDropdown = table.add(theme.dropdown(action.closeBehavior)).expandCellX().widget();
            closeDropdown.action = () -> action.closeBehavior = closeDropdown.get();
            table.row();

            table.add(theme.label("For the Action Wheel: Close fires this and closes the whole wheel. Stay fires it but keeps the current ring open, so you can fire more from it without re-holding the key. Quick Actions' own plain list uses this the same way.", 400));
            table.row();
        }

        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.button("Back")).expandX().widget().action = this::close;
    }
}
