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
import meteordevelopment.meteorclient.systems.modules.render.XPLevelAdjust;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.elements.VanillaAirHud;
import meteordevelopment.meteorclient.systems.hud.elements.VanillaArmorHud;
import meteordevelopment.meteorclient.systems.hud.elements.VanillaHealthHud;
import meteordevelopment.meteorclient.systems.hud.elements.VanillaHotbarHud;
import meteordevelopment.meteorclient.systems.hud.elements.VanillaHungerHud;
import meteordevelopment.meteorclient.systems.hud.elements.VanillaMountHealthHud;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.CustomFontRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
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

    @Shadow protected abstract boolean shouldRenderExperience();

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

        float renderProgress = module.getRenderProgress(client.player.experienceProgress);
        int argb = module.getBackgroundOverlayArgb(renderProgress);
        if (argb == 0) return;

        int y = context.getScaledWindowHeight() - 32 + 3;
        drawXpBarOverlay(context, module, argb, x, y, 182, 182, 5, module.getBackgroundGrayTexture(), module.getBackgroundGrayTextureWidth(), module.getBackgroundGrayTextureHeight());
    }

    // Drawn independently at TAIL rather than hooked onto vanilla's own progress-texture draw call:
    // vanilla only makes that call at all when its own (real, unsmoothed, un-inverted) width is
    // greater than zero, which is most of the time while the bar sits at "ready" on a
    // cooldown-repurposed server - hooking that call would mean Smooth Fill/Invert Progress could
    // never keep drawing a receding/growing fill during exactly that window. Both render styles
    // already draw themselves fully (Colorize redraws the whole cropped texture, Solid draws a
    // flat rect), so nothing here actually depends on vanilla's own draw having happened.
    @Inject(method = "renderExperienceBar", at = @At("TAIL"))
    private void onRenderExperienceBarProgress(DrawContext context, int x, CallbackInfo ci) {
        if (client.player == null || Modules.get() == null) return;
        if (client.player.getNextLevelExperience() <= 0) return;

        XPBarAdjust module = Modules.get().get(XPBarAdjust.class);
        if (!module.isActive() || !module.shouldRecolorFill()) return;

        float renderProgress = module.getRenderProgress(client.player.experienceProgress);
        int argb = module.getFillOverlayArgb(renderProgress);
        if (argb == 0) return;

        int width = module.getFillWidthPixels(renderProgress);
        if (width <= 0) return;

        int y = context.getScaledWindowHeight() - 32 + 3;

        RenderSystem.enableBlend();
        drawXpBarOverlay(context, module, argb, x, y, width, 183, 5, module.getProgressGrayTexture(), module.getProgressGrayTextureWidth(), module.getProgressGrayTextureHeight());
        RenderSystem.disableBlend();
    }

    // XPLevelAdjust module: fully replaces vanilla's own draw (cancelled below) rather than
    // overlaying on top of it, since vanilla skips drawing entirely once the level is 0 - a plain
    // overlay could never implement On Zero's Hide/Custom Text/Distinct Color options, which all
    // need to run even when vanilla itself would show nothing.
    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void onRenderExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (client.player == null || Modules.get() == null) return;

        XPLevelAdjust module = Modules.get().get(XPLevelAdjust.class);
        if (!module.isActive() || !shouldRenderExperience()) return;

        ci.cancel();

        int level = client.player.experienceLevel;
        if (module.isHidden(level)) return;

        String text = module.getText(level);
        Color color = module.getColor(level);
        int argb = color.getPacked();

        double scale = module.getScale();
        int centerX = context.getScaledWindowWidth() / 2;
        int y = context.getScaledWindowHeight() - 31 - 4;

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.scale((float) scale, (float) scale, 1f);

        if (module.useCustomFont()) {
            double width = CustomFontRenderer.width(module.getFont(), text, 1.0, module.getShadow());
            double drawX = centerX / scale - width / 2.0;
            double drawY = y / scale;

            CustomFontRenderer.render(module.getFont(), text, drawX, drawY, color, 1.0, module.getShadow());
        }
        else {
            int width = client.textRenderer.getWidth(text);
            int drawX = (int) Math.round(centerX / scale - width / 2.0);
            int drawY = (int) Math.round(y / scale);

            context.drawText(client.textRenderer, text, drawX, drawY, argb, module.getShadow());
        }

        matrices.pop();
    }

    // Vanilla HUD relocator elements (VanillaHotbarHud etc.): none of them draw anything
    // themselves - vanilla's own draw call for that piece just runs completely unmodified inside
    // a translated/scaled matrix instead, so every bit of vanilla's own logic (item rendering,
    // heart selection, blinking/regen animation, absorption, hardcore hearts...) stays exactly
    // correct for free. The transform maps vanilla's own default anchor point for that piece onto
    // the element's real HUD position, at the element's own scale - when no such element is
    // present, defaultX/defaultY and scale 1 make this an identity transform (a harmless no-op).
    private <T extends HudElement> T findVanillaElement(Class<T> type) {
        for (HudElement element : Hud.get()) {
            if (type.isInstance(element) && element.isActive()) return type.cast(element);
        }

        return null;
    }

    private void pushVanillaTransform(DrawContext context, double elementX, double elementY, double scale, double defaultX, double defaultY) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(elementX, elementY, 0);
        matrices.scale((float) scale, (float) scale, 1f);
        matrices.translate(-defaultX, -defaultY, 0);
    }

    private void popVanillaTransform(DrawContext context) {
        context.getMatrices().pop();
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"))
    private void onRenderHotbarHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        VanillaHotbarHud element = findVanillaElement(VanillaHotbarHud.class);
        double defaultX = context.getScaledWindowWidth() / 2.0 - 91;
        double defaultY = context.getScaledWindowHeight() - 22;

        pushVanillaTransform(context, element != null ? element.x : defaultX, element != null ? element.y : defaultY, element != null ? element.getScale() : 1, defaultX, defaultY);
    }

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void onRenderHotbarTail(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        popVanillaTransform(context);
    }

    @Inject(method = "renderArmor", at = @At("HEAD"))
    private static void onRenderArmorHead(DrawContext context, PlayerEntity player, int i, int j, int k, int x, CallbackInfo ci) {
        VanillaArmorHud element = staticFindVanillaElement(VanillaArmorHud.class);

        double defaultX = x;
        double defaultY = i - (j - 1) * k - 10;

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(element != null ? element.x : defaultX, element != null ? element.y : defaultY, 0);
        matrices.scale((float) (element != null ? element.getScale() : 1), (float) (element != null ? element.getScale() : 1), 1f);
        matrices.translate(-defaultX, -defaultY, 0);
    }

    @Inject(method = "renderArmor", at = @At("TAIL"))
    private static void onRenderArmorTail(DrawContext context, PlayerEntity player, int i, int j, int k, int x, CallbackInfo ci) {
        context.getMatrices().pop();
    }

    private static <T extends HudElement> T staticFindVanillaElement(Class<T> type) {
        for (HudElement element : Hud.get()) {
            if (type.isInstance(element) && element.isActive()) return type.cast(element);
        }

        return null;
    }

    @Inject(method = "renderHealthBar", at = @At("HEAD"))
    private void onRenderHealthBarHead(DrawContext context, PlayerEntity player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        VanillaHealthHud element = findVanillaElement(VanillaHealthHud.class);
        pushVanillaTransform(context, element != null ? element.x : x, element != null ? element.y : y, element != null ? element.getScale() : 1, x, y);
    }

    @Inject(method = "renderHealthBar", at = @At("TAIL"))
    private void onRenderHealthBarTail(DrawContext context, PlayerEntity player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        popVanillaTransform(context);
    }

    @Inject(method = "renderFood", at = @At("HEAD"))
    private void onRenderFoodHead(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo ci) {
        VanillaHungerHud element = findVanillaElement(VanillaHungerHud.class);
        double defaultX = right - 81;
        double defaultY = top;

        pushVanillaTransform(context, element != null ? element.x : defaultX, element != null ? element.y : defaultY, element != null ? element.getScale() : 1, defaultX, defaultY);
    }

    @Inject(method = "renderFood", at = @At("TAIL"))
    private void onRenderFoodTail(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo ci) {
        popVanillaTransform(context);
    }

    @Inject(method = "renderMountHealth", at = @At("HEAD"))
    private void onRenderMountHealthHead(DrawContext context, CallbackInfo ci) {
        VanillaMountHealthHud element = findVanillaElement(VanillaMountHealthHud.class);
        double defaultX = context.getScaledWindowWidth() / 2.0 + 91 - 81;
        double defaultY = context.getScaledWindowHeight() - 39;

        pushVanillaTransform(context, element != null ? element.x : defaultX, element != null ? element.y : defaultY, element != null ? element.getScale() : 1, defaultX, defaultY);
    }

    @Inject(method = "renderMountHealth", at = @At("TAIL"))
    private void onRenderMountHealthTail(DrawContext context, CallbackInfo ci) {
        popVanillaTransform(context);
    }

    // Air bubbles: vanilla draws these inline inside renderStatusBars with no separate callable
    // method to redirect like the other pieces, so VanillaAirHud reimplements the visual itself
    // (see that class) and this just hides vanilla's own copy - by translating it far off-screen
    // rather than cancelling, since cancelling mid-method here would skip the profiler.pop() at
    // the end of renderStatusBars and leave its profiler stack unbalanced. RenderSystem.enableBlend()/
    // disableBlend() bracket exactly the air-drawing loop and are the only inline blend calls in
    // this specific method (armor/health/food each have their own, inside their own methods), so
    // they're a safe, unambiguous pair of injection points.
    @Inject(method = "renderStatusBars", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;enableBlend()V", ordinal = 0, shift = At.Shift.BEFORE, remap = false))
    private void onRenderAirHead(DrawContext context, CallbackInfo ci) {
        if (findVanillaElement(VanillaAirHud.class) == null) return;

        context.getMatrices().push();
        context.getMatrices().translate(100000.0, 100000.0, 0);
    }

    @Inject(method = "renderStatusBars", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;disableBlend()V", ordinal = 0, shift = At.Shift.AFTER, remap = false))
    private void onRenderAirTail(DrawContext context, CallbackInfo ci) {
        if (findVanillaElement(VanillaAirHud.class) == null) return;

        context.getMatrices().pop();
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
