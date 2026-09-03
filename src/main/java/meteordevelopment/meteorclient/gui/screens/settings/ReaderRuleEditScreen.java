/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WDoubleEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.ReaderRule;

public class ReaderRuleEditScreen extends WindowScreen {
    private final ReaderRule rule;

    public ReaderRuleEditScreen(GuiTheme theme, ReaderRule rule) {
        super(theme, "Edit Rule");

        this.rule = rule;
    }

    @Override
    public void initWidgets() {
        WTable table = add(theme.table()).expandX().widget();

        table.add(theme.label("Name"));
        WTextBox name = table.add(theme.textBox(rule.name)).expandX().widget();
        name.action = () -> rule.name = name.get();
        table.row();

        table.add(theme.label("Pattern (plain text, or a regex if Use Regex is on - matches anywhere in the text, not the whole thing)"));
        WTextBox pattern = table.add(theme.textBox(rule.pattern)).expandX().widget();
        pattern.action = () -> rule.pattern = pattern.get();
        table.row();

        addCheckboxRow(table, "Use Regex", rule.useRegex, v -> rule.useRegex = v);

        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.label("Watch"));
        table.row();
        addCheckboxRow(table, "Chat", rule.watchChat, v -> rule.watchChat = v);
        addCheckboxRow(table, "Boss Bar", rule.watchBossBar, v -> rule.watchBossBar = v);
        addCheckboxRow(table, "Scoreboard", rule.watchScoreboard, v -> rule.watchScoreboard = v);
        addCheckboxRow(table, "Title/Subtitle", rule.watchTitle, v -> rule.watchTitle = v);

        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.label("Notify with"));
        table.row();
        addCheckboxRow(table, "Sound", rule.notifySound, v -> rule.notifySound = v);
        addCheckboxRow(table, "Toast", rule.notifyToast, v -> rule.notifyToast = v);
        addCheckboxRow(table, "System Notification", rule.notifySystem, v -> rule.notifySystem = v);
        addCheckboxRow(table, "HUD Text (Reader Notif element)", rule.notifyHudText, v -> rule.notifyHudText = v);

        WHorizontalList durationRow = table.add(theme.horizontalList()).expandCellX().widget();
        durationRow.add(theme.label("HUD Text duration (seconds)"));
        WDoubleEdit duration = durationRow.add(theme.doubleEdit(rule.hudDuration, 0.5, 60)).right().widget();
        duration.action = () -> rule.hudDuration = duration.get();
        table.row();

        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.button("Back")).expandX().widget().action = this::close;
    }

    private void addCheckboxRow(WTable table, String label, boolean value, java.util.function.Consumer<Boolean> onChanged) {
        WHorizontalList row = table.add(theme.horizontalList()).expandCellX().widget();
        row.add(theme.label(label));

        var checkbox = row.add(theme.checkbox(value)).right().widget();
        checkbox.action = () -> onChanged.accept(checkbox.checked);

        table.row();
    }
}
