/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.text.MeteorClickEvent;
import meteordevelopment.meteorclient.utils.misc.text.RunnableClickEvent;
import meteordevelopment.meteorclient.utils.render.CustomPanorama;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.RotatingCubeMapRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Style;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Screen.class, priority = 500)
public abstract class ScreenMixin {
    // Draws CustomPanorama's renderer instead of vanilla's own whenever a valid one is loaded -
    // passes straight through to the original (real) instance/args otherwise, so this is a no-op
    // for every other screen that shares this same panorama-drawing code path.
    @Redirect(method = "renderPanoramaBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/RotatingCubeMapRenderer;render(Lnet/minecraft/client/gui/DrawContext;IIFF)V"))
    private void vlRedirectPanorama(RotatingCubeMapRenderer instance, DrawContext context, int width, int height, float alpha, float delta) {
        RotatingCubeMapRenderer custom = CustomPanorama.getRenderer();
        (custom != null ? custom : instance).render(context, width, height, alpha, delta);
    }

    @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true)
    private void onRenderInGameBackground(CallbackInfo info) {
        if (Utils.canUpdate() && Modules.get().get(NoRender.class).noGuiBackground())
            info.cancel();
    }

    @Inject(method = "handleTextClick", at = @At(value = "HEAD"), cancellable = true)
    private void onInvalidClickEvent(@Nullable Style style, CallbackInfoReturnable<Boolean> cir) {
        if (style == null || !(style.getClickEvent() instanceof RunnableClickEvent runnableClickEvent)) return;

        runnableClickEvent.runnable.run();
        cir.setReturnValue(true);
    }

    @Inject(method = "handleTextClick", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;)V", ordinal = 1, remap = false), cancellable = true)
    private void onRunCommand(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (style.getClickEvent() instanceof MeteorClickEvent clickEvent && clickEvent.getValue().startsWith(Config.get().prefix.get())) {
            try {
                Commands.dispatch(style.getClickEvent().getValue().substring(Config.get().prefix.get().length()));
                cir.setReturnValue(true);
            } catch (CommandSyntaxException e) {
                MeteorClient.LOG.error("Failed to run command", e);
            }
        }
    }
}
