/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Custom sound events added by VL+. None of these ever actually resolve their audio through the
 * normal resource pack lookup - SoundLoaderMixin intercepts each one and streams whatever
 * arbitrary .ogg file it's currently pointed at instead (see ReaderRule's per-rule custom sound,
 * and NoiseNotif's own single ability-ready custom sound), so their sounds.json entries only need
 * to exist to make the events themselves resolvable to a Sound at all.
 *
 * Registered as a fixed pool rather than one-per-rule, since Minecraft's registries freeze after
 * bootstrap (see MeteorClient's early/late onInitializeClient split) and ReaderRules are created
 * and deleted freely at runtime, long after that freeze - there's no way to register a genuinely
 * new SoundEvent per rule on demand. Each rule that wants its own custom file instead claims one
 * of these pre-registered slots for as long as it exists (see ReaderRule.customSoundSlot).
 */
public class VLSounds {
    public static final Identifier CUSTOM_ALERT_ID = Identifier.of(MeteorClient.MOD_ID, "alerts_custom");
    public static final SoundEvent CUSTOM_ALERT = SoundEvent.of(CUSTOM_ALERT_ID);

    // Rules claim slots by index (see ReaderRuleSoundSlots) - 32 concurrent rules with their own
    // distinct custom file is far more than any realistic rule list needs.
    public static final int RULE_SOUND_SLOTS = 32;
    private static final SoundEvent[] RULE_SOUNDS = new SoundEvent[RULE_SOUND_SLOTS];
    private static final boolean[] RULE_SOUND_SLOT_USED = new boolean[RULE_SOUND_SLOTS];

    public static void init() {
        Registry.register(Registries.SOUND_EVENT, CUSTOM_ALERT_ID, CUSTOM_ALERT);

        for (int i = 0; i < RULE_SOUND_SLOTS; i++) {
            Identifier id = Identifier.of(MeteorClient.MOD_ID, "alerts_rule_" + i);
            RULE_SOUNDS[i] = Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
        }
    }

    public static SoundEvent ruleSound(int slot) {
        return slot >= 0 && slot < RULE_SOUND_SLOTS ? RULE_SOUNDS[slot] : null;
    }

    /** Finds and reserves the first free slot. Returns -1 if the pool is exhausted. */
    public static int claimRuleSoundSlot() {
        for (int i = 0; i < RULE_SOUND_SLOTS; i++) {
            if (!RULE_SOUND_SLOT_USED[i]) {
                RULE_SOUND_SLOT_USED[i] = true;
                return i;
            }
        }

        return -1;
    }

    public static void releaseRuleSoundSlot(int slot) {
        if (slot >= 0 && slot < RULE_SOUND_SLOTS) RULE_SOUND_SLOT_USED[slot] = false;
    }

    /** Marks a specific slot used without scanning - for rules loaded from a previous session that already have a slot assigned. */
    public static void reserveRuleSoundSlot(int slot) {
        if (slot >= 0 && slot < RULE_SOUND_SLOTS) RULE_SOUND_SLOT_USED[slot] = true;
    }
}
