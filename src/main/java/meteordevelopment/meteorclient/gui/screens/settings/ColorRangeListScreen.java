/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WQuad;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WDoubleEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.settings.ColorRangeEntry;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.List;

/**
 * Editor for a "By Value Ranges" color mode's bands - each entry colors any value at or below its
 * own upTo, picked in ascending order (see ColorRangeEntry.resolve), e.g. "below 10 red, 10-20
 * yellow, above that green" - three entries with upTo 9.999../10/1000000 (the last one's upTo just
 * needs to be at least as high as any value you'll ever actually see).
 */
public class ColorRangeListScreen extends WindowScreen {
    private final List<ColorRangeEntry> ranges;
    private final double sliderMax;

    private WTable table;

    public ColorRangeListScreen(GuiTheme theme, String title, List<ColorRangeEntry> ranges, double sliderMax) {
        super(theme, title + " - Color Ranges");

        this.ranges = ranges;
        this.sliderMax = sliderMax;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        table.clear();

        table.add(theme.label("Each entry colors any value at or below its own \"Up To\" - sorted low to high below, regardless of the order you add them in. Anything above the highest one just uses that top entry's color.", 400));
        table.row();

        if (!ranges.isEmpty()) {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        for (ColorRangeEntry entry : ColorRangeEntry.sorted(ranges)) {
            SettingColor color = entry.color;

            WHorizontalList list = table.add(theme.horizontalList()).expandCellX().widget();

            WQuad quad = list.add(theme.quad(color)).widget();

            WIntEdit r = list.add(theme.intEdit(color.r, 0, 255, 0, 255, false)).widget();
            WIntEdit g = list.add(theme.intEdit(color.g, 0, 255, 0, 255, false)).widget();
            WIntEdit b = list.add(theme.intEdit(color.b, 0, 255, 0, 255, false)).widget();
            WIntEdit a = list.add(theme.intEdit(color.a, 0, 255, 0, 255, false)).widget();

            Runnable updateColor = () -> {
                color.r = r.get();
                color.g = g.get();
                color.b = b.get();
                color.a = a.get();
                color.validate();

                quad.color.set(color);
            };

            r.action = updateColor;
            g.action = updateColor;
            b.action = updateColor;
            a.action = updateColor;

            table.add(theme.label("Up To:"));

            WDoubleEdit upTo = table.add(theme.doubleEdit(entry.upTo, -1000000, 1000000, 0, sliderMax, 2, false)).widget();
            upTo.action = () -> entry.upTo = upTo.get();

            WHorizontalList actions = table.add(theme.horizontalList()).expandCellX().widget();

            actions.add(theme.minus()).right().widget().action = () -> {
                ranges.remove(entry);

                table.clear();
                initTable();
            };

            table.row();
        }

        if (!ranges.isEmpty()) table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.button("Add Range")).expandCellX().widget().action = () -> {
            double nextUpTo = ranges.isEmpty() ? sliderMax / 2 : ColorRangeEntry.sorted(ranges).get(ranges.size() - 1).upTo + 1;
            ranges.add(new ColorRangeEntry(nextUpTo, new SettingColor(255, 255, 255, 255)));

            table.clear();
            initTable();
        };
    }
}
