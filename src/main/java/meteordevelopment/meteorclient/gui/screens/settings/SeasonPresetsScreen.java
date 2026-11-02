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
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.settings.AmbienceSeasonColors;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Four fixed season color slots - independent of the main override settings (see
 * Ambience.currentSeasonColors()), not a snapshot of them. Save the current look's colors into one
 * or preview one on demand, with no name to type (unlike the general Ambience Presets list).
 */
public class SeasonPresetsScreen extends WindowScreen {
    private final Ambience ambience;

    private WTable table;

    public SeasonPresetsScreen(GuiTheme theme, Ambience ambience) {
        super(theme, "Season Colors");

        this.ambience = ambience;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        table.clear();

        table.add(theme.label("An independent color layer - Auto Season (in the main settings) applies whichever of these matches the real-world calendar date, underneath any manual override.", 400));
        table.row();

        if (ambience.isPreviewingSeason()) {
            table.add(theme.label("Currently previewing a season regardless of the calendar.", 400));
            table.add(theme.button("Stop Previewing")).expandCellX().widget().action = () -> {
                ambience.stopPreviewingSeason();
                initTable();
            };
            table.row();

            blendPreviewRow();
        }

        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        row("Spring", ambience.springColors);
        row("Summer", ambience.summerColors);
        row("Autumn", ambience.autumnColors);
        row("Winter", ambience.winterColors);
    }

    // Only shown while previewing - lets you also preview a fade partway toward a second season
    // (e.g. "what does 50% Autumn -> Winter look like"), without waiting for Fade Between Seasons'
    // own calendar-driven blend to actually get there for real.
    private void blendPreviewRow() {
        table.add(theme.label("Blend toward:"));

        WHorizontalList list = table.add(theme.horizontalList()).expandCellX().widget();

        blendTargetButton(list, "None", null);
        blendTargetButton(list, "Spring", ambience.springColors.get());
        blendTargetButton(list, "Summer", ambience.summerColors.get());
        blendTargetButton(list, "Autumn", ambience.autumnColors.get());
        blendTargetButton(list, "Winter", ambience.winterColors.get());

        table.row();

        if (ambience.getPreviewBlendTarget() != null) {
            table.add(theme.label("Blend Amount (%):"));

            WIntEdit amount = table.add(theme.intEdit(Math.round(ambience.getPreviewBlendFactor() * 100), 0, 100, 0, 100, false)).expandCellX().widget();
            amount.action = () -> ambience.setPreviewBlendFactor(amount.get() / 100f);

            table.row();
        }
    }

    private void blendTargetButton(WHorizontalList list, String label, AmbienceSeasonColors target) {
        list.add(theme.button(label)).right().widget().action = () -> {
            ambience.setPreviewBlendTarget(target);
            initTable();
        };
    }

    private void row(String name, Setting<AmbienceSeasonColors> setting) {
        AmbienceSeasonColors colors = setting.get();

        table.add(theme.label(name));
        table.add(theme.label(colors.saved ? "Saved" : "Empty")).expandCellX();

        WHorizontalList list = table.add(theme.horizontalList()).expandCellX().widget();

        list.add(theme.button("Save Current")).right().widget().action = () -> {
            ambience.captureCurrentIntoSeason(colors);
            setting.onChanged();

            initTable();
        };

        list.add(theme.button("Preview")).right().widget().action = () -> ambience.previewSeasonColors(colors);

        // One single screen for everything else - the six flat colors (sky/cloud/lightning/lava)
        // as extra rows up top (via BlockColorMapScreen's headerRows hook), then the same per-block
        // editor Global Blocks/Regions/Per-Biome Colors already use below that (individual blocks,
        // or the Override Entire Season/Grass/Foliage/Water Color sentinel rows). Save Current
        // above only ever touches the flat colors plus the three Grass/Foliage/Water sentinels -
        // anything else added here (e.g. just oak leaves, or a hand-picked sky color) is entirely
        // up to this screen.
        list.add(theme.button("Colors (" + colors.blocks.size() + ")")).right().widget().action = () -> {
            BlockColorMapScreen screen = new BlockColorMapScreen(theme, name, "Season", colors.blocks, ambience::reload,
                block -> !colors.disabledBlocks.contains(block),
                (block, enabled) -> {
                    if (enabled) colors.disabledBlocks.remove(block);
                    else colors.disabledBlocks.add(block);
                },
                false,
                headerTable -> flatColorRows(headerTable, colors));
            screen.onClosed(() -> {
                setting.onChanged();
                initTable();
            });

            mc.setScreen(screen);
        };

        table.row();
    }

    private void flatColorRows(WTable headerTable, AmbienceSeasonColors colors) {
        flatColorRow(headerTable, "Overworld Sky", colors, colors.overworldSky, () -> colors.overworldSkyEnabled, v -> colors.overworldSkyEnabled = v);
        flatColorRow(headerTable, "Nether Sky", colors, colors.netherSky, () -> colors.netherSkyEnabled, v -> colors.netherSkyEnabled = v);
        flatColorRow(headerTable, "End Sky", colors, colors.endSky, () -> colors.endSkyEnabled, v -> colors.endSkyEnabled = v);
        flatColorRow(headerTable, "Cloud", colors, colors.cloud, () -> colors.cloudEnabled, v -> colors.cloudEnabled = v);
        flatColorRow(headerTable, "Lightning", colors, colors.lightning, () -> colors.lightningEnabled, v -> colors.lightningEnabled = v);
        flatColorRow(headerTable, "Lava", colors, colors.lava, () -> colors.lavaEnabled, v -> colors.lavaEnabled = v);
    }

    // enabled: off by default (same as Ambience's own Custom Sky/Cloud/etc. Color toggles) - this
    // one channel does nothing until switched on, regardless of whatever color is picked for it.
    private void flatColorRow(WTable headerTable, String label, AmbienceSeasonColors colors, SettingColor color, java.util.function.BooleanSupplier getEnabled, java.util.function.Consumer<Boolean> setEnabled) {
        headerTable.add(theme.label(label)).expandCellX();

        var enabled = headerTable.add(theme.checkbox(getEnabled.getAsBoolean())).right().widget();
        enabled.action = () -> {
            setEnabled.accept(enabled.checked);
            colors.saved = true;
            ambience.reload();
        };

        WQuad quad = headerTable.add(theme.quad(color)).right().widget();

        headerTable.add(theme.button(GuiRenderer.EDIT)).right().widget().action = () -> {
            ColorSetting set = new ColorSetting(label, "", new SettingColor(color), settingColor -> {
                color.set(settingColor);
                quad.color.set(color);
                setEnabled.accept(true);
                colors.saved = true;
                ambience.reload();
            }, null, null);
            set.set(color);

            mc.setScreen(new ColorSettingScreen(theme, set));
        };

        headerTable.row();
    }
}
