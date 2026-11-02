/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.settings.QuickAction;
import meteordevelopment.meteorclient.settings.Setting;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class QuickActionListScreen extends WindowScreen {
    private final List<QuickAction> list;
    private final Runnable onChanged;

    private WTable table;

    public QuickActionListScreen(GuiTheme theme, Setting<List<QuickAction>> setting) {
        this(theme, "Quick Actions", setting.get(), setting::onChanged);
    }

    /** For editing a nested Submenu entry's own Children list - not a top-level Setting, just a plain list living inside its parent QuickAction, saved as part of that parent's own tag. */
    public QuickActionListScreen(GuiTheme theme, String title, List<QuickAction> list, Runnable onChanged) {
        super(theme, title);

        this.list = list;
        this.onChanged = onChanged;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        table.clear();

        table.add(theme.label("Each row is one button/slice."));
        table.row();
        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        for (QuickAction action : List.copyOf(list)) {
            String label = action.label.isBlank() ? "(unnamed)" : action.label;
            table.add(theme.label(label)).expandCellX();
            table.add(theme.label(summary(action)));

            WHorizontalList buttons = table.add(theme.horizontalList()).expandCellX().widget();

            buttons.add(theme.button("Edit")).right().widget().action = () -> {
                QuickActionEditScreen screen = new QuickActionEditScreen(theme, action);
                screen.onClosed(() -> {
                    onChanged.run();
                    initTable();
                });

                mc.setScreen(screen);
            };

            buttons.add(theme.minus()).right().widget().action = () -> {
                list.remove(action);
                onChanged.run();

                initTable();
            };

            table.row();
        }

        if (!list.isEmpty()) table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.button("Add Action")).expandX().widget().action = () -> {
            QuickAction action = new QuickAction();
            action.label = "Action " + (list.size() + 1);

            list.add(action);
            onChanged.run();

            QuickActionEditScreen screen = new QuickActionEditScreen(theme, action);
            screen.onClosed(() -> {
                onChanged.run();
                initTable();
            });

            mc.setScreen(screen);
        };
    }

    private String summary(QuickAction action) {
        if (action.type == QuickAction.Type.Submenu) return "Submenu: " + action.children.size() + " entries";

        String base = switch (action.type) {
            case Command -> "Command: " + (action.command.isBlank() ? "(empty)" : action.command);
            case Module -> "Module: " + (action.moduleId.isBlank() ? "(none)" : action.moduleId);
            case Submenu -> ""; // unreachable, handled above
        };

        base += " [" + action.closeBehavior + "]";

        return base;
    }
}
