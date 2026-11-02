/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.EventProfiler;
import meteordevelopment.orbit.EventHandler;

import java.util.List;

/**
 * Times every orbit event listener in the whole mod (one per module/HUD element method - see
 * LambdaListenerMixin) and periodically reports the worst offenders to chat, for tracking down lag
 * without guessing which module is responsible. Purely additive - existing modules need no changes
 * to be profiled. Zero overhead while this is off (LambdaListenerMixin's hot path is a single
 * volatile boolean read when EventProfiler.active is false).
 */
public class ResourceProfiler extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> reportInterval = sgGeneral.add(new IntSetting.Builder()
        .name("report-interval")
        .description("How often, in seconds, to print the worst offenders to chat. Each report window resets the counters, so every report is just that window's usage, not a running total.")
        .defaultValue(10)
        .min(2)
        .sliderRange(2, 60)
        .build()
    );

    private final Setting<Integer> topCount = sgGeneral.add(new IntSetting.Builder()
        .name("top-count")
        .description("How many of the worst offenders to list each report.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Double> minTotalMs = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-total-ms")
        .description("Skips anything that used less than this many milliseconds total over the report window - keeps trivial listeners out of the report.")
        .defaultValue(1.0)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private long lastReportMs;

    public ResourceProfiler() {
        super(Categories.Debug, "resource-profiler", "Times every module's/HUD element's event handler and periodically reports the worst offenders to chat - for tracking down lag.", "profiler", "perf", "lag-finder");
    }

    @Override
    public void onActivate() {
        EventProfiler.reset();
        EventProfiler.active = true;
        lastReportMs = System.currentTimeMillis();
    }

    @Override
    public void onDeactivate() {
        EventProfiler.active = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        long now = System.currentTimeMillis();
        if (now - lastReportMs < reportInterval.get() * 1000L) return;

        report();
        EventProfiler.reset();
        lastReportMs = now;
    }

    private void report() {
        List<EventProfiler.Stat> stats = EventProfiler.snapshot();

        if (stats.isEmpty()) {
            info("Nothing recorded this window.");
            return;
        }

        info("Top offenders over the last %ds:", reportInterval.get());

        int shown = 0;
        for (EventProfiler.Stat stat : stats) {
            if (shown >= topCount.get()) break;

            double totalMs = stat.totalNanos() / 1_000_000.0;
            if (totalMs < minTotalMs.get()) break;

            double avgUs = (stat.totalNanos() / (double) stat.calls()) / 1000.0;
            info("  %s: %.2fms total, %d calls, %.1fus avg", stat.target().getSimpleName(), totalMs, stat.calls(), avgUs);
            shown++;
        }

        if (shown == 0) info("  (everything was under Min Total MS this window)");
    }
}
