/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.screens.QuickActionsScreen;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.QuickAction;
import meteordevelopment.meteorclient.settings.QuickActionListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;

import java.util.ArrayList;
import java.util.List;

/**
 * A small custom menu of buttons you define yourself - inspired by FancyMenu's custom-button-with-
 * custom-action idea, but for in-game use. Each entry in Actions (its own list editor, see
 * QuickActionListScreen/QuickActionEditScreen) is one button: a label, and either a module to toggle
 * or one or more chat/client commands to run when it's clicked (so it can do anything a typed
 * command can - toggle other things via ".command", run server commands, etc.), plus its own Close
 * On Click override. Opens with its own keybind, and optionally adds a button to the pause menu too.
 */
public class QuickActions extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> openKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("open-key")
        .description("Opens the quick actions menu.")
        .defaultValue(Keybind.none())
        .action(this::openIfActive)
        .build()
    );

    private final Setting<Boolean> pauseMenuButton = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-menu-button")
        .description("Adds a button to the pause menu (Esc) that opens the quick actions menu.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> pauseMenuButtonText = sgGeneral.add(new StringSetting.Builder()
        .name("pause-menu-button-text")
        .description("Text on the pause menu button.")
        .defaultValue("Quick Actions")
        .visible(pauseMenuButton::get)
        .build()
    );

    private final Setting<Boolean> inventoryButton = sgGeneral.add(new BoolSetting.Builder()
        .name("inventory-button")
        .description("Adds a small \"QA\" button above the off hand slot in your own inventory screen that opens the quick actions menu.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> exitOnEscape = sgGeneral.add(new BoolSetting.Builder()
        .name("exit-on-escape")
        .description("Pressing Escape on the quick actions menu exits straight back to gameplay, instead of stepping back to whatever screen opened it (e.g. your inventory, if opened from the QA button there).")
        .defaultValue(false)
        .build()
    );

    public boolean exitOnEscape() {
        return exitOnEscape.get();
    }

    private final Setting<List<QuickAction>> actions = sgGeneral.add(new QuickActionListSetting.Builder()
        .name("actions")
        .description("The buttons shown in the Quick Actions menu - each with its own label, a command to run (or a module to toggle), and its own Close On Click (purely per-action, no overall setting for it).")
        .defaultValue(new ArrayList<>())
        .build()
    );

    public QuickActions() {
        super(Categories.Misc, "quick-actions", "A menu of custom buttons you define, each toggling a module or running a command - opens with its own keybind, and optionally from the pause menu.", "qa", "macro-menu", "button-menu");
    }

    public List<QuickAction> getActions() {
        return actions.get();
    }

    public boolean pauseMenuButtonEnabled() {
        return isActive() && pauseMenuButton.get();
    }

    public boolean inventoryButtonEnabled() {
        return isActive() && inventoryButton.get();
    }

    public String pauseMenuButtonText() {
        return pauseMenuButtonText.get();
    }

    public void openIfActive() {
        if (!isActive()) return;
        open();
    }

    public void open() {
        mc.setScreen(new QuickActionsScreen(GuiThemes.get(), this));
    }
}
