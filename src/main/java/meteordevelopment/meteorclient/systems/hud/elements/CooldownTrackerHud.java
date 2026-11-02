/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks a fixed set of named ability/item cooldowns by watching chat for the exact messages the
 * server sends when each one triggers - the same detection technique Ventureland Utils' own
 * cooldown HUD uses (Scroll of Life, Revenge, Resurrection), reimplemented here so it renders
 * through Meteor's own HUD system and sits alongside everything else instead of a separate
 * overlay. Not a generic rule-based tracker (see Alerts' Reader Notif for that pattern, watching
 * arbitrary regexes) - each entry's trigger text is hardcoded to match this server's exact
 * messages, same as the mod it's replacing. Adding another tracked item means adding another
 * entry here in code (trigger text/regex + a ticks-remaining field + its own settings block).
 */
public class CooldownTrackerHud extends HudElement {

    @Override
    public boolean hasOwnBackground() {
        return true;
    }

    public static final HudElementInfo<CooldownTrackerHud> INFO = new HudElementInfo<>(Hud.GROUP, "cooldown-tracker", "Tracks Scroll of Life/Revenge/Resurrection cooldowns from chat, the same detection Ventureland Utils' own HUD uses.", CooldownTrackerHud::new);

    private static final Pattern REVENGE_PATTERN = Pattern.compile("Revenge is now on cooldown against mobs for (\\d+)s");
    private static final String SCROLL_OF_LIFE_MESSAGE = "Scroll of life saves you from death";
    private static final String RESURRECTION_SUFFIX = "revived.";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScrollOfLife = settings.createGroup("Scroll of Life");
    private final SettingGroup sgRevenge = settings.createGroup("Revenge");
    private final SettingGroup sgResurrection = settings.createGroup("Resurrection");

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text scale.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderRange(0.5, 3)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Text shadow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> spacing = sgGeneral.add(new IntSetting.Builder()
        .name("spacing")
        .description("Vertical space between entries, in pixels (before Scale).")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    // Scroll of Life

    private final Setting<Boolean> scrollOfLifeEnabled = sgScrollOfLife.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Tracks Scroll of Life's cooldown - starts counting down when chat shows \"" + SCROLL_OF_LIFE_MESSAGE + "\".")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> scrollOfLifeName = sgScrollOfLife.add(new StringSetting.Builder()
        .name("display-name")
        .description("Name shown before the remaining time.")
        .defaultValue("Scroll of Life")
        .visible(scrollOfLifeEnabled::get)
        .build()
    );

    private final Setting<Integer> scrollOfLifeDuration = sgScrollOfLife.add(new IntSetting.Builder()
        .name("cooldown-seconds")
        .description("Cooldown duration in seconds - the chat message doesn't include this, so it has to be set to match the server's real value (Ventureland Utils' own default is 300).")
        .defaultValue(300)
        .min(1)
        .sliderRange(1, 1200)
        .visible(scrollOfLifeEnabled::get)
        .build()
    );

    private final Setting<SettingColor> scrollOfLifeColor = sgScrollOfLife.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of this entry's text.")
        .defaultValue(new SettingColor(255, 85, 255))
        .visible(scrollOfLifeEnabled::get)
        .build()
    );

    // Revenge

    private final Setting<Boolean> revengeEnabled = sgRevenge.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Tracks Revenge's cooldown - starts counting down when chat shows \"Revenge is now on cooldown against mobs for <n>s\" (the duration is read straight from that message, no setting needed).")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> revengeName = sgRevenge.add(new StringSetting.Builder()
        .name("display-name")
        .description("Name shown before the remaining time.")
        .defaultValue("Revenge")
        .visible(revengeEnabled::get)
        .build()
    );

    private final Setting<SettingColor> revengeColor = sgRevenge.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of this entry's text.")
        .defaultValue(new SettingColor(170, 0, 0))
        .visible(revengeEnabled::get)
        .build()
    );

    // Resurrection

    private final Setting<Boolean> resurrectionEnabled = sgResurrection.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Tracks Resurrection's cooldown - starts counting down on a two-word chat message ending in \"" + RESURRECTION_SUFFIX + "\" (e.g. a player name followed by that word).")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> resurrectionName = sgResurrection.add(new StringSetting.Builder()
        .name("display-name")
        .description("Name shown before the remaining time.")
        .defaultValue("Resurrection")
        .visible(resurrectionEnabled::get)
        .build()
    );

    private final Setting<Integer> resurrectionDuration = sgResurrection.add(new IntSetting.Builder()
        .name("cooldown-seconds")
        .description("Cooldown duration in seconds - the chat message doesn't include this, so it has to be set to match the server's real value (Ventureland Utils' own default is 100).")
        .defaultValue(100)
        .min(1)
        .sliderRange(1, 1200)
        .visible(resurrectionEnabled::get)
        .build()
    );

    private final Setting<SettingColor> resurrectionColor = sgResurrection.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of this entry's text.")
        .defaultValue(new SettingColor(255, 85, 85))
        .visible(resurrectionEnabled::get)
        .build()
    );

    private int scrollOfLifeTicks = 0;
    private int revengeTicks = 0;
    private int resurrectionTicks = 0;

    public CooldownTrackerHud() {
        super(INFO);

        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!isActive()) return;

        // ReceiveMessageEvent fires from inside the vanilla chat-receiving pipeline and is
        // Cancellable - an uncaught exception here has nothing to do with cancelling, but a plain
        // handler crash escaping into that pipeline is not something to risk over a cooldown
        // tracker. Nothing below should realistically throw (the regex already guarantees only
        // digits reach parseInt), but the whole thing is wrapped just in case some unexpected chat
        // content manages to hit an edge case.
        try {
            String text = event.getMessage().getString().trim();
            if (text.isEmpty()) return;

            if (scrollOfLifeEnabled.get() && text.equals(SCROLL_OF_LIFE_MESSAGE)) {
                scrollOfLifeTicks = scrollOfLifeDuration.get() * 20;
                return;
            }

            if (revengeEnabled.get()) {
                Matcher matcher = REVENGE_PATTERN.matcher(text);
                if (matcher.matches()) {
                    revengeTicks = Integer.parseInt(matcher.group(1)) * 20;
                    return;
                }
            }

            if (resurrectionEnabled.get()) {
                String[] parts = text.split(" ");
                if (parts.length == 2 && parts[1].equals(RESURRECTION_SUFFIX)) {
                    resurrectionTicks = resurrectionDuration.get() * 20;
                }
            }
        } catch (Exception ignored) {
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

        if (scrollOfLifeTicks > 0) scrollOfLifeTicks--;
        if (revengeTicks > 0) revengeTicks--;
        if (resurrectionTicks > 0) resurrectionTicks--;
    }

    private static String formatTime(int ticks) {
        int seconds = (ticks + 19) / 20; // round up to the nearest second
        if (seconds >= 60) return String.format("%d:%02d", seconds / 60, seconds % 60);
        return seconds + "s";
    }

    @Override
    public void render(HudRenderer renderer) {
        List<String> lines = new ArrayList<>();
        List<Color> lineColors = new ArrayList<>();

        if (isInEditor()) {
            if (scrollOfLifeEnabled.get()) {
                lines.add(scrollOfLifeName.get() + ": 5:00");
                lineColors.add(scrollOfLifeColor.get());
            }
            if (revengeEnabled.get()) {
                lines.add(revengeName.get() + ": 10s");
                lineColors.add(revengeColor.get());
            }
            if (resurrectionEnabled.get()) {
                lines.add(resurrectionName.get() + ": 1:40");
                lineColors.add(resurrectionColor.get());
            }
        }
        else {
            if (scrollOfLifeEnabled.get() && scrollOfLifeTicks > 0) {
                lines.add(scrollOfLifeName.get() + ": " + formatTime(scrollOfLifeTicks));
                lineColors.add(scrollOfLifeColor.get());
            }
            if (revengeEnabled.get() && revengeTicks > 0) {
                lines.add(revengeName.get() + ": " + formatTime(revengeTicks));
                lineColors.add(revengeColor.get());
            }
            if (resurrectionEnabled.get() && resurrectionTicks > 0) {
                lines.add(resurrectionName.get() + ": " + formatTime(resurrectionTicks));
                lineColors.add(resurrectionColor.get());
            }
        }

        double textHeight = renderer.textHeight(shadow.get(), scale.get());
        double lineHeight = textHeight + spacing.get() * scale.get();
        double maxWidth = 1;

        for (int i = 0; i < lines.size(); i++) {
            double ty = y + i * lineHeight;
            renderer.text(lines.get(i), x, ty, lineColors.get(i), shadow.get(), scale.get());
            maxWidth = Math.max(maxWidth, renderer.textWidth(lines.get(i), shadow.get(), scale.get()));
        }

        double totalHeight = lines.isEmpty() ? textHeight : lines.size() * lineHeight - spacing.get() * scale.get();
        setSize(maxWidth, totalHeight);
    }
}
