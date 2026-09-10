/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.screens;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.VLPlusAdditions;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AddHudElementScreen extends WindowScreen {
    private final int x, y;
    private final WTextBox searchBar;

    private Object firstObject;

    public AddHudElementScreen(GuiTheme theme, int x, int y) {
        super(theme, "Add Hud element");

        this.x = x;
        this.y = y;

        searchBar = theme.textBox("");
        searchBar.action = () -> {
            clear();
            initWidgets();
        };

        enterAction = () -> runObject(firstObject);
    }

    @Override
    public void initWidgets() {
        firstObject = null;

        // Search bar
        add(searchBar).expandX();
        searchBar.setFocused(true);

        // Group infos - LinkedHashMap so section order stays pinned to the stable, alphabetical
        // order infos are encountered in below, instead of drifting between searches depending on
        // which subset of groups happens to survive the current filter (a plain HashMap's
        // iteration order isn't guaranteed stable across different key sets).
        Hud hud = Hud.get();
        Map<HudGroup, List<Item>> grouped = new LinkedHashMap<>();

        // Titles here are shown without their origin/experimental symbol prefixes (☄/✎/✚/⚠) - this
        // list's window width was overflowing for longer names once those symbols got stacked on
        // top, spilling text past the window's edge. The symbols stay everywhere else (the main
        // module/HUD element lists) - this is purely a display/search choice for this one picker.
        for (HudElementInfo<?> info : hud.infos.values()) {
            String baseTitle = VLPlusAdditions.stripOriginPrefix(info.title);

            if (info.hasPresets() && !searchBar.get().isEmpty()) {
                for (HudElementInfo<?>.Preset preset : info.presets) {
                    String title = baseTitle + "  -  " + preset.title;
                    if (Utils.searchTextDefault(title, searchBar.get(), false)) grouped.computeIfAbsent(info.group, hudGroup -> new ArrayList<>()).add(new Item(title, info.description, preset));
                }
            }
            else if (Utils.searchTextDefault(baseTitle, searchBar.get(), false)) grouped.computeIfAbsent(info.group, hudGroup -> new ArrayList<>()).add(new Item(baseTitle, info.description, info));
        }

        // Elements opening a preset picker (the " > " button, e.g. Text) get their own extra
        // screen instead of adding directly - pinned last within their group so they don't sit
        // wherever they happen to land alphabetically among the plain add-directly elements.
        for (List<Item> items : grouped.values()) {
            items.sort(Comparator.comparing(item -> item.object instanceof HudElementInfo<?> info && info.hasPresets()));
        }

        // Create widgets
        for (HudGroup group : grouped.keySet()) {
            WSection section = add(theme.section(group.title())).expandX().widget();

            for (Item item : grouped.get(group)) {
                WHorizontalList l = section.add(theme.horizontalList()).expandX().widget();

                WLabel title = l.add(theme.label(item.title)).widget();
                title.tooltip = item.description;

                if (item.object instanceof HudElementInfo.Preset preset) {
                    WPlus add = l.add(theme.plus()).expandCellX().right().widget();
                    add.action = () -> runObject(preset);

                    if (firstObject == null) firstObject = preset;
                }
                else {
                    HudElementInfo<?> info = (HudElementInfo<?>) item.object;

                    if (info.hasPresets()) {
                        WButton open = l.add(theme.button(" > ")).expandCellX().right().widget();
                        open.action = () -> runObject(info);
                    }
                    else {
                        WPlus add = l.add(theme.plus()).expandCellX().right().widget();
                        add.action = () -> runObject(info);
                    }

                    if (firstObject == null) firstObject = info;
                }
            }
        }
    }

    private void runObject(Object object) {
        if (object == null) return;
        if (object instanceof HudElementInfo.Preset preset) {
            Hud.get().add(preset, x, y);
            close();
        }
        else {
            HudElementInfo<?> info = (HudElementInfo<?>) object;

            if (info.hasPresets()) {
                HudElementPresetsScreen screen = new HudElementPresetsScreen(theme, info, x, y);
                screen.parent = parent;

                mc.setScreen(screen);
            }
            else {
                Hud.get().add(info, x, y);
                close();
            }
        }
    }

    @Override
    protected void onRenderBefore(DrawContext drawContext, float delta) {
        HudEditorScreen.renderElements(drawContext);
    }

    private record Item(String title, String description, Object object) {}
}
