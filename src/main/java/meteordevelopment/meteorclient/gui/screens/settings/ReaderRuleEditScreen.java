/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WDoubleEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.ReaderRule;
import meteordevelopment.meteorclient.settings.SoundEventListSetting;
import meteordevelopment.meteorclient.systems.modules.misc.NoiseNotif;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ReaderRuleEditScreen extends WindowScreen {
    private final ReaderRule rule;
    private WTable table;

    public ReaderRuleEditScreen(GuiTheme theme, ReaderRule rule) {
        super(theme, "Edit Rule");

        this.rule = rule;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();

        buildRows();
    }

    // Rebuildable (rather than laid out once) so the Sound section below can show/hide its
    // mode-specific controls live as Notify With Sound / Sound Mode change, instead of always
    // showing every mode's controls at once regardless of which one is actually active.
    private void buildRows() {
        table.clear();

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
        addCheckboxRowRefresh(table, "Sound", rule.notifySound, v -> rule.notifySound = v);
        addCheckboxRow(table, "Toast", rule.notifyToast, v -> rule.notifyToast = v);
        addCheckboxRow(table, "System Notification", rule.notifySystem, v -> rule.notifySystem = v);
        addCheckboxRow(table, "HUD Text (Reader Notif element)", rule.notifyHudText, v -> rule.notifyHudText = v);

        table.add(theme.label("HUD Text duration (seconds)"));
        WDoubleEdit duration = table.add(theme.doubleEdit(rule.hudDuration, 0.5, 60)).expandX().widget();
        duration.action = () -> rule.hudDuration = duration.get();
        table.row();

        if (rule.notifySound) buildSoundSection();

        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.button("Back")).expandX().widget().action = this::close;
    }

    private void buildSoundSection() {
        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.label("Sound Mode"));
        var modeDropdown = table.add(theme.dropdown(rule.soundMode)).expandCellX().widget();
        modeDropdown.action = () -> {
            rule.soundMode = modeDropdown.get();
            if (rule.soundMode == ReaderRule.SoundMode.CustomFile) rule.ensureCustomSoundSlot();
            buildRows();
        };
        table.row();

        switch (rule.soundMode) {
            case Default -> {
                table.add(theme.label("Uses the Alerts module's own Sound setting above (Sound / Custom Sound File)."));
                table.row();
            }

            case Registered -> {
                table.add(theme.label("Sound"));

                String current = rule.registeredSound != null ? rule.registeredSound.getPath() : "None";
                WLabel soundLabel = table.add(theme.label(current)).expandCellX().widget();

                var select = table.add(theme.button("Select")).widget();
                select.action = () -> {
                    List<SoundEvent> initial = new ArrayList<>();
                    if (rule.registeredSound != null) {
                        SoundEvent event = Registries.SOUND_EVENT.get(rule.registeredSound);
                        if (event != null) initial.add(event);
                    }

                    SoundEventListSetting tempSetting = new SoundEventListSetting("sound", "Which sound this rule plays.", initial, sounds -> {
                        rule.registeredSound = sounds.isEmpty() ? null : sounds.get(0).getId();
                        soundLabel.set(rule.registeredSound != null ? rule.registeredSound.getPath() : "None");
                    }, null, null);
                    tempSetting.set(initial);

                    mc.setScreen(new SoundEventListSettingScreen(theme, tempSetting));
                };
                table.row();
            }

            case CustomFile -> {
                table.add(theme.label("Sound File"));

                String fileName = rule.customSoundPath.isEmpty() ? "None" : new File(rule.customSoundPath).getName();
                WLabel fileLabel = table.add(theme.label(fileName)).expandCellX().widget();

                var choose = table.add(theme.button("Choose File...")).widget();
                choose.action = () -> {
                    String path = NoiseNotif.chooseSoundFile();
                    if (path == null) return;

                    rule.customSoundPath = path;
                    fileLabel.set(new File(path).getName());
                };
                table.row();

                if (!rule.ensureCustomSoundSlot()) {
                    table.add(theme.label("No free custom sound slots left (32 max across all rules) - this rule will play no sound until another rule frees one up."));
                    table.row();
                }
            }
        }
    }

    // Matches the label-then-control, two-cell-per-row shape every other row in this table uses
    // (Name, Pattern, HUD Text duration) - mixing that with a single wide cell per row (as this
    // used to, via a nested WHorizontalList) left the table's per-column width calculation
    // inconsistent between rows, squishing everything into the narrow width the checkbox rows'
    // one cell implied instead of the width the label+textbox rows actually needed.
    private void addCheckboxRow(WTable table, String label, boolean value, java.util.function.Consumer<Boolean> onChanged) {
        table.add(theme.label(label));

        var checkbox = table.add(theme.checkbox(value)).expandCellX().right().widget();
        checkbox.action = () -> onChanged.accept(checkbox.checked);

        table.row();
    }

    // Same as addCheckboxRow, but rebuilds the whole screen on toggle - only needed for the one
    // checkbox (Sound) that something else's visibility (the Sound Mode section below) depends on.
    private void addCheckboxRowRefresh(WTable table, String label, boolean value, java.util.function.Consumer<Boolean> onChanged) {
        table.add(theme.label(label));

        var checkbox = table.add(theme.checkbox(value)).expandCellX().right().widget();
        checkbox.action = () -> {
            onChanged.accept(checkbox.checked);
            buildRows();
        };

        table.row();
    }
}
