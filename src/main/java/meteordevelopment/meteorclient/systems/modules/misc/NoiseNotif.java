/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.render.RenderBossBarEvent;
import meteordevelopment.meteorclient.events.render.RenderScoreboardEvent;
import meteordevelopment.meteorclient.events.render.TitleSetEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.screens.settings.ReaderRuleListScreen;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

    public enum AbilityUsedTrigger {
        XpBarEdge,
        RightClickThenKey
    }

    public enum RageAlertMode {
        None,
        Max,
        SpecificValue
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgVolume = settings.createGroup("Volume (Ability Ready)");
    private final SettingGroup sgVolumeUsed = settings.createGroup("Volume (Ability Used)");
    private final SettingGroup sgRage = settings.createGroup("Rage Alert");
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
        .description("Which sound to play when ready. Picking more than one plays a random one from the list each time, for variety. Also used by Reader Notif's Sound channel. Ignored if Use Custom Sound File is on.")
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

    // General (ability used) - the opposite moment from ability-ready above: the instant the XP
    // bar jumps back UP from ready, meaning the ability was actually triggered/consumed. Registered
    // sounds only for now, unlike the ready sound's custom-file option - that needs its own
    // SoundLoaderMixin slot (see VLSounds.CUSTOM_ALERT), not added yet.

    private final Setting<Boolean> abilityUsedEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("ability-used-enabled")
        .description("Plays a sound the instant your ability is actually used, separate from the ready sound above.")
        .defaultValue(false)
        .build()
    );

    private final Setting<AbilityUsedTrigger> abilityUsedTrigger = sgGeneral.add(new EnumSetting.Builder<AbilityUsedTrigger>()
        .name("ability-used-trigger")
        .description("How to detect that your ability was used. Xp Bar Edge: the moment the XP bar jumps back up from ready - works if abilities are tracked via the XP bar, same convention as the ready sound above. Right Click Then Key: fires when you right-click and then press a separate key shortly after - for servers where using an ability is right-click followed by a key rather than an XP-bar-tracked cooldown.")
        .defaultValue(AbilityUsedTrigger.XpBarEdge)
        .visible(abilityUsedEnabled::get)
        .build()
    );

    private final Setting<Keybind> abilityUsedComboKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("ability-used-combo-key")
        .description("The key to press shortly after right-clicking to count as using your ability, when Ability Used Trigger above is Right Click Then Key.")
        .defaultValue(Keybind.none())
        .visible(() -> abilityUsedEnabled.get() && abilityUsedTrigger.get() == AbilityUsedTrigger.RightClickThenKey)
        .build()
    );

    private final Setting<Integer> abilityUsedComboWindow = sgGeneral.add(new IntSetting.Builder()
        .name("ability-used-combo-window")
        .description("How many ticks after right-clicking the combo key above still counts, when Ability Used Trigger above is Right Click Then Key.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 100)
        .visible(() -> abilityUsedEnabled.get() && abilityUsedTrigger.get() == AbilityUsedTrigger.RightClickThenKey)
        .build()
    );

    private final Setting<List<SoundEvent>> usedSound = sgGeneral.add(new SoundEventListSetting.Builder()
        .name("used-sound")
        .description("Which sound to play when your ability is used. Picking more than one plays a random one from the list each time, for variety.")
        .defaultValue(List.of(SoundEvents.ENTITY_ARROW_SHOOT))
        .visible(abilityUsedEnabled::get)
        .build()
    );

    private final Setting<Void> testUsedSound = sgGeneral.add(new ButtonSetting.Builder()
        .name("test-used-sound")
        .description("Plays the currently configured ability-used sound once, at the pitch/volume settings above.")
        .buttonText("Test Used Sound")
        .action(this::playUsedSound)
        .visible(abilityUsedEnabled::get)
        .build()
    );

    private final Setting<Double> volume = sgVolume.add(new DoubleSetting.Builder()
        .name("volume")
        .description("Volume multiplier for the ability-ready sound and Reader Notif's Sound channel, on top of your normal Master Volume slider. 100% plays at the sound's own normal volume.")
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

    // Volume (Ability Used) - independent from the ready sound's own volume above, so the two can
    // be balanced separately (e.g. a loud "ready" alert but a quieter "used" confirmation).

    private final Setting<Double> usedVolume = sgVolumeUsed.add(new DoubleSetting.Builder()
        .name("volume")
        .description("Volume multiplier for the ability-used sound, on top of your normal Master Volume slider. 100% plays at the sound's own normal volume.")
        .defaultValue(1.0)
        .range(0, 2)
        .sliderRange(0, 2)
        .visible(() -> !this.usedOverrideVolume.get())
        .build()
    );

    private final Setting<Boolean> usedOverrideVolume = sgVolumeUsed.add(new BoolSetting.Builder()
        .name("override-volume")
        .description("Plays at the volume below independently of your current Master Volume slider, instead of being scaled down by it (and instead of the plain Volume setting above). Can't force full loudness if Master Volume is muted or very low - Minecraft's audio engine still applies Master Volume as a hard ceiling underneath this, so it's a best-effort compensation, not a true bypass.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> usedOverrideVolumeAmount = sgVolumeUsed.add(new DoubleSetting.Builder()
        .name("override-volume-amount")
        .description("Target playback volume - 100% means as loud as your sliders would allow at their own max.")
        .defaultValue(1.0)
        .range(0, 1)
        .sliderRange(0, 1)
        .visible(usedOverrideVolume::get)
        .build()
    );

    // Rage Alert - watches boss bars (RenderBossBarEvent.BossText already carries the real
    // ClientBossBar, whose getPercent() gives the actual 0-1 fill, not just its title text) for one
    // whose name contains the keyword below, and fires a sound the moment its fill crosses the
    // chosen point - independent of Reader Notif below, which only ever matches boss bar TEXT.

    private final Setting<RageAlertMode> rageAlertMode = sgRage.add(new EnumSetting.Builder<RageAlertMode>()
        .name("rage-alert-mode")
        .description("None: off. Max: plays a sound the moment the rage boss bar reaches its max (100%). Specific Value: plays a sound the moment it reaches the percent below instead - useful to get a heads-up before it's actually full.")
        .defaultValue(RageAlertMode.None)
        .build()
    );

    private final Setting<String> rageBarKeyword = sgRage.add(new StringSetting.Builder()
        .name("rage-bar-keyword")
        .description("Text (case-insensitive, anywhere in the name) used to tell the rage boss bar apart from any other boss bar currently shown, e.g. a boss fight's own health bar.")
        .defaultValue("Rage")
        .visible(() -> rageAlertMode.get() != RageAlertMode.None)
        .build()
    );

    private final Setting<Integer> rageThreshold = sgRage.add(new IntSetting.Builder()
        .name("rage-threshold")
        .description("Percent of the rage bar at/above which the alert fires, when Rage Alert Mode above is Specific Value.")
        .defaultValue(90)
        .range(1, 100)
        .sliderRange(1, 100)
        .visible(() -> rageAlertMode.get() == RageAlertMode.SpecificValue)
        .build()
    );

    private final Setting<List<SoundEvent>> rageSound = sgRage.add(new SoundEventListSetting.Builder()
        .name("rage-sound")
        .description("Which sound to play when the rage alert fires. Picking more than one plays a random one from the list each time, for variety.")
        .defaultValue(List.of(SoundEvents.ENTITY_ENDER_DRAGON_GROWL))
        .visible(() -> rageAlertMode.get() != RageAlertMode.None)
        .build()
    );

    private final Setting<Double> rageVolume = sgRage.add(new DoubleSetting.Builder()
        .name("rage-volume")
        .description("Volume multiplier for the rage alert sound, on top of your normal Master Volume slider.")
        .defaultValue(1.0)
        .range(0, 2)
        .sliderRange(0, 2)
        .visible(() -> rageAlertMode.get() != RageAlertMode.None)
        .build()
    );

    private final Setting<Void> testRageSound = sgRage.add(new ButtonSetting.Builder()
        .name("test-rage-sound")
        .description("Plays the currently configured rage alert sound once.")
        .buttonText("Test Rage Sound")
        .action(this::playRageSound)
        .visible(() -> rageAlertMode.get() != RageAlertMode.None)
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

    // Rage Alert edge-detection state - keyed by the boss bar's own stable UUID so switching to a
    // DIFFERENT boss bar (even one that also happens to match the keyword) can't inherit stale
    // "was above threshold" state from whatever bar was tracked before it.
    private UUID lastRageBarUuid = null;
    private boolean wasRageAboveThreshold = false;

    // Right Click Then Key trigger state - a short window that opens on right-click and closes
    // either when the combo key is pressed (fires the sound) or the window expires unused.
    private boolean wasUseKeyPressed = false;
    private boolean wasComboKeyPressed = false;
    private int comboWindowTicksRemaining = 0;

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
        lastScoreboardText = null;
        wasUseKeyPressed = false;
        wasComboKeyPressed = false;
        comboWindowTicksRemaining = 0;
        lastRageBarUuid = null;
        wasRageAboveThreshold = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        checkQualityRules();

        float progress = mc.player.experienceProgress;

        // 0 = ready, matching AbilityCooldownHud/XPBarAdjust's convention on this server.
        if (abilityReadyEnabled.get() && lastProgress > 0.001f && progress <= 0.001f) playReadySound();

        if (abilityUsedEnabled.get()) {
            if (abilityUsedTrigger.get() == AbilityUsedTrigger.XpBarEdge) {
                // Opposite edge: bar jumps back up from ready = the ability was just used.
                if (lastProgress != -1 && lastProgress <= 0.001f && progress > 0.001f) playUsedSound();
            }
            else {
                boolean useKeyPressed = mc.options.useKey.isPressed();
                if (useKeyPressed && !wasUseKeyPressed) comboWindowTicksRemaining = abilityUsedComboWindow.get();
                wasUseKeyPressed = useKeyPressed;

                boolean comboKeyPressed = abilityUsedComboKey.get().isPressed();
                if (comboWindowTicksRemaining > 0) {
                    if (comboKeyPressed && !wasComboKeyPressed) {
                        playUsedSound();
                        comboWindowTicksRemaining = 0;
                    }
                    else comboWindowTicksRemaining--;
                }
                wasComboKeyPressed = comboKeyPressed;
            }
        }

        lastProgress = progress;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!readerEnabled.get()) return;

        checkRules(event.getMessage(), rule -> rule.watchChat);
    }

    @EventHandler
    private void onBossBarText(RenderBossBarEvent.BossText event) {
        checkRageAlert(event);

        if (!readerEnabled.get()) return;

        checkRules(event.name, rule -> rule.watchBossBar);
    }

    private void checkRageAlert(RenderBossBarEvent.BossText event) {
        if (rageAlertMode.get() == RageAlertMode.None || event.bossBar == null) return;

        String keyword = rageBarKeyword.get();
        if (keyword.isEmpty() || !event.name.getString().toLowerCase().contains(keyword.toLowerCase())) return;

        float target = rageAlertMode.get() == RageAlertMode.Max ? 1.0f : rageThreshold.get() / 100f;
        boolean above = event.bossBar.getPercent() >= target - 0.001f;

        UUID uuid = event.bossBar.getUuid();
        if (!uuid.equals(lastRageBarUuid)) {
            // First frame seeing this particular bar - seed the edge state silently instead of
            // firing immediately, in case it's already above the threshold the instant it appears
            // (e.g. rejoining mid-fight with rage already high).
            lastRageBarUuid = uuid;
            wasRageAboveThreshold = above;
            return;
        }

        if (above && !wasRageAboveThreshold) playRageSound();
        wasRageAboveThreshold = above;
    }

    // Scoreboard still only re-matches when its text actually changes (re-checking every rule every
    // frame regardless would be wasted work for a rule that's still on its own cooldown, see
    // ReaderRule.lastTriggeredMs/cooldownSeconds). Title/subtitle used to work the same way, but
    // that meant a rule could only ever re-fire once the text changed to something ELSE first - a
    // genuinely repeated title (the same warning sent again, unchanged) never re-triggered. It also
    // used to sample every RENDERED FRAME a title was on screen instead - relying on the per-rule
    // cooldown alone to avoid spam meant a title visible longer than that cooldown re-triggered a
    // second time near the end, during its own fade-out. Title/subtitle now hook the server's actual
    // set-title/set-subtitle calls (see TitleSetEvent) instead of sampling render frames, so this
    // fires exactly once per real renewal, never more, regardless of how long it stays on screen.
    private String lastScoreboardText = null;

    @EventHandler
    private void onTitleSet(TitleSetEvent event) {
        if (!readerEnabled.get()) return;

        String titleText = event.title != null ? event.title.getString() : "";
        String subtitleText = event.subtitle != null ? event.subtitle.getString() : "";

        checkTitleRules(titleText, subtitleText);
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

    /** Title and subtitle checked independently (see ReaderRule.TitleWatch) - Both matches if either one does, rather than concatenating them into one blob first. */
    private void checkTitleRules(String titleText, String subtitleText) {
        long now = System.currentTimeMillis();

        for (ReaderRule rule : List.copyOf(readerRules.get())) {
            if (rule.titleWatch == ReaderRule.TitleWatch.Off) continue;
            if (now - rule.lastTriggeredMs < rule.cooldownSeconds * 1000) continue;

            boolean watchTitle = rule.titleWatch == ReaderRule.TitleWatch.Title || rule.titleWatch == ReaderRule.TitleWatch.Both;
            boolean watchSubtitle = rule.titleWatch == ReaderRule.TitleWatch.Subtitle || rule.titleWatch == ReaderRule.TitleWatch.Both;

            String matchedPlain = null;
            if (watchTitle && rule.matches(titleText)) matchedPlain = titleText;
            else if (watchSubtitle && rule.matches(subtitleText)) matchedPlain = subtitleText;

            if (matchedPlain == null) continue;

            rule.lastTriggeredMs = now;
            dispatch(rule, Text.literal(matchedPlain));
        }
    }

    // Main-hand weapon plus chest/legs armor only - head/feet aren't worth tracking, same scope
    // ItemInfo's Durability Bar settled on for its own weapon/armor split.
    private static final net.minecraft.entity.EquipmentSlot[] QUALITY_WATCH_SLOTS = {
        null, // main hand - handled separately below, not a real EquipmentSlot lookup
        net.minecraft.entity.EquipmentSlot.CHEST,
        net.minecraft.entity.EquipmentSlot.LEGS
    };

    /** Quality rules don't watch a single text stream like every other source - they poll your current gear every tick instead, so this runs from onTick rather than from one of the text-event handlers above. */
    private void checkQualityRules() {
        boolean anyWatching = false;
        for (ReaderRule rule : readerRules.get()) {
            if (rule.enabled && rule.watchQuality) { anyWatching = true; break; }
        }
        if (!anyWatching) return;

        meteordevelopment.meteorclient.systems.modules.render.ItemInfo itemInfo = meteordevelopment.meteorclient.systems.modules.Modules.get().get(meteordevelopment.meteorclient.systems.modules.render.ItemInfo.class);
        if (itemInfo == null) return;

        for (net.minecraft.entity.EquipmentSlot slot : QUALITY_WATCH_SLOTS) {
            net.minecraft.item.ItemStack stack = slot == null ? mc.player.getMainHandStack() : mc.player.getEquippedStack(slot);
            if (stack.isEmpty()) continue;

            meteordevelopment.meteorclient.systems.modules.render.ItemInfo.QualityInfo info = itemInfo.getQualityInfo(stack);
            if (info == null || info.qualityPercent() == null) continue;

            checkQualityRulesAgainst(stack, slot, info.qualityPercent());
        }
    }

    private void checkQualityRulesAgainst(net.minecraft.item.ItemStack stack, net.minecraft.entity.EquipmentSlot slot, int qualityPercent) {
        long now = System.currentTimeMillis();

        for (ReaderRule rule : List.copyOf(readerRules.get())) {
            if (!rule.watchQuality) continue;
            if (!rule.matchesQualitySlot(slot)) continue;
            if (now - rule.lastTriggeredMs < rule.cooldownSeconds * 1000) continue;
            if (!rule.matchesQuality(qualityPercent)) continue;

            rule.lastTriggeredMs = now;
            dispatch(rule, Text.literal(stack.getName().getString() + " - Quality: " + qualityPercent + "%"));
        }
    }

    private void checkRules(String plain, java.util.function.Predicate<ReaderRule> sourceFilter) {
        long now = System.currentTimeMillis();

        for (ReaderRule rule : List.copyOf(readerRules.get())) {
            if (!sourceFilter.test(rule)) continue;
            if (now - rule.lastTriggeredMs < rule.cooldownSeconds * 1000) continue;
            if (!rule.matches(plain)) continue;

            rule.lastTriggeredMs = now;
            dispatch(rule, Text.literal(plain));
        }
    }

    private void checkRules(Text text, java.util.function.Predicate<ReaderRule> sourceFilter) {
        String plain = text.getString();
        long now = System.currentTimeMillis();

        for (ReaderRule rule : List.copyOf(readerRules.get())) {
            if (!sourceFilter.test(rule)) continue;
            if (now - rule.lastTriggeredMs < rule.cooldownSeconds * 1000) continue;
            if (!rule.matches(plain)) continue;

            rule.lastTriggeredMs = now;
            dispatch(rule, text);
        }
    }

    private void dispatch(ReaderRule rule, Text matchedText) {
        if (rule.notifySound) {
            SoundEvent soundEvent = resolveRuleSound(rule);
            if (soundEvent != null) playSound(soundEvent, volume.get() * rule.volume, overrideVolume.get(), overrideVolumeAmount.get());
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

        // com.mojang.text2speech.Narrator, not mc.getNarratorManager() - the latter checks the
        // vanilla Narrator accessibility OPTION first and no-ops if it's off, which would make this
        // silently do nothing for anyone who doesn't already have that turned on for unrelated
        // reasons. This talks to the OS-level TTS engine directly, independent of that setting.
        if (rule.notifyTts) com.mojang.text2speech.Narrator.getNarrator().say(rule.name + ": " + matchedText.getString(), false);
    }

    private void playReadySound() {
        SoundEvent soundEvent = notificationSound();
        if (soundEvent != null) playSound(soundEvent, volume.get(), overrideVolume.get(), overrideVolumeAmount.get());
    }

    private void playUsedSound() {
        SoundEvent soundEvent = pickRandom(usedSound.get());
        if (soundEvent != null) playSound(soundEvent, usedVolume.get(), usedOverrideVolume.get(), usedOverrideVolumeAmount.get());
    }

    private void playRageSound() {
        SoundEvent soundEvent = pickRandom(rageSound.get());
        if (soundEvent != null) playSound(soundEvent, rageVolume.get(), false, 0);
    }

    private SoundEvent notificationSound() {
        if (useCustomSoundFile.get()) return VLSounds.CUSTOM_ALERT;

        return pickRandom(sound.get());
    }

    /** Picking more than one sound in a SoundEventListSetting plays a random one each time, for variety - only the single entry is used when there's just one, same as before. */
    private SoundEvent pickRandom(List<SoundEvent> sounds) {
        if (sounds.isEmpty()) return null;
        if (sounds.size() == 1) return sounds.get(0);

        return sounds.get(ThreadLocalRandom.current().nextInt(sounds.size()));
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

    private void playSound(SoundEvent soundEvent, double volumeSetting, boolean overrideVolumeSetting, double overrideAmountSetting) {
        if (soundEvent == null || mc.player == null || mc.world == null) return;

        float volumeValue = (float) volumeSetting;

        if (overrideVolumeSetting) {
            float masterVolume = mc.options.getSoundVolume(SoundCategory.MASTER);
            volumeValue = masterVolume > 0.001f ? (float) (overrideAmountSetting / masterVolume) : 0f;
        }

        mc.world.playSoundFromEntity(mc.player, mc.player, soundEvent, SoundCategory.MASTER, volumeValue, pitch.get().floatValue());
    }
}
