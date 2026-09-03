/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.util.Set;

/**
 * Marks module/HUD element titles so it's clear at a glance whether something is stock Meteor
 * Client, a stock module VL+ has modified/extended, or a module/HUD element VL+ added from
 * scratch - plus a separate Experimental marker for specific settings that aren't fully reliable
 * yet, regardless of which of the three the module itself is.
 *
 * Prefixes are recomputed live (refreshAll(), wired to Config's onChanged) rather than baked in
 * once at construction - the earlier version only applied the symbol once at startup, so toggling
 * the Config setting mid-session (or even after a restart, depending on exact timing) didn't
 * retroactively touch already-built titles.
 */
public class VLPlusAdditions {
    public static final String METEOR_SYMBOL = "☄"; // comet - stock Meteor, untouched
    public static final String MODIFIED_SYMBOL = "✎"; // pencil - stock Meteor, modified/extended by VL+
    public static final String VLPLUS_SYMBOL = "✚"; // thick plus - added by VL+ from scratch
    public static final String EXPERIMENTAL_SYMBOL = "⚠"; // warning - not fully reliable yet

    // Module ids (Module.name) added from scratch.
    private static final Set<String> NEW_MODULES = Set.of(
        "hitboxes",
        "particle-color",
        "xp-bar-adjust",
        "xp-level-adjust",
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
        "reader-notif",
        "vanilla-hotbar",
        "vanilla-armor",
        "vanilla-health",
        "vanilla-hunger",
        "vanilla-mount-health",
        "vanilla-air"
    );

    // HudElementInfo ids (HudElementInfo.name) that originate upstream but have been modified.
    private static final Set<String> MODIFIED_HUD_ELEMENTS = Set.of(
        "item"
    );

    // HudElementInfo ids reported as not working correctly yet - shown with EXPERIMENTAL_SYMBOL
    // in addition to (not instead of) their origin symbol above, since a whole element can be
    // both "added by VL+" and "not fully reliable yet" at once.
    private static final Set<String> EXPERIMENTAL_HUD_ELEMENTS = Set.of(
        "vanilla-hotbar",
        "vanilla-armor",
        "vanilla-health",
        "vanilla-hunger",
        "vanilla-mount-health",
        "vanilla-air"
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

    private static String originPrefix(String name, Set<String> newSet, Set<String> modifiedSet) {
        if (!symbolsEnabled()) return "";

        if (newSet.contains(name)) return VLPLUS_SYMBOL + " ";
        if (modifiedSet.contains(name)) return MODIFIED_SYMBOL + " ";
        return METEOR_SYMBOL + " ";
    }

    // Loops until no more known prefixes match, not just once - a HUD element can carry both an
    // origin symbol AND the experimental symbol at once (see refreshHudElementTitle), so a single
    // pass would only strip one of the two and the other would get baked into the "clean" title,
    // accumulating a new origin symbol on top of it every subsequent refreshAll() call.
    private static String stripOriginPrefix(String title) {
        boolean strippedAny = true;

        while (strippedAny) {
            strippedAny = false;

            for (String symbol : new String[]{VLPLUS_SYMBOL, MODIFIED_SYMBOL, METEOR_SYMBOL, EXPERIMENTAL_SYMBOL}) {
                String prefix = symbol + " ";
                if (title.startsWith(prefix)) {
                    title = title.substring(prefix.length());
                    strippedAny = true;
                }
            }
        }

        return title;
    }

    public static void refreshModuleTitle(Module module) {
        module.title = originPrefix(module.name, NEW_MODULES, MODIFIED_MODULES) + stripOriginPrefix(module.title);
    }

    public static void refreshHudElementTitle(HudElementInfo<?> info) {
        String prefix = originPrefix(info.name, NEW_HUD_ELEMENTS, MODIFIED_HUD_ELEMENTS);
        if (symbolsEnabled() && EXPERIMENTAL_HUD_ELEMENTS.contains(info.name)) prefix += EXPERIMENTAL_SYMBOL + " ";

        info.title = prefix + stripOriginPrefix(info.title);
    }

    /** Call whenever Config's "show origin symbols" setting changes, to update already-built titles live. */
    public static void refreshAll() {
        Modules modules = Modules.get();
        if (modules != null) for (Module module : modules.getList()) refreshModuleTitle(module);

        Hud hud = Hud.get();
        if (hud != null) for (HudElementInfo<?> info : hud.infos.values()) refreshHudElementTitle(info);
    }

    /** Prefixes a setting's own displayed name (not just its description) so it stands out as not fully reliable yet. */
    public static <T> Setting<T> markExperimental(Setting<T> setting) {
        setting.title = EXPERIMENTAL_SYMBOL + " " + setting.title;
        return setting;
    }
}
