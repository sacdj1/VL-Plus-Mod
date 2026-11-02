/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.world;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.RenderScoreboardEvent;
import meteordevelopment.meteorclient.utils.PreInit;
import net.minecraft.scoreboard.ScoreboardEntry;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the current scoreboard sidebar's title + lines available anywhere, independent of any
 * particular module's active state - same reasoning as MeteorStarscript's own boss bar cache
 * (server.bossbar): RenderScoreboardEvent fires every frame the sidebar is actually showing,
 * regardless of who's listening, so there's no reason to make every feature that wants this
 * (Discord Presence's zone/corruption readouts, an Arena HUD, etc.) register its own listener and
 * re-walk the scoreboard entries separately.
 * <p>
 * Lines are in the order the server sends them, NOT necessarily the on-screen display order
 * (vanilla's own sidebar sorts by score) - if a feature needs a specific line, match by content
 * (a keyword or regex), not by index.
 */
public class ScoreboardUtils {
    private static String title = "";
    private static List<String> lines = List.of();

    private ScoreboardUtils() {
    }

    @PreInit
    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(new Listener());
    }

    public static String title() {
        return title;
    }

    public static List<String> lines() {
        return lines;
    }

    /** The first line matching the given predicate (checked against the plain, color-code-stripped text), or null if none match. */
    public static String findLine(Predicate<String> matcher) {
        for (String line : lines) {
            if (matcher.test(line)) return line;
        }

        return null;
    }

    /** The first capture group of the first line the given pattern matches anywhere in, or null if no line matches. */
    public static String findGroup(Pattern pattern) {
        for (String line : lines) {
            Matcher m = pattern.matcher(line);
            if (m.find() && m.groupCount() >= 1) return m.group(1);
        }

        return null;
    }

    private static final class Listener {
        @EventHandler
        private void onRenderScoreboard(RenderScoreboardEvent event) {
            title = event.objective.getDisplayName().getString();

            List<String> newLines = new ArrayList<>();
            for (ScoreboardEntry entry : event.objective.getScoreboard().getScoreboardEntries(event.objective)) {
                if (entry.hidden()) continue;
                newLines.add(entry.name().getString());
            }

            lines = newLines;
        }
    }
}
