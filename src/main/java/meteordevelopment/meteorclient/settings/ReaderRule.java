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

    public String name = "Rule";
    public boolean enabled = true;

    // Sources
    public boolean watchChat = true;
    public boolean watchBossBar = false;
    public boolean watchScoreboard = false;
    public boolean watchTitle = false;

    // Match
    public String pattern = "";
    public boolean useRegex = false;

    // Notifications
    public boolean notifySound = true;
    public boolean notifyToast = false;
    public boolean notifySystem = false;
    public boolean notifyHudText = false;
    public double hudDuration = 4.0; // seconds - how long this rule's match stays on the Reader Notif HUD element

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
        tag.putBoolean("watchTitle", watchTitle);
        tag.putString("pattern", pattern);
        tag.putBoolean("useRegex", useRegex);
        tag.putBoolean("notifySound", notifySound);
        tag.putBoolean("notifyToast", notifyToast);
        tag.putBoolean("notifySystem", notifySystem);
        tag.putBoolean("notifyHudText", notifyHudText);
        tag.putDouble("hudDuration", hudDuration);
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
        rule.watchTitle = tag.getBoolean("watchTitle");
        rule.pattern = tag.getString("pattern");
        rule.useRegex = tag.getBoolean("useRegex");
        rule.notifySound = tag.getBoolean("notifySound");
        rule.notifyToast = tag.getBoolean("notifyToast");
        rule.notifySystem = tag.getBoolean("notifySystem");
        rule.notifyHudText = tag.getBoolean("notifyHudText");
        rule.hudDuration = tag.contains("hudDuration") ? tag.getDouble("hudDuration") : 4.0;

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
