/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.utils.misc.EventProfiler;
import meteordevelopment.orbit.listeners.LambdaListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * Every module/HUD element's @EventHandler method is invoked through one LambdaListener instance
 * (one per method) - timing call() here, generically, profiles literally everything hooked into the
 * event bus without needing per-module instrumentation.
 *
 * LambdaListener's own "target" field is NOT the owning module/HUD element - it's actually set from
 * method.getParameters()[0].getType(), i.e. the EVENT's class (Render2DEvent, TickEvent.Post, ...),
 * so grouping by it lumps every module's Render2DEvent handler together instead of telling them
 * apart. The constructor's own "Class<?> clazz" parameter (the method's actual declaring class -
 * the module/HUD element) is what's needed instead, and it's only ever used locally inside the
 * constructor to build a MethodHandle lookup - never stored in a field - so it's captured here into
 * a field of our own instead.
 *
 * remap=false because orbit is a plain third-party library on the classpath, not run through
 * Minecraft's own Yarn remapper - there's no obfuscation mapping for it to look up (same reason the
 * Sodium mixins need it).
 */
@Mixin(value = LambdaListener.class, remap = false)
public abstract class LambdaListenerMixin {
    @Unique
    private Class<?> vlplus$owner;

    @Unique
    private long vlplus$callStartNanos;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void vlplus$onConstruct(LambdaListener.Factory factory, Class<?> clazz, Object instance, Method method, CallbackInfo ci) {
        vlplus$owner = clazz;
    }

    @Inject(method = "call", at = @At("HEAD"))
    private void vlplus$onCallStart(Object event, CallbackInfo ci) {
        if (EventProfiler.active) vlplus$callStartNanos = System.nanoTime();
    }

    @Inject(method = "call", at = @At("RETURN"))
    private void vlplus$onCallEnd(Object event, CallbackInfo ci) {
        if (!EventProfiler.active || vlplus$callStartNanos == 0) return;

        long elapsed = System.nanoTime() - vlplus$callStartNanos;
        vlplus$callStartNanos = 0;

        EventProfiler.record(vlplus$owner, elapsed);
    }
}
