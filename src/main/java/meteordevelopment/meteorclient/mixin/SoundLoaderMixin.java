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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lets NoiseNotif's Reader Notif play an arbitrary .ogg file from disk instead of only registered
 * sound events. Minecraft's sound system only ever loads audio through the resource manager, so
 * there's no vanilla way to hand it a raw filesystem path - instead, this intercepts two kinds of
 * reserved identifiers instead of looking them up as a resource: VLSounds.CUSTOM_ALERT (NoiseNotif's
 * single shared ability-ready custom sound) and the VLSounds.RULE_SOUND_SLOTS pool
 * (meteor-client:alerts_rule_0.. - see meteor-client:sounds.json), one per ReaderRule that's chosen
 * CustomFile sound mode. Every other sound in the game is completely unaffected. Deliberately
 * bypasses SoundLoader's own cache (unlike the vanilla path) - the file behind a given identifier
 * can change at runtime (a different file chosen, or a slot handed to a different rule), so caching
 * it under the same fixed key would keep playing a stale file after that.
 */
@Mixin(SoundLoader.class)
public abstract class SoundLoaderMixin {
    private static final Identifier CUSTOM_ALERT_LOCATION = Identifier.of("meteor-client", "sounds/alerts_custom.ogg");
    private static final Pattern RULE_SOUND_PATTERN = Pattern.compile("^sounds/alerts_rule_(\\d+)\\.ogg$");

    @Inject(method = "loadStatic(Lnet/minecraft/util/Identifier;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), cancellable = true)
    private void onLoadStatic(Identifier id, CallbackInfoReturnable<CompletableFuture<StaticSound>> cir) {
        if (Modules.get() == null || !id.getNamespace().equals("meteor-client")) return;

        String path;

        if (id.equals(CUSTOM_ALERT_LOCATION)) {
            path = Modules.get().get(NoiseNotif.class).getCustomSoundPath();
        }
        else {
            Matcher matcher = RULE_SOUND_PATTERN.matcher(id.getPath());
            if (!matcher.matches()) return;

            path = Modules.get().get(NoiseNotif.class).getRuleSoundPath(Integer.parseInt(matcher.group(1)));
        }

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
