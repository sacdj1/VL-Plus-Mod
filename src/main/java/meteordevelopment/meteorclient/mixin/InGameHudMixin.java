/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.RenderScoreboardEvent;
import meteordevelopment.meteorclient.events.render.RenderTitleEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.BetterChat;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.systems.modules.render.XPBarAdjust;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow @Final private MinecraftClient client;

    @Shadow private Text title;

    @Shadow private Text subtitle;

    @Shadow public abstract void clear();

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        client.getProfiler().push(MeteorClient.MOD_ID + "_render_2d");

        Utils.unscaledProjection();

        MeteorClient.EVENT_BUS.post(Render2DEvent.get(context, context.getScaledWindowWidth(), context.getScaledWindowWidth(), tickCounter.getTickDelta(true)));

        Utils.scaledProjection();
        RenderSystem.applyModelViewMatrix();

        client.getProfiler().pop();
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderStatusEffectOverlay(CallbackInfo info) {
        if (Modules.get().get(NoRender.class).noPotionIcons()) info.cancel();
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderPortalOverlay(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        if (Modules.get().get(NoRender.class).noPortalOverlay()) ci.cancel();
    }

    @ModifyArgs(method = "renderMiscOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V", ordinal = 0))
    private void onRenderPumpkinOverlay(Args args) {
        if (Modules.get().get(NoRender.class).noPumpkinOverlay()) args.set(2, 0f);
    }

    @ModifyArgs(method = "renderMiscOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V", ordinal = 1))
    private void onRenderPowderedSnowOverlay(Args args) {
        if (Modules.get().get(NoRender.class).noPowderedSnowOverlay()) args.set(2, 0f);
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderVignetteOverlay(DrawContext context, Entity entity, CallbackInfo ci) {
        if (Modules.get().get(NoRender.class).noVignette()) ci.cancel();
    }

    // Reader Notif (NoiseNotif module): lets rules watch the scoreboard sidebar's title and score
    // lines. Declared before the NoRender cancel hook below so it still fires even if that one
    // cancels the callback - same-point injectors run in declaration order (this mixin API
    // predates @Inject's "order" field, so that's the only ordering control available).
    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"))
    private void onRenderScoreboardSidebarForReaderNotif(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        MeteorClient.EVENT_BUS.post(RenderScoreboardEvent.get(objective));
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderScoreboardSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (Modules.get().get(NoRender.class).noScoreboard()) ci.cancel();
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderScoreboardSidebar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Modules.get().get(NoRender.class).noScoreboard()) ci.cancel();
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderSpyglassOverlay(DrawContext context, float scale, CallbackInfo ci) {
        if (Modules.get().get(NoRender.class).noSpyglassOverlay()) ci.cancel();
    }

    @ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"))
    private boolean alwaysRenderCrosshairInFreecam(boolean firstPerson) {
        return Modules.get().isActive(Freecam.class) || firstPerson;
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Modules.get().get(NoRender.class).noCrosshair()) ci.cancel();
    }

    // Reader Notif (NoiseNotif module): lets rules watch the title/subtitle text - only posted
    // while one is actually showing, same as vanilla only renders it then. Declared before the
    // NoRender cancel hook below so it still fires even if that one cancels the callback.
    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"))
    private void onRenderTitleForReaderNotif(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (title != null) MeteorClient.EVENT_BUS.post(RenderTitleEvent.get(title, subtitle));
    }

    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
    private void onRenderTitle(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Modules.get().get(NoRender.class).noTitle()) ci.cancel();
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void onRenderHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        if (Modules.get().get(NoRender.class).noHeldItemName()) ci.cancel();
    }

    @Inject(method = "clear", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;clear(Z)V"), cancellable = true)
    private void onClear(CallbackInfo info) {
        if (Modules.get().get(BetterChat.class).keepHistory()) {
            info.cancel();
        }
    }

    // XPBarAdjust module: the background/track is the always-full-width texture drawn first (this
    // is the static "track" - it never changes width with progress), the fill/progress texture is
    // drawn second, cropped to the current xp width, and is the part that actually grows/shrinks.
    // Two render styles: Colorize (default) draws the bar's own texture, converted to grayscale
    // once and cached, then tinted via setShaderColor - a true, full-strength color that still
    // preserves the texture's own shading/border instead of turning into a flat block. Solid just
    // draws a plain alpha-blended color quad on top, no texture at all. Bounds are recomputed here
    // from the same formula InGameHud itself uses (rather than captured via @Redirect) so this
    // stays a plain @Inject - @Redirect claims exclusive ownership of the call it targets and can
    // conflict with another mod's mixin on the same instruction, which @Inject doesn't.
    @Inject(method = "renderExperienceBar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V", shift = At.Shift.AFTER))
    private void onRenderExperienceBarBackground(DrawContext context, int x, CallbackInfo ci) {
        if (client.player == null || Modules.get() == null) return;

        XPBarAdjust module = Modules.get().get(XPBarAdjust.class);
        if (!module.isActive() || !module.shouldRecolorBackground()) return;

        int argb = module.getBackgroundOverlayArgb(client.player.experienceProgress);
        if (argb == 0) return;

        int y = context.getScaledWindowHeight() - 32 + 3;
        drawXpBarOverlay(context, module, argb, x, y, 182, 182, 5, module.getBackgroundGrayTexture(), module.getBackgroundGrayTextureWidth(), module.getBackgroundGrayTextureHeight());
    }

    @Inject(method = "renderExperienceBar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIIIIIII)V", shift = At.Shift.AFTER))
    private void onRenderExperienceBarProgress(DrawContext context, int x, CallbackInfo ci) {
        if (client.player == null || Modules.get() == null) return;

        XPBarAdjust module = Modules.get().get(XPBarAdjust.class);
        if (!module.isActive() || !module.shouldRecolorFill()) return;

        int argb = module.getFillOverlayArgb(client.player.experienceProgress);
        if (argb == 0) return;

        int width = (int) (client.player.experienceProgress * 183f);
        if (width <= 0) return;

        int y = context.getScaledWindowHeight() - 32 + 3;
        drawXpBarOverlay(context, module, argb, x, y, width, 183, 5, module.getProgressGrayTexture(), module.getProgressGrayTextureWidth(), module.getProgressGrayTextureHeight());
    }

    // maxWidth is the logical full-bar width this specific draw call scales against (182 for the
    // background, which is always drawn at that full width anyway; 183 for the fill, which is
    // drawn at anywhere from 0 up to that as progress changes) - NOT the same thing as texWidth,
    // the gray texture's own actual pixel dimensions, which can differ (e.g. a 2x resolution
    // resource pack). Sampling a region proportional to width/maxWidth out of the texture, rather
    // than always sampling the whole texture and stretching it into whatever width happens to be
    // requested, is what keeps the fill's texture pattern from visibly warping as it grows/shrinks.
    private void drawXpBarOverlay(DrawContext context, XPBarAdjust module, int argb, int x, int y, int width, int maxWidth, int height, Identifier grayTexture, int texWidth, int texHeight) {
        if (module.getRenderStyle() == XPBarAdjust.RenderStyle.Colorize && grayTexture != null) {
            float a = ((argb >>> 24) & 0xFF) / 255f;
            float r = ((argb >>> 16) & 0xFF) / 255f;
            float g = ((argb >>> 8) & 0xFF) / 255f;
            float b = (argb & 0xFF) / 255f;

            int regionWidth = Math.max(1, Math.round(texWidth * (width / (float) maxWidth)));

            RenderSystem.setShaderColor(r, g, b, a);
            context.drawTexture(grayTexture, x, y, width, height, 0f, 0f, regionWidth, texHeight, texWidth, texHeight);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            return;
        }

        context.fill(x, y, x + width, y + height, argb);
    }
}
