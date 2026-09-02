/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import net.minecraft.nbt.NbtCompound;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A single "watch this text source for this pattern, then fire these notifications" rule for
 * NoiseNotif's Reader Notif feature. Deliberately a plain POJO edited via raw widgets (like
 * AmbienceRegion), not a mini Settings instance - the field count doesn't need the extra machinery.
 */
public class ReaderRule {
    public String name = "Rule";
    public boolean enabled = true;

    // Sources
    public boolean watchChat = true;
    public boolean watchBossBar = false;

    // Match
    public String pattern = "";
    public boolean useRegex = false;

    // Notifications
    public boolean notifySound = true;
    public boolean notifyToast = false;
    public boolean notifySystem = false;
    public boolean notifyHudText = false;
    public double hudDuration = 4.0; // seconds - how long this rule's match stays on the Reader Notif HUD element

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
        tag.putString("pattern", pattern);
        tag.putBoolean("useRegex", useRegex);
        tag.putBoolean("notifySound", notifySound);
        tag.putBoolean("notifyToast", notifyToast);
        tag.putBoolean("notifySystem", notifySystem);
        tag.putBoolean("notifyHudText", notifyHudText);
        tag.putDouble("hudDuration", hudDuration);

        return tag;
    }

    public static ReaderRule fromTag(NbtCompound tag) {
        ReaderRule rule = new ReaderRule();

        rule.name = tag.getString("name");
        rule.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        rule.watchChat = tag.getBoolean("watchChat");
        rule.watchBossBar = tag.getBoolean("watchBossBar");
        rule.pattern = tag.getString("pattern");
        rule.useRegex = tag.getBoolean("useRegex");
        rule.notifySound = tag.getBoolean("notifySound");
        rule.notifyToast = tag.getBoolean("notifyToast");
        rule.notifySystem = tag.getBoolean("notifySystem");
        rule.notifyHudText = tag.getBoolean("notifyHudText");
        rule.hudDuration = tag.contains("hudDuration") ? tag.getDouble("hudDuration") : 4.0;

        return rule;
    }
}
