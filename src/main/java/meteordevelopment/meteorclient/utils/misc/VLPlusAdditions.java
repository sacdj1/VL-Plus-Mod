/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc;

import meteordevelopment.meteorclient.systems.config.Config;

import java.util.Set;

/**
 * Marks module/HUD element titles so it's clear at a glance whether something is stock Meteor
 * Client, a stock module VL+ has modified/extended, or a module/HUD element VL+ added from
 * scratch. Symbols are baked into the title once at construction time (module/HudElementInfo
 * registration happens once, at startup), so toggling Config's "show origin symbols" setting off
 * takes effect immediately for anything constructed after the toggle, but already-built titles
 * need a restart to fully clear - a restart-required setting, same as several other Config options.
 */
public class VLPlusAdditions {
    public static final String METEOR_SYMBOL = "☄"; // comet - stock Meteor, untouched
    public static final String MODIFIED_SYMBOL = "✎"; // pencil - stock Meteor, modified/extended by VL+
    public static final String VLPLUS_SYMBOL = "✚"; // thick plus - added by VL+ from scratch

    // Module ids (Module.name) added from scratch.
    private static final Set<String> NEW_MODULES = Set.of(
        "hitboxes",
        "particle-color",
        "xp-bar-adjust",
        "noise-notif"
    );

    // Module ids (Module.name) that originate upstream but have been meaningfully modified/extended.
    private static final Set<String> MODIFIED_MODULES = Set.of(
        "disguise",
        "breadcrumbs",
        "ambience",
        "truesight"
    );

    // HudElementInfo ids (HudElementInfo.name) added from scratch.
    private static final Set<String> NEW_HUD_ELEMENTS = Set.of(
        "ability-cooldown",
        "xp-level",
        "reader-notif"
    );

    // HudElementInfo ids (HudElementInfo.name) that originate upstream but have been modified.
    private static final Set<String> MODIFIED_HUD_ELEMENTS = Set.of(
        "item"
    );

    private VLPlusAdditions() {
    }

    public static boolean symbolsEnabled() {
        try {
            Config config = Config.get();
            return config == null || config.showOriginSymbols.get();
        } catch (Exception e) {
            return true;
        }
    }

    public static String modulePrefix(String name) {
        if (!symbolsEnabled()) return "";

        if (NEW_MODULES.contains(name)) return VLPLUS_SYMBOL + " ";
        if (MODIFIED_MODULES.contains(name)) return MODIFIED_SYMBOL + " ";
        return METEOR_SYMBOL + " ";
    }

    public static String hudElementPrefix(String name) {
        if (!symbolsEnabled()) return "";

        if (NEW_HUD_ELEMENTS.contains(name)) return VLPLUS_SYMBOL + " ";
        if (MODIFIED_HUD_ELEMENTS.contains(name)) return MODIFIED_SYMBOL + " ";
        return METEOR_SYMBOL + " ";
    }
}
