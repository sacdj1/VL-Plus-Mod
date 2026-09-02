/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.settings.ReaderRule;
import meteordevelopment.meteorclient.settings.Setting;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ReaderRuleListScreen extends WindowScreen {
    private final Setting<List<ReaderRule>> setting;

    private WTable table;

    public ReaderRuleListScreen(GuiTheme theme, Setting<List<ReaderRule>> setting) {
        super(theme, "Reader Rules");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        table.clear();

        table.add(theme.label("Each rule watches the sources you pick for text matching its pattern, then fires the notifications you pick."));
        table.row();
        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        for (ReaderRule rule : List.copyOf(setting.get())) {
            table.add(theme.label(rule.name)).expandCellX();
            table.add(theme.label(summary(rule)));

            WHorizontalList list = table.add(theme.horizontalList()).expandCellX().widget();

            var enabled = list.add(theme.checkbox(rule.enabled)).right().widget();
            enabled.action = () -> {
                rule.enabled = enabled.checked;
                setting.onChanged();
            };

            list.add(theme.button("Edit")).right().widget().action = () -> {
                ReaderRuleEditScreen screen = new ReaderRuleEditScreen(theme, rule);
                screen.onClosed(() -> {
                    setting.onChanged();
                    initTable();
                });

                mc.setScreen(screen);
            };

            list.add(theme.minus()).right().widget().action = () -> {
                setting.get().remove(rule);
                setting.onChanged();

                initTable();
            };

            table.row();
        }

        if (!setting.get().isEmpty()) table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.button("Add Rule")).expandX().widget().action = () -> {
            ReaderRule rule = new ReaderRule();
            rule.name = "Rule " + (setting.get().size() + 1);

            setting.get().add(rule);
            setting.onChanged();

            ReaderRuleEditScreen screen = new ReaderRuleEditScreen(theme, rule);
            screen.onClosed(() -> {
                setting.onChanged();
                initTable();
            });

            mc.setScreen(screen);
        };
    }

    private String summary(ReaderRule rule) {
        StringBuilder sb = new StringBuilder();

        if (rule.watchChat) sb.append("Chat ");
        if (rule.watchBossBar) sb.append("BossBar ");
        if (sb.isEmpty()) sb.append("(no sources) ");

        sb.append("- \"").append(rule.pattern.isEmpty() ? "(empty)" : rule.pattern).append("\"");

        return sb.toString();
    }
}
