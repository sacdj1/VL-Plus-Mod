/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.NoiseNotif;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.client.sound.StaticSound;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Lets NoiseNotif's Reader Notif play an arbitrary .ogg file from disk instead of only registered
 * sound events. Minecraft's sound system only ever loads audio through the resource manager, so
 * there's no vanilla way to hand it a raw filesystem path - instead, this intercepts the one
 * specific, reserved identifier VLSounds.CUSTOM_ALERT resolves to (see meteor-client:sounds.json)
 * and, only for that identifier, decodes NoiseNotif's currently chosen file directly instead of
 * looking it up as a resource. Every other sound in the game is completely unaffected. Deliberately
 * bypasses SoundLoader's own cache (unlike the vanilla path) - the file behind this identifier can
 * change at runtime whenever the user picks a different one, so caching it under the same fixed key
 * would keep playing a stale file after that.
 */
@Mixin(SoundLoader.class)
public abstract class SoundLoaderMixin {
    private static final Identifier CUSTOM_ALERT_LOCATION = Identifier.of("meteor-client", "sounds/alerts_custom.ogg");

    @Inject(method = "loadStatic(Lnet/minecraft/util/Identifier;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), cancellable = true)
    private void onLoadStatic(Identifier id, CallbackInfoReturnable<CompletableFuture<StaticSound>> cir) {
        if (!id.equals(CUSTOM_ALERT_LOCATION) || Modules.get() == null) return;

        String path = Modules.get().get(NoiseNotif.class).getCustomSoundPath();
        if (path == null || path.isEmpty()) return;

        File file = new File(path);
        if (!file.isFile()) return;

        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            try (FileInputStream input = new FileInputStream(file); OggAudioStream stream = new OggAudioStream(input)) {
                return new StaticSound(stream.readAll(), stream.getFormat());
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, Util.getDownloadWorkerExecutor()));
    }
}
