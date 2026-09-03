/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.events.render;

import net.minecraft.scoreboard.ScoreboardObjective;

public class RenderScoreboardEvent {
    private static final RenderScoreboardEvent INSTANCE = new RenderScoreboardEvent();

    public ScoreboardObjective objective;

    public static RenderScoreboardEvent get(ScoreboardObjective objective) {
        INSTANCE.objective = objective;
        return INSTANCE;
    }
}
