/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backing store for the Resource Profiler module - accumulates how much time every orbit event
 * listener (one per module/HUD element method, keyed by its owning class) spends in call(), fed by
 * LambdaListenerMixin. `active` is a bare static field rather than a lookup through Modules so the
 * mixin's hot path (runs on EVERY event dispatch in the whole mod, several times per frame) costs a
 * single volatile read while off, instead of a module-registry lookup on every single event.
 */
public class EventProfiler {
    public static volatile boolean active = false;

    public record Stat(Class<?> target, long totalNanos, long calls) {}

    private static final Map<Class<?>, long[]> data = new ConcurrentHashMap<>();

    public static void record(Class<?> target, long nanos) {
        data.compute(target, (k, v) -> {
            if (v == null) v = new long[2];
            v[0] += nanos;
            v[1]++;
            return v;
        });
    }

    public static void reset() {
        data.clear();
    }

    /** Snapshot sorted by total time descending - heaviest first. */
    public static List<Stat> snapshot() {
        List<Stat> list = new ArrayList<>();
        for (Map.Entry<Class<?>, long[]> entry : data.entrySet()) {
            list.add(new Stat(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }
        list.sort((a, b) -> Long.compare(b.totalNanos(), a.totalNanos()));
        return list;
    }
}
