/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.QuickAction;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.misc.QuickActions;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

public class QuickActionsScreen extends WindowScreen {
    private final QuickActions module;

    public QuickActionsScreen(GuiTheme theme, QuickActions module) {
        super(theme, "Quick Actions");

        this.module = module;
    }

    @Override
    public void initWidgets() {
        boolean any = false;

        for (QuickAction action : module.getActions()) {
            if (action.type == QuickAction.Type.Module) {
                Module target = Modules.get().get(action.moduleId.trim());
                if (target == null) continue;

                // Reuses the exact module-row widget the Modules screen itself uses - same on/off
                // color and same left-click-toggle/right-click-settings behavior as the real Modules
                // screen. It shows the module's own title, not this action's Label.
                add(theme.module(target)).expandX();
                any = true;
            }
            else {
                String commands = action.command.trim();
                if (commands.isEmpty()) continue;

                String label = action.label.isBlank() ? commands : action.label;
                boolean shouldClose = action.closeBehavior == QuickAction.CloseBehavior.Close;

                WButton button = theme.button(label);
                button.action = () -> {
                    for (String command : commands.split(";")) {
                        String trimmed = command.trim();
                        if (!trimmed.isEmpty()) ChatUtils.sendPlayerMsg(trimmed);
                    }

                    if (shouldClose) meteordevelopment.meteorclient.MeteorClient.mc.setScreen(null);
                };
                add(button).expandX();
                any = true;
            }
        }

        if (!any) add(theme.label("No actions configured - add some in the Quick Actions module's settings.", 400));
    }

    @Override
    public void close() {
        // Normally Escape steps back to whichever screen opened this one (e.g. the inventory, if
        // opened from its own QA button there) - Exit On Escape skips that and goes straight back
        // to gameplay instead, same as pressing Escape from the inventory screen itself would.
        if (module.exitOnEscape()) parent = null;

        super.close();
    }
}
