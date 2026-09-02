/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.systems.modules.misc.NameProtect;
import net.minecraft.client.gui.DrawContext;

import java.util.Map;

public class DisguisedPlayersScreen extends WindowScreen {
    private final NameProtect nameProtect;

    public DisguisedPlayersScreen(GuiTheme theme, NameProtect nameProtect) {
        super(theme, "Disguised Players");

        this.nameProtect = nameProtect;
    }

    @Override
    public void initWidgets() {
        WTable table = add(theme.table()).expandX().widget();

        if (!nameProtect.otherPlayersEnabled()) {
            table.add(theme.label("Enable \"Other Players\" to disguise other players."));
            return;
        }

        nameProtect.refreshOtherPlayerNames();

        Map<String, String> names = nameProtect.getOtherPlayerNames();

        if (names.isEmpty()) {
            table.add(theme.label("No other players found."));
            return;
        }

        table.add(theme.label("Real Name"));
        table.add(theme.label("Disguised As"));
        table.row();

        nameProtect.withRealNames(() -> {
            for (Map.Entry<String, String> entry : names.entrySet()) {
                table.add(theme.label(entry.getKey())).expandCellX();
                table.add(theme.label(entry.getValue()));
                table.row();
            }
        });
    }

    // Labels store their text and get redrawn every frame, so suppression has to be active for
    // the whole render call, not just while initWidgets() builds the widgets - the global
    // TextVisitFactoryMixin hook that disguises text runs at draw time, not at construction time.
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        nameProtect.withRealNames(() -> super.render(context, mouseX, mouseY, delta));
    }
}
