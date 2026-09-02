/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared queue between NoiseNotif (pushes matched text) and ReaderNotifHud (renders it) - decoupled
 * since a Module and a HudElement aren't otherwise wired together. Single-threaded (render/tick
 * thread only), so a plain ArrayList is fine.
 *
 * Duration is per-entry (set by the ReaderRule that pushed it), not a global/HUD-element setting -
 * different rules may want their match to stay on screen for different lengths of time.
 */
public class ReaderNotifQueue {
    public record Entry(Text text, long addedAt, long durationMs) {
    }

    private static final List<Entry> entries = new ArrayList<>();

    private ReaderNotifQueue() {
    }

    public static void push(Text text, long durationMs) {
        entries.add(new Entry(text, System.currentTimeMillis(), durationMs));
    }

    public static List<Entry> get() {
        return entries;
    }

    public static void prune() {
        long now = System.currentTimeMillis();
        entries.removeIf(e -> now - e.addedAt() > e.durationMs());
    }

    public static void clear() {
        entries.clear();
    }
}
