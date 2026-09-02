/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.render.RenderBossBarEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.screens.settings.ReaderRuleListScreen;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.ReaderNotifQueue;
import meteordevelopment.meteorclient.utils.misc.SystemNotifier;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Plays a sound when an ability (tracked via the XP bar, same convention as AbilityCooldownHud/
 * XPBarAdjust: experienceProgress 0 = ready) becomes ready. Currently only supports vanilla
 * Minecraft sound events - playing an arbitrary .ogg file from a folder is a separate, more
 * involved follow-up (Minecraft's sound engine only plays audio registered through a resource
 * pack, so that needs a small dynamic resource pack built at runtime).
 *
 * Also includes Reader Notif: user-defined rules that watch chat and/or the boss bar for a text/
 * regex pattern, and fire a sound/toast/system notification/on-screen HUD text (ReaderNotifHud)
 * when it matches. Scoreboard and title text sources aren't wired up yet - a follow-up.
 */
public class NoiseNotif extends Module {
    public enum SystemFocusMode {
        Always,
        OnlyUnfocused,
        OnlyFocused
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgVolume = settings.createGroup("Volume");
    private final SettingGroup sgReader = settings.createGroup("Reader Notif");
    private final SettingGroup sgSystem = settings.createGroup("Reader Notif - System Notification");

    // General (ability ready)

    private final Setting<List<SoundEvent>> sound = sgGeneral.add(new SoundEventListSetting.Builder()
        .name("sound")
        .description("Which sound to play when ready. Only the first sound picked here is used. Also used by Reader Notif's Sound channel.")
        .defaultValue(List.of(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP))
        .build()
    );

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch")
        .description("Pitch of the notification sound.")
        .defaultValue(1.0)
        .range(0.5, 2.0)
        .sliderRange(0.5, 2.0)
        .build()
    );

    private final Setting<Boolean> overrideVolume = sgVolume.add(new BoolSetting.Builder()
        .name("override-volume")
        .description("Plays at the volume below independently of your current Master Volume slider, instead of being scaled down by it. Can't force full loudness if Master Volume is muted or very low - Minecraft's audio engine still applies Master Volume as a hard ceiling underneath this, so it's a best-effort compensation, not a true bypass.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> overrideVolumeAmount = sgVolume.add(new DoubleSetting.Builder()
        .name("volume")
        .description("Target playback volume - 100% means as loud as your sliders would allow at their own max.")
        .defaultValue(1.0)
        .range(0, 1)
        .sliderRange(0, 1)
        .visible(overrideVolume::get)
        .build()
    );

    // Reader Notif

    private final Setting<Boolean> readerEnabled = sgReader.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Watches chat and/or the boss bar against your rules below, firing notifications on a match.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<ReaderRule>> readerRules = sgReader.add(new ReaderRuleListSetting.Builder()
        .name("rules")
        .description("Internal - managed via the button below.")
        .visible(() -> false)
        .build()
    );

    private final Setting<Void> manageRules = sgReader.add(new ButtonSetting.Builder()
        .name("manage-rules")
        .description("Add, edit or remove rules.")
        .buttonText("Manage Rules")
        .screen(theme -> new ReaderRuleListScreen(theme, readerRules))
        .build()
    );

    // Reader Notif - System Notification

    private final Setting<SystemFocusMode> systemFocusMode = sgSystem.add(new EnumSetting.Builder<SystemFocusMode>()
        .name("focus-mode")
        .description("When the System Notification channel is allowed to actually fire, based on whether the game window currently has focus.")
        .defaultValue(SystemFocusMode.OnlyUnfocused)
        .build()
    );

    private float lastProgress = -1;

    public NoiseNotif() {
        super(Categories.Misc, "noise-notif", "Plays a sound when your ability (tracked via the XP bar) becomes ready, and/or watches chat/boss bar text for custom rules (Reader Notif).");

        // Display name only - keeping the internal id "noise-notif" so existing saved settings and
        // .noise-notif commands keep working unchanged.
        title = "Alerts";
    }

    @Override
    public void onActivate() {
        lastProgress = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        float progress = mc.player.experienceProgress;

        // 0 = ready, matching AbilityCooldownHud/XPBarAdjust's convention on this server.
        if (lastProgress > 0.001f && progress <= 0.001f) playReadySound();

        lastProgress = progress;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!readerEnabled.get()) return;

        checkRules(event.getMessage(), rule -> rule.watchChat);
    }

    @EventHandler
    private void onBossBarText(RenderBossBarEvent.BossText event) {
        if (!readerEnabled.get()) return;

        checkRules(event.name, rule -> rule.watchBossBar);
    }

    private void checkRules(Text text, java.util.function.Predicate<ReaderRule> sourceFilter) {
        String plain = text.getString();

        for (ReaderRule rule : List.copyOf(readerRules.get())) {
            if (!sourceFilter.test(rule)) continue;
            if (!rule.matches(plain)) continue;

            dispatch(rule, text);
        }
    }

    private void dispatch(ReaderRule rule, Text matchedText) {
        if (rule.notifySound) playSound(sound.get().isEmpty() ? null : sound.get().get(0));

        if (rule.notifyToast) {
            mc.getToastManager().add(new MeteorToast(Items.PAPER, rule.name, matchedText.getString()));
        }

        if (rule.notifySystem) {
            boolean focused = mc.isWindowFocused();
            boolean allowed = switch (systemFocusMode.get()) {
                case Always -> true;
                case OnlyUnfocused -> !focused;
                case OnlyFocused -> focused;
            };

            if (allowed) SystemNotifier.notify(rule.name, matchedText.getString());
        }

        if (rule.notifyHudText) ReaderNotifQueue.push(matchedText, (long) (rule.hudDuration * 1000));
    }

    private void playReadySound() {
        if (sound.get().isEmpty()) return;

        playSound(sound.get().get(0));
    }

    private void playSound(SoundEvent soundEvent) {
        if (soundEvent == null || mc.player == null || mc.world == null) return;

        float volume = 1.0f;

        if (overrideVolume.get()) {
            float masterVolume = mc.options.getSoundVolume(SoundCategory.MASTER);
            volume = masterVolume > 0.001f ? (float) (overrideVolumeAmount.get() / masterVolume) : 0f;
        }

        mc.world.playSoundFromEntity(mc.player, mc.player, soundEvent, SoundCategory.MASTER, volume, pitch.get().floatValue());
    }
}
