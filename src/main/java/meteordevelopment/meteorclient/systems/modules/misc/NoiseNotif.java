/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.render.RenderBossBarEvent;
import meteordevelopment.meteorclient.events.render.RenderScoreboardEvent;
import meteordevelopment.meteorclient.events.render.RenderTitleEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.screens.settings.ReaderRuleListScreen;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.ReaderNotifQueue;
import meteordevelopment.meteorclient.utils.misc.SystemNotifier;
import meteordevelopment.meteorclient.utils.misc.VLSounds;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays a sound when an ability (tracked via the XP bar, same convention as AbilityCooldownHud/
 * XPBarAdjust: experienceProgress 0 = ready) becomes ready. Can use either a registered vanilla
 * sound event, or an arbitrary .ogg file picked from disk (SoundLoaderMixin intercepts loading for
 * VLSounds.CUSTOM_ALERT to stream whatever file is currently chosen, since Minecraft's sound system
 * otherwise only ever loads audio through the resource manager).
 *
 * Also includes Reader Notif: user-defined rules that watch chat, the boss bar, the scoreboard
 * sidebar and/or the title/subtitle for a text/regex pattern, and fire a sound/toast/system
 * notification/on-screen HUD text (ReaderNotifHud) when it matches. Scoreboard and title sources
 * match against the whole visible block of text at once (title+subtitle, or the sidebar's title
 * plus every visible line), not line-by-line.
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

    private final Setting<Boolean> abilityReadyEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("ability-ready-enabled")
        .description("Plays a sound when your ability (tracked via the XP bar) becomes ready. Independent of the rest of this module - turn this off to use Reader Notif and/or the other settings below without also getting a sound every time the XP bar hits 0.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useCustomSoundFile = sgGeneral.add(new BoolSetting.Builder()
        .name("use-custom-sound-file")
        .description("Plays an arbitrary .ogg file from disk instead of the registered sound below, for both the ability-ready sound and Reader Notif's Sound channel.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<SoundEvent>> sound = sgGeneral.add(new SoundEventListSetting.Builder()
        .name("sound")
        .description("Which sound to play when ready. Only the first sound picked here is used. Also used by Reader Notif's Sound channel. Ignored if Use Custom Sound File is on.")
        .defaultValue(List.of(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP))
        .visible(() -> !useCustomSoundFile.get())
        .build()
    );

    private final Setting<String> customSoundPath = sgGeneral.add(new StringSetting.Builder()
        .name("custom-sound-file")
        .description("Internal - path to the chosen .ogg file, set via the button below.")
        .visible(() -> false)
        .build()
    );

    private final Setting<Void> chooseCustomSoundFile = sgGeneral.add(new ButtonSetting.Builder()
        .name("choose-custom-sound-file")
        .description("Pick a .ogg file from disk to use as the notification sound.")
        .buttonText("Choose File...")
        .action(this::chooseCustomSoundFile)
        .visible(useCustomSoundFile::get)
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

    private final Setting<Void> testSound = sgGeneral.add(new ButtonSetting.Builder()
        .name("test-sound")
        .description("Plays the currently configured sound once (whichever one is active above - registered sound or custom file), at the pitch/volume settings above, so you can check it without waiting for a real trigger. Needs to be in a world.")
        .buttonText("Test Sound")
        .action(this::playReadySound)
        .build()
    );

    private final Setting<Double> volume = sgVolume.add(new DoubleSetting.Builder()
        .name("volume")
        .description("Volume multiplier for every sound this module plays (ability-ready and Reader Notif's Sound channel alike), on top of your normal Master Volume slider. 100% plays at the sound's own normal volume.")
        .defaultValue(1.0)
        .range(0, 2)
        .sliderRange(0, 2)
        .visible(() -> !this.overrideVolume.get())
        .build()
    );

    private final Setting<Boolean> overrideVolume = sgVolume.add(new BoolSetting.Builder()
        .name("override-volume")
        .description("Plays at the volume below independently of your current Master Volume slider, instead of being scaled down by it (and instead of the plain Volume setting above). Can't force full loudness if Master Volume is muted or very low - Minecraft's audio engine still applies Master Volume as a hard ceiling underneath this, so it's a best-effort compensation, not a true bypass.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> overrideVolumeAmount = sgVolume.add(new DoubleSetting.Builder()
        .name("override-volume-amount")
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
        .description("Watches chat, the boss bar, the scoreboard and/or the title against your rules below, firing notifications on a match.")
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
        super(Categories.Misc, "noise-notif", "Plays a sound when your ability (tracked via the XP bar) becomes ready, and/or watches chat/boss bar/scoreboard/title text for custom rules (Reader Notif).");

        // Display name only - keeping the internal id "noise-notif" so existing saved settings and
        // .noise-notif commands keep working unchanged.
        title = "Alerts";
    }

    /** Used by SoundLoaderMixin to resolve VLSounds.CUSTOM_ALERT's audio to this file. */
    public String getCustomSoundPath() {
        return customSoundPath.get();
    }

    private void chooseCustomSoundFile() {
        String path = chooseSoundFile();
        if (path != null) customSoundPath.set(path);
    }

    /** Opens a native "pick a .ogg file" dialog - shared by this module's own custom sound file picker and each ReaderRule's own (see ReaderRuleEditScreen). Returns null if the user cancelled. */
    public static String chooseSoundFile() {
        PointerBuffer filters = BufferUtils.createPointerBuffer(1);
        ByteBuffer oggFilter = MemoryUtil.memASCII("*.ogg");
        filters.put(oggFilter);
        filters.rewind();

        return TinyFileDialogs.tinyfd_openFileDialog("Choose Sound File", null, filters, "OGG files (*.ogg)", false);
    }

    @Override
    public void onActivate() {
        lastProgress = -1;
        lastTitleText = null;
        lastScoreboardText = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!abilityReadyEnabled.get() || mc.player == null) return;

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

    // Both of these render every frame the text is on screen, not just when it changes - track the
    // last seen plain text per source so a match only fires once per change, not once per frame.
    private String lastTitleText = null;
    private String lastScoreboardText = null;

    @EventHandler
    private void onRenderTitle(RenderTitleEvent event) {
        if (!readerEnabled.get()) return;

        StringBuilder sb = new StringBuilder(event.title.getString());
        if (event.subtitle != null) sb.append('\n').append(event.subtitle.getString());
        String text = sb.toString();

        if (text.equals(lastTitleText)) return;
        lastTitleText = text;

        checkRules(text, rule -> rule.watchTitle);
    }

    @EventHandler
    private void onRenderScoreboard(RenderScoreboardEvent event) {
        if (!readerEnabled.get()) return;

        StringBuilder sb = new StringBuilder(event.objective.getDisplayName().getString());
        for (ScoreboardEntry entry : event.objective.getScoreboard().getScoreboardEntries(event.objective)) {
            if (entry.hidden()) continue;
            sb.append('\n').append(entry.name().getString());
        }
        String text = sb.toString();

        if (text.equals(lastScoreboardText)) return;
        lastScoreboardText = text;

        checkRules(text, rule -> rule.watchScoreboard);
    }

    private void checkRules(String plain, java.util.function.Predicate<ReaderRule> sourceFilter) {
        for (ReaderRule rule : List.copyOf(readerRules.get())) {
            if (!sourceFilter.test(rule)) continue;
            if (!rule.matches(plain)) continue;

            dispatch(rule, Text.literal(plain));
        }
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
        if (rule.notifySound) {
            SoundEvent soundEvent = resolveRuleSound(rule);
            if (soundEvent != null) playSound(soundEvent);
        }

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
        SoundEvent soundEvent = notificationSound();
        if (soundEvent != null) playSound(soundEvent);
    }

    private SoundEvent notificationSound() {
        if (useCustomSoundFile.get()) return VLSounds.CUSTOM_ALERT;

        return sound.get().isEmpty() ? null : sound.get().get(0);
    }

    /** Which sound a specific rule's notification should actually play, per its own Sound Mode - Default falls back to this module's shared ability-ready sound above. */
    private SoundEvent resolveRuleSound(ReaderRule rule) {
        return switch (rule.soundMode) {
            case Default -> notificationSound();
            case Registered -> rule.registeredSound != null ? Registries.SOUND_EVENT.get(rule.registeredSound) : null;
            case CustomFile -> rule.ensureCustomSoundSlot() ? VLSounds.ruleSound(rule.customSoundSlot) : null;
        };
    }

    /** Used by SoundLoaderMixin to resolve a claimed rule-sound-pool slot back to whichever rule currently owns it and its chosen file. */
    public String getRuleSoundPath(int slot) {
        for (ReaderRule rule : readerRules.get()) {
            if (rule.soundMode == ReaderRule.SoundMode.CustomFile && rule.customSoundSlot == slot) return rule.customSoundPath;
        }

        return null;
    }

    private void playSound(SoundEvent soundEvent) {
        if (soundEvent == null || mc.player == null || mc.world == null) return;

        float volumeValue = volume.get().floatValue();

        if (overrideVolume.get()) {
            float masterVolume = mc.options.getSoundVolume(SoundCategory.MASTER);
            volumeValue = masterVolume > 0.001f ? (float) (overrideVolumeAmount.get() / masterVolume) : 0f;
        }

        mc.world.playSoundFromEntity(mc.player, mc.player, soundEvent, SoundCategory.MASTER, volumeValue, pitch.get().floatValue());
    }
}
