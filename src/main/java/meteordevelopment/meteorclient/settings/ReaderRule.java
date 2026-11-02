/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.utils.misc.VLSounds;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A single "watch this text source for this pattern, then fire these notifications" rule for
 * NoiseNotif's Reader Notif feature. Deliberately a plain POJO edited via raw widgets (like
 * AmbienceRegion), not a mini Settings instance - the field count doesn't need the extra machinery.
 */
public class ReaderRule {
    public enum SoundMode {
        Default,   // NoiseNotif's own shared sound/custom file settings
        Registered, // this rule's own pick from the registered sound list
        CustomFile  // this rule's own uploaded .ogg file, via its claimed VLSounds rule-sound slot
    }

    /** Title and subtitle are checked separately (not concatenated into one blob) - Both matches if EITHER one matches on its own. */
    public enum TitleWatch {
        Off,
        Title,
        Subtitle,
        Both
    }

    public String name = "Rule";
    public boolean enabled = true;

    public enum QualityComparator {
        Under,
        UnderOrEqual,
        Equal,
        OverOrEqual,
        Over
    }

    // Sources
    public boolean watchChat = true;
    public boolean watchBossBar = false;
    public boolean watchScoreboard = false;
    public TitleWatch titleWatch = TitleWatch.Off;

    // Quality - a completely different kind of "source" from the text-stream ones above: instead
    // of pattern-matching incoming text, this polls your current main-hand weapon and chest/legs
    // armor's own Quality % (via ItemInfo) every tick and fires when it crosses the threshold -
    // Pattern/Regex below are irrelevant while this is on, matched() always returns false for text.
    public boolean watchQuality = false;
    public QualityComparator qualityComparator = QualityComparator.UnderOrEqual;
    public double qualityThreshold = 100;

    // Match
    public String pattern = "";
    public boolean useRegex = false;

    // Notifications
    public boolean notifySound = true;
    public boolean notifyToast = false;
    public boolean notifySystem = false;
    public boolean notifyHudText = false;
    public boolean notifyTts = false;
    public double hudDuration = 4.0; // seconds - how long this rule's match stays on the Reader Notif HUD element

    // How long this rule stays silent after matching before it's allowed to fire again - without
    // this, a rule watching Title/Scoreboard (see NoiseNotif's own per-frame change-detection)
    // could only ever re-trigger once the text changed to something ELSE first; truly identical
    // repeat text (e.g. the same boss warning shown again unchanged) never re-triggered at all,
    // reported as "sometimes doesn't trigger" and "re-triggers too slowly on repeat text".
    public double cooldownSeconds = 2.0;

    public transient long lastTriggeredMs = 0;

    // Sound - only meaningful while notifySound is on. Default reuses NoiseNotif's own single
    // shared sound (matches every rule's old behavior before per-rule sound existed). Registered
    // and CustomFile give this rule its own independent sound instead.
    public SoundMode soundMode = SoundMode.Default;
    public Identifier registeredSound = null;
    public String customSoundPath = "";
    public int customSoundSlot = -1;

    /** Claims a VLSounds rule-sound-pool slot for this rule if it doesn't already have one. Returns false if the pool is exhausted. */
    public boolean ensureCustomSoundSlot() {
        if (customSoundSlot >= 0) return true;

        customSoundSlot = VLSounds.claimRuleSoundSlot();
        return customSoundSlot >= 0;
    }

    /** Releases this rule's claimed slot (if any) back to the pool - call when the rule is deleted, or no longer wants CustomFile mode. */
    public void releaseCustomSoundSlot() {
        if (customSoundSlot < 0) return;

        VLSounds.releaseRuleSoundSlot(customSoundSlot);
        customSoundSlot = -1;
    }

    private transient Pattern compiled;
    private transient String compiledSource;

    public boolean matchesQuality(double value) {
        if (!enabled || !watchQuality) return false;

        return switch (qualityComparator) {
            case Under -> value < qualityThreshold;
            case UnderOrEqual -> value <= qualityThreshold;
            case Equal -> value == qualityThreshold;
            case OverOrEqual -> value >= qualityThreshold;
            case Over -> value > qualityThreshold;
        };
    }

    public boolean matches(String text) {
        if (!enabled || pattern.isEmpty()) return false;

        if (useRegex) {
            if (compiled == null || !pattern.equals(compiledSource)) {
                try {
                    compiled = Pattern.compile(pattern);
                    compiledSource = pattern;
                } catch (PatternSyntaxException e) {
                    return false;
                }
            }

            return compiled.matcher(text).find();
        }

        return text.toLowerCase().contains(pattern.toLowerCase());
    }

    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("name", name);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("watchChat", watchChat);
        tag.putBoolean("watchBossBar", watchBossBar);
        tag.putBoolean("watchScoreboard", watchScoreboard);
        tag.putString("titleWatch", titleWatch.name());
        tag.putBoolean("watchQuality", watchQuality);
        tag.putString("qualityComparator", qualityComparator.name());
        tag.putDouble("qualityThreshold", qualityThreshold);
        tag.putString("pattern", pattern);
        tag.putBoolean("useRegex", useRegex);
        tag.putBoolean("notifySound", notifySound);
        tag.putBoolean("notifyToast", notifyToast);
        tag.putBoolean("notifySystem", notifySystem);
        tag.putBoolean("notifyHudText", notifyHudText);
        tag.putBoolean("notifyTts", notifyTts);
        tag.putDouble("hudDuration", hudDuration);
        tag.putDouble("cooldownSeconds", cooldownSeconds);
        tag.putString("soundMode", soundMode.name());
        if (registeredSound != null) tag.putString("registeredSound", registeredSound.toString());
        tag.putString("customSoundPath", customSoundPath);
        tag.putInt("customSoundSlot", customSoundSlot);

        return tag;
    }

    public static ReaderRule fromTag(NbtCompound tag) {
        ReaderRule rule = new ReaderRule();

        rule.name = tag.getString("name");
        rule.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        rule.watchChat = tag.getBoolean("watchChat");
        rule.watchBossBar = tag.getBoolean("watchBossBar");
        rule.watchScoreboard = tag.getBoolean("watchScoreboard");

        if (tag.contains("titleWatch")) {
            try {
                rule.titleWatch = TitleWatch.valueOf(tag.getString("titleWatch"));
            } catch (IllegalArgumentException e) {
                rule.titleWatch = TitleWatch.Off;
            }
        } else {
            // Legacy save from before title/subtitle were split - the old boolean's "on" state
            // matched the concatenated title+subtitle blob, closest equivalent is Both.
            rule.titleWatch = tag.getBoolean("watchTitle") ? TitleWatch.Both : TitleWatch.Off;
        }

        rule.watchQuality = tag.getBoolean("watchQuality");
        try {
            rule.qualityComparator = tag.contains("qualityComparator") ? QualityComparator.valueOf(tag.getString("qualityComparator")) : QualityComparator.UnderOrEqual;
        } catch (IllegalArgumentException e) {
            rule.qualityComparator = QualityComparator.UnderOrEqual;
        }
        rule.qualityThreshold = tag.contains("qualityThreshold") ? tag.getDouble("qualityThreshold") : 100;

        rule.pattern = tag.getString("pattern");
        rule.useRegex = tag.getBoolean("useRegex");
        rule.notifySound = tag.getBoolean("notifySound");
        rule.notifyToast = tag.getBoolean("notifyToast");
        rule.notifySystem = tag.getBoolean("notifySystem");
        rule.notifyHudText = tag.getBoolean("notifyHudText");
        rule.notifyTts = tag.getBoolean("notifyTts");
        rule.hudDuration = tag.contains("hudDuration") ? tag.getDouble("hudDuration") : 4.0;
        rule.cooldownSeconds = tag.contains("cooldownSeconds") ? tag.getDouble("cooldownSeconds") : 2.0;

        try {
            rule.soundMode = tag.contains("soundMode") ? SoundMode.valueOf(tag.getString("soundMode")) : SoundMode.Default;
        } catch (IllegalArgumentException e) {
            rule.soundMode = SoundMode.Default;
        }
        rule.registeredSound = tag.contains("registeredSound") ? Identifier.tryParse(tag.getString("registeredSound")) : null;
        rule.customSoundPath = tag.getString("customSoundPath");
        rule.customSoundSlot = tag.contains("customSoundSlot") ? tag.getInt("customSoundSlot") : -1;

        return rule;
    }
}
