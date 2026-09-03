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
 * Custom sound events added by VL+. CUSTOM_ALERT's audio is never actually resolved through the
 * normal resource pack lookup - SoundLoaderMixin intercepts it and streams whatever arbitrary
 * .ogg file NoiseNotif's Reader Notif "custom sound file" points at instead, so an entry in
 * sounds.json only needs to exist to make the event itself resolvable to a Sound at all.
 */
public class VLSounds {
    public static final Identifier CUSTOM_ALERT_ID = Identifier.of(MeteorClient.MOD_ID, "alerts_custom");
    public static final SoundEvent CUSTOM_ALERT = SoundEvent.of(CUSTOM_ALERT_ID);

    public static void init() {
        Registry.register(Registries.SOUND_EVENT, CUSTOM_ALERT_ID, CUSTOM_ALERT);
    }
}
