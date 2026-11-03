/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WQuad;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WDoubleEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.settings.WeightedColorEntry;
import meteordevelopment.meteorclient.settings.WeightedColorEntry.BlendMode;
import meteordevelopment.meteorclient.settings.WeightedColorEntry.Mode;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.NbtCompound;

import java.util.List;

public class WeightedColorListScreen extends WindowScreen {
    private final List<WeightedColorEntry> colors;

    private WTable table;

    public WeightedColorListScreen(GuiTheme theme, List<WeightedColorEntry> colors, String blockName) {
        super(theme, blockName + " - Colors");

        this.colors = colors;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private static String blendLabel(BlendMode mode) {
        return switch (mode) {
            case None -> "No Blend";
            case SameBlock -> "Blend: Same Block";
            case AnyBlock -> "Blend: Any Block";
        };
    }

    private static BlendMode nextBlendMode(BlendMode mode) {
        return switch (mode) {
            case None -> BlendMode.SameBlock;
            case SameBlock -> BlendMode.AnyBlock;
            case AnyBlock -> BlendMode.None;
        };
    }

    private static String modeLabel(Mode mode) {
        return switch (mode) {
            case Replace -> "Replace";
            case Colorize -> "Colorize";
            case HueShift -> "Hue Shift";
        };
    }

    private static Mode nextMode(Mode mode) {
        return switch (mode) {
            case Replace -> Mode.Colorize;
            case Colorize -> Mode.HueShift;
            case HueShift -> Mode.Replace;
        };
    }

    private void initTable() {
        table.clear();

        if (colors.size() > 1) {
            table.add(theme.label("Multiple colors are picked at random per block, weighted by the numbers below.", 400));
            table.row();
        }

        for (WeightedColorEntry entry : List.copyOf(colors)) {
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

            table.add(theme.label("Weight:"));

            WIntEdit weight = table.add(theme.intEdit(entry.weight, 1, 100, 1, 100, false)).widget();
            weight.action = () -> entry.weight = weight.get();

            WHorizontalList actions = table.add(theme.horizontalList()).expandCellX().widget();

            actions.add(theme.button(blendLabel(entry.blend))).right().widget().action = () -> {
                entry.blend = nextBlendMode(entry.blend);

                table.clear();
                initTable();
            };

            actions.add(theme.button(modeLabel(entry.mode))).right().widget().action = () -> {
                entry.mode = nextMode(entry.mode);

                table.clear();
                initTable();
            };

            actions.add(theme.button(GuiRenderer.COPY)).right().widget().action = () ->
                NbtUtils.toClipboard("Color", entry.toTag());

            actions.add(theme.button(GuiRenderer.PASTE)).right().widget().action = () -> {
                NbtCompound clipboard = NbtUtils.fromClipboard(entry.toTag());
                if (clipboard == null) return;

                WeightedColorEntry pasted = WeightedColorEntry.fromTag(clipboard);
                entry.color = pasted.color;
                entry.weight = pasted.weight;
                entry.blend = pasted.blend;
                entry.mode = pasted.mode;
                entry.hueShiftSpeed = pasted.hueShiftSpeed;
                entry.hueShiftRange = pasted.hueShiftRange;

                table.clear();
                initTable();
            };

            actions.add(theme.button("Duplicate")).right().widget().action = () -> {
                WeightedColorEntry duplicate = new WeightedColorEntry(new SettingColor(color.r, color.g, color.b, color.a), entry.weight, entry.blend, entry.mode);
                duplicate.hueShiftSpeed = entry.hueShiftSpeed;
                duplicate.hueShiftRange = entry.hueShiftRange;
                colors.add(colors.indexOf(entry) + 1, duplicate);

                table.clear();
                initTable();
            };

            actions.add(theme.minus()).right().widget().action = () -> {
                colors.remove(entry);

                table.clear();
                initTable();
            };

            table.row();

            if (entry.mode == Mode.HueShift) {
                table.add(theme.label("Hue Shift Speed"));
                WDoubleEdit hueSpeed = table.add(theme.doubleEdit(entry.hueShiftSpeed, 0.1, 10)).widget();
                hueSpeed.action = () -> entry.hueShiftSpeed = hueSpeed.get();

                table.add(theme.label("Range"));
                WDoubleEdit hueRange = table.add(theme.doubleEdit(entry.hueShiftRange, 0, 180)).widget();
                hueRange.action = () -> entry.hueShiftRange = hueRange.get();

                table.row();
            }
        }

        if (!colors.isEmpty()) table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.button("Add Color")).expandCellX().widget().action = () -> {
            colors.add(new WeightedColorEntry(new SettingColor(255, 255, 255, 255), 100));

            table.clear();
            initTable();
        };
    }
}
