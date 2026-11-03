/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.WeaponModelEntry;

import java.util.List;

/**
 * Per-weapon CustomModelData values for Weapon Models' four ability states. The weapon field uses
 * the exact same matcher convention as Animations: an item id (minecraft:bow) or "name:Doom
 * Launcher" for a display-name match. -1 in any state field means "don't override that state".
 *
 * Each weapon can also have Tier Overrides - its own four state values for a specific tier (per
 * ItemInfo's own tier/rarity detection - plain "Tier N", "Tier N Enhancer", or "Tier N Unique Item"
 * lore lines), for e.g. giving a Tier 7 bow a fancier glow than a Tier 2 one. The Unique checkbox
 * matches that tier's separate Unique-rarity overlay specifically - a Tier 4 override and a Tier 4
 * Unique override are two independent rows, since Unique is a different rarity, not a recolor of
 * the same tier. A tier override field left at -1 falls back to the weapon's base field above.
 *
 * Manually-entered numbers for now - matching whatever values your resource pack's own item model
 * "overrides" predicates actually use. A picker that reads those values straight out of your loaded
 * resource packs (so you don't have to look them up yourself) is planned as a follow-up, not yet
 * wired in here.
 */
public class WeaponModelListScreen extends WindowScreen {
    private final Setting<List<WeaponModelEntry>> setting;

    private WTable table;

    public WeaponModelListScreen(GuiTheme theme, Setting<List<WeaponModelEntry>> setting) {
        super(theme, "Weapon Models");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        table.clear();

        table.add(theme.label("Weapon: an item id (minecraft:bow) or \"name:Doom Launcher\" for a display-name match. -1 = no override for that state.", 420));
        table.row();
        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        for (WeaponModelEntry entry : List.copyOf(setting.get())) {
            WHorizontalList header = table.add(theme.horizontalList()).expandCellX().widget();

            WTextBox weapon = header.add(theme.textBox(entry.weapon)).expandCellX().widget();
            weapon.action = () -> {
                entry.weapon = weapon.get();
                setting.onChanged();
            };

            header.add(theme.minus()).widget().action = () -> {
                setting.get().remove(entry);
                setting.onChanged();

                table.clear();
                initTable();
            };

            table.row();

            modelDataField(table, "Idle", entry.idle, v -> entry.idle = v);
            modelDataField(table, "Activate", entry.activate, v -> entry.activate = v);
            modelDataField(table, "Active", entry.active, v -> entry.active = v);
            modelDataField(table, "Deactivate", entry.deactivate, v -> entry.deactivate = v);

            table.add(theme.label("Tier Overrides (-1 falls back to the weapon's own fields above)", 400));
            table.row();

            for (WeaponModelEntry.TierOverride override : List.copyOf(entry.tierOverrides)) {
                WHorizontalList tierHeader = table.add(theme.horizontalList()).expandCellX().widget();

                tierHeader.add(theme.label("Tier"));
                WIntEdit tier = tierHeader.add(theme.intEdit(override.tier, 0, Integer.MAX_VALUE, true)).widget();
                tier.action = () -> {
                    override.tier = tier.get();
                    setting.onChanged();
                };

                tierHeader.add(theme.label("Unique"));
                var unique = tierHeader.add(theme.checkbox(override.unique)).widget();
                unique.action = () -> {
                    override.unique = unique.checked;
                    setting.onChanged();
                };

                tierHeader.add(theme.minus()).widget().action = () -> {
                    entry.tierOverrides.remove(override);
                    setting.onChanged();

                    table.clear();
                    initTable();
                };

                table.row();

                modelDataField(table, "Idle", override.idle, v -> override.idle = v);
                modelDataField(table, "Activate", override.activate, v -> override.activate = v);
                modelDataField(table, "Active", override.active, v -> override.active = v);
                modelDataField(table, "Deactivate", override.deactivate, v -> override.deactivate = v);
            }

            table.add(theme.button("Add Tier Override")).expandCellX().widget().action = () -> {
                entry.tierOverrides.add(new WeaponModelEntry.TierOverride());
                setting.onChanged();

                table.clear();
                initTable();
            };

            table.row();
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        table.add(theme.button("Add Weapon")).expandCellX().widget().action = () -> {
            setting.get().add(new WeaponModelEntry());
            setting.onChanged();

            table.clear();
            initTable();
        };
    }

    private interface IntSetter {
        void set(int value);
    }

    private void modelDataField(WTable table, String label, int value, IntSetter set) {
        table.add(theme.label(label));

        WIntEdit edit = table.add(theme.intEdit(value, -1, Integer.MAX_VALUE, true)).widget();
        edit.action = () -> {
            set.set(edit.get());
            setting.onChanged();
        };

        table.row();
    }
}
