/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IDrawContext;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.RenderScoreboardEvent;
import meteordevelopment.meteorclient.events.render.TitleSetEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.BetterChat;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.systems.modules.render.HealthBarAdjust;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.systems.modules.render.StaminaBarAdjust;
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
import meteordevelopment.meteorclient.systems.hud.elements.VanillaXPBarHud;
import meteordevelopment.meteorclient.systems.hud.elements.VanillaXPLevelHud;
import meteordevelopment.meteorclient.systems.hud.elements.VanillaTitleHud;
import meteordevelopment.meteorclient.systems.hud.elements.VanillaSubtitleHud;
import meteordevelopment.meteorclient.systems.hud.elements.VanillaActionBarHud;
import meteordevelopment.meteorclient.systems.hud.elements.VanillaScoreboardHud;
import meteordevelopment.meteorclient.systems.hud.elements.VanillaItemNameHud;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.GrayscaleSpriteCache;
import meteordevelopment.meteorclient.utils.render.ItemRenderAlphaState;
import meteordevelopment.meteorclient.utils.render.VanillaHudRotationState;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import net.minecraft.client.texture.Sprite;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow @Final private MinecraftClient client;

    @Shadow private Text title;

    @Shadow private Text subtitle;

    @Shadow public abstract void clear();

    // ---- Vanilla HUD element replacement (deferred redraw) ----------------------------------------
    // A Vanilla* HUD element does NOT transform vanilla's own draw where it normally happens (that
    // pushed/popped matrices in the middle of vanilla's render, in between chat, the scoreboard, the
    // tab list... - anything leaking out of it, or a pop that didn't line up, disturbed those). Instead
    // vanilla's in-place draw is cancelled, and the exact same vanilla method is called again at the
    // very END of the HUD pass (see vlReplayDeferred, called from onRender before Meteor's own HUD),
    // wrapped in one push / try / finally / pop. Everything else has finished drawing by then, so
    // there's nothing left for it to disturb, and the pairing is guaranteed by the finally.
    // Static because renderArmor is a static method and needs to defer too.
    private static final java.util.List<Runnable> vlDeferred = new java.util.ArrayList<>();
    private static boolean vlReplaying = false;
    private static boolean vlReplayErrorLogged = false;

    private static void vlReplayDeferred() {
        if (vlDeferred.isEmpty()) return;

        java.util.List<Runnable> jobs = new java.util.ArrayList<>(vlDeferred);
        vlDeferred.clear();

        vlReplaying = true;
        try {
            for (Runnable job : jobs) {
                try {
                    job.run();
                } catch (Throwable t) {
                    if (!vlReplayErrorLogged) {
                        vlReplayErrorLogged = true;
                        MeteorClient.LOG.error("A relocated Vanilla HUD element threw while redrawing (further errors from this are not logged)", t);
                    }
                }
            }
        } finally {
            vlReplaying = false;
        }
    }

    @Shadow private int titleRemainTicks;
    @Shadow private int titleFadeInTicks;
    @Shadow private int titleStayTicks;
    @Shadow private int titleFadeOutTicks;

    @Shadow protected abstract void renderScoreboardSidebar(DrawContext context, ScoreboardObjective objective);
    @Shadow protected abstract void renderOverlayMessage(DrawContext context, RenderTickCounter tickCounter);
    @Shadow protected abstract void renderHeldItemTooltip(DrawContext context);
    @Shadow protected abstract void renderExperienceBar(DrawContext context, int x);
    @Shadow protected abstract void renderExperienceLevel(DrawContext context, RenderTickCounter tickCounter);
    @Shadow protected abstract void renderHotbar(DrawContext context, RenderTickCounter tickCounter);
    @Shadow protected abstract void renderHealthBar(DrawContext context, PlayerEntity player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking);
    @Shadow protected abstract void renderFood(DrawContext context, PlayerEntity player, int top, int right);
    @Shadow protected abstract void renderMountHealth(DrawContext context);
    @Shadow private static void renderArmor(DrawContext context, PlayerEntity player, int i, int j, int k, int x) {
        throw new AssertionError();
    }



    @Shadow protected abstract boolean shouldRenderExperience();

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        vlReplayDeferred();

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
        if (vlReplaying) return;
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

    // Declared after the NoRender-cancel hook above so this never defers when that will cancel.
    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderScoreboardPositionHead(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (vlReplaying || ci.isCancelled()) return;

        VanillaScoreboardHud element = findVanillaElement(VanillaScoreboardHud.class);
        if (element == null || Modules.get().get(NoRender.class).noScoreboard()) return;

        ci.cancel();
        if (element.isHidden()) return;

        vlDeferred.add(() -> {
            double defaultX = context.getScaledWindowWidth() - 3;
            double defaultY = context.getScaledWindowHeight() / 2.0;
            double scale = guiScaleFactor(context);

            double centerX = element.x + element.getWidth() / 2.0;
            double centerY = element.y + element.getHeight() / 2.0;

            pushVanillaTransform(context, centerX / scale, centerY / scale, element.getScale() / scale, defaultX, defaultY, element.getRotation(), centerX / scale, centerY / scale, element.alpha.get() / 255.0);
            try {
                renderScoreboardSidebar(context, objective);
            } finally {
                popVanillaTransform(context);
            }
        });
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderSpyglassOverlay(DrawContext context, float scale, CallbackInfo ci) {
        if (Modules.get().get(NoRender.class).noSpyglassOverlay()) ci.cancel();
    }

    @ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"))
    private boolean alwaysRenderCrosshairInFreecam(boolean firstPerson) {
        return firstPerson || Modules.get().isActive(Freecam.class) || Modules.get().get(NoRender.class).crosshairInThirdPerson();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Modules.get().get(NoRender.class).noCrosshair()) ci.cancel();
    }

    // Reader Notif (NoiseNotif module): lets rules watch the title/subtitle text - posted once
    // right when the server actually (re)sets either one, not once per rendered frame while it
    // happens to still be on screen. The old per-frame approach (re-checking every frame a title
    // was visible, gated by a time-based per-rule cooldown) meant a title on screen longer than
    // that cooldown re-triggered a second time near the end, during its own fade-out - the exact
    // same title "renewal", counted twice. Hooking the actual set calls instead means this only
    // ever fires once per genuine renewal, matching however many times the server actually sends one.
    @Inject(method = "setTitle", at = @At("TAIL"))
    private void onSetTitle(Text titleArg, CallbackInfo ci) {
        MeteorClient.EVENT_BUS.post(TitleSetEvent.get(title, subtitle));
    }

    @Inject(method = "setSubtitle", at = @At("TAIL"))
    private void onSetSubtitle(Text subtitleArg, CallbackInfo ci) {
        MeteorClient.EVENT_BUS.post(TitleSetEvent.get(title, subtitle));
    }

    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
    private void onRenderTitle(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Modules.get().get(NoRender.class).noTitle()) ci.cancel();
    }

    // Title / Subtitle: unlike the other Vanilla* elements these are NOT vanilla's own draw moved
    // around - vanilla's title code is cancelled and the text is drawn here from vanilla's own title
    // state (same fade timing, same background), at the elements' own position/scale. The old approach
    // injected into the middle of vanilla's title method (pushing at each inner scale() call and
    // popping before each inner pop()), which is what was disturbing the chat and tab list scale.
    // Drawn deferred (end of the HUD pass), contained in one push / try / finally / pop each.
    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
    private void onRenderTitleReplace(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (vlReplaying || ci.isCancelled()) return;

        VanillaTitleHud titleElement = findVanillaElement(VanillaTitleHud.class);
        VanillaSubtitleHud subtitleElement = findVanillaElement(VanillaSubtitleHud.class);
        if (titleElement == null && subtitleElement == null) return;
        if (Modules.get().get(NoRender.class).noTitle()) return;

        ci.cancel();

        Text titleText = title;
        Text subtitleText = subtitle;
        if (titleText == null || titleRemainTicks <= 0) return;

        // Same fade math as vanilla's own renderTitleAndSubtitle.
        float remaining = (float) titleRemainTicks - tickCounter.getTickDelta(false);
        int alpha = 255;

        if (titleRemainTicks > titleFadeOutTicks + titleStayTicks) {
            float elapsed = (float) (titleFadeInTicks + titleStayTicks + titleFadeOutTicks) - remaining;
            alpha = (int) (elapsed * 255.0F / (float) titleFadeInTicks);
        }

        if (titleRemainTicks <= titleFadeOutTicks) alpha = (int) (remaining * 255.0F / (float) titleFadeOutTicks);

        alpha = MathHelper.clamp(alpha, 0, 255);
        if (alpha <= 8) return;

        int finalAlpha = alpha;

        vlDeferred.add(() -> {
            double guiScale = guiScaleFactor(context);
            int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();

            // Vanilla's own anchor: screen centre, title at 4x, subtitle at 2x; y is text-top offset.
            if (titleElement == null) drawTitleText(context, titleText, finalAlpha, w / 2.0, h / 2.0 + (-10 + 4) * 4, 4, 0);
            else if (!titleElement.isHidden()) {
                double cx = (titleElement.x + titleElement.getWidth() / 2.0) / guiScale;
                double cy = (titleElement.y + titleElement.getHeight() / 2.0) / guiScale;
                drawTitleText(context, titleText, (int) (finalAlpha * titleElement.alpha.get() / 255.0), cx, cy, 4 * titleElement.getScale() / guiScale, titleElement.getRotation());
            }

            if (subtitleText == null) return;

            if (subtitleElement == null) drawTitleText(context, subtitleText, finalAlpha, w / 2.0, h / 2.0 + (5 + 4) * 2, 2, 0);
            else if (!subtitleElement.isHidden()) {
                double cx = (subtitleElement.x + subtitleElement.getWidth() / 2.0) / guiScale;
                double cy = (subtitleElement.y + subtitleElement.getHeight() / 2.0) / guiScale;
                drawTitleText(context, subtitleText, (int) (finalAlpha * subtitleElement.alpha.get() / 255.0), cx, cy, 2 * subtitleElement.getScale() / guiScale, subtitleElement.getRotation());
            }
        });
    }

    /** One title/subtitle line, centred on (centerX, centerY) in GUI units, contained in its own push/pop. */
    private void drawTitleText(DrawContext context, Text text, int alpha, double centerX, double centerY, double scale, double rotation) {
        if (alpha <= 8) return;

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        RenderSystem.enableBlend();

        try {
            matrices.translate(centerX, centerY, 0);
            if (rotation != 0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotation));
            matrices.scale((float) scale, (float) scale, 1f);

            int width = client.textRenderer.getWidth(text);
            context.drawTextWithBackground(client.textRenderer, text, -width / 2, -4, width, ColorHelper.Argb.withAlpha(alpha, -1));
        } finally {
            RenderSystem.disableBlend();
            matrices.pop();
        }
    }

    // Action bar (renderOverlayMessage) is its own separate vanilla method with only one draw call,
    // so - unlike title/subtitle above - the whole-method wrap pattern (see e.g. onRenderHotbarHead)
    // applies directly here.
    private void vlDeferWithTransform(DrawContext context, HudElement element, double defaultX, double defaultY, Runnable draw) {
        // element positions are the box CENTER for text-like elements (see callers) - shared helper
        // for the simple "same vanilla call, relocated" elements below.
        double scale = guiScaleFactor(context);
        double centerX = element.x + element.getWidth() / 2.0;
        double centerY = element.y + element.getHeight() / 2.0;

        double contentScale = element instanceof VanillaActionBarHud a ? a.getScale() : ((VanillaItemNameHud) element).getScale();
        int rotation = element instanceof VanillaActionBarHud a2 ? a2.getRotation() : ((VanillaItemNameHud) element).getRotation();

        pushVanillaTransform(context, centerX / scale, centerY / scale, contentScale / scale, defaultX, defaultY, rotation, centerX / scale, centerY / scale, element.alpha.get() / 255.0);
        try {
            draw.run();
        } finally {
            popVanillaTransform(context);
        }
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void onRenderActionBarPositionHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (vlReplaying || ci.isCancelled()) return;

        VanillaActionBarHud element = findVanillaElement(VanillaActionBarHud.class);
        if (element == null) return;

        ci.cancel();
        if (element.isHidden()) return;

        vlDeferred.add(() -> vlDeferWithTransform(context, element,
            context.getScaledWindowWidth() / 2.0, context.getScaledWindowHeight() - 68.0,
            () -> renderOverlayMessage(context, tickCounter)));
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void onRenderHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        if (Modules.get().get(NoRender.class).noHeldItemName()) ci.cancel();
    }

    // Held item name popup / action bar: same "cancel in place, redraw the same vanilla call at the end" scheme as every Vanilla* element.
    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void onRenderItemNamePositionHead(DrawContext context, CallbackInfo ci) {
        if (vlReplaying || ci.isCancelled()) return;

        VanillaItemNameHud element = findVanillaElement(VanillaItemNameHud.class);
        if (element == null) return;

        ci.cancel();
        if (element.isHidden()) return;

        vlDeferred.add(() -> vlDeferWithTransform(context, element,
            context.getScaledWindowWidth() / 2.0, context.getScaledWindowHeight() - 59 + (client.interactionManager.hasStatusBars() ? 0 : 14),
            () -> renderHeldItemTooltip(context)));
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
    // VanillaXPBarHud: wraps the WHOLE method (HEAD/TAIL, declared so the TAIL-pop below runs
    // after onRenderExperienceBarProgress's own TAIL hook further down - same-point injectors in
    // this class run in declaration order) so XPBarAdjust's recolor overlays, which run nested
    // inside this window, automatically move/scale along with the relocated bar for free - no
    // dependency between the two modules needed beyond injection order. Neither vanilla's own body
    // nor XPBarAdjust's hooks ever cancel renderExperienceBar, so a plain push/pop pair here is
    // always safe (unlike renderExperienceLevel below, which XPLevelAdjust can cancel).
    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void onRenderExperienceBarPositionHead(DrawContext context, int x, CallbackInfo ci) {
        if (vlReplaying || ci.isCancelled()) return;

        if (Modules.get().get(XPBarAdjust.class).shouldAutoHide()) {
            ci.cancel();
            return;
        }

        VanillaXPBarHud element = findVanillaElement(VanillaXPBarHud.class);
        if (element == null) return;

        ci.cancel();
        if (element.isHidden()) return;

        // XPBarAdjust's own recolor hooks live inside renderExperienceBar itself, so they run on the
        // redraw below and move/scale with the relocated bar for free.
        vlDeferred.add(() -> {
            double defaultX = x;
            double defaultY = context.getScaledWindowHeight() - 32 + 3;
            double scale = guiScaleFactor(context);

            pushVanillaTransform(context,
                element.x / scale, element.y / scale,
                element.getScaleX() / scale, element.getScaleY() / scale, defaultX, defaultY,
                element.getRotation(), pivotX(element, scale), pivotY(element, scale),
                element.alpha.get() / 255.0
            );

            try {
                renderExperienceBar(context, x);
            } finally {
                popVanillaTransform(context);
            }
        });
    }

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

        // A VanillaXPLevelHud element takes over position AND scale entirely when present (its
        // own Scale setting, not module.getScale()) - anchorX is the element's own box CENTER
        // (not its left edge), so the text still centers on it regardless of digit count, same as
        // vanilla's own default screen-centered behavior.
        VanillaXPLevelHud relocated = findVanillaElement(VanillaXPLevelHud.class);
        if (relocated != null && relocated.isHidden()) return;

        double guiScale = guiScaleFactor(context);

        double scale = relocated != null ? relocated.getScale() / guiScale : module.getScale();
        int anchorX = relocated != null ? (int) Math.round((relocated.x + relocated.getWidth() / 2.0) / guiScale) : context.getScaledWindowWidth() / 2;
        int anchorY = relocated != null ? (int) Math.round(relocated.y / guiScale) : context.getScaledWindowHeight() - 31 - 4;

        if (relocated != null) {
            double a = relocated.alpha.get() / 255.0;
            argb = withAlphaMultiplied(argb, a);
        }

        final int argbF = argb;
        final double scaleF = scale;
        final int anchorXF = anchorX, anchorYF = anchorY;
        final VanillaXPLevelHud relocatedF = relocated;

        vlDeferred.add(() -> {
            MatrixStack matrices = context.getMatrices();
            matrices.push();

            try {
                matrices.scale((float) scaleF, (float) scaleF, 1f);
                int outlineArgb = switch (module.getOutline()) {
                    case Black -> Color.BLACK.getPacked();
                    case White -> Color.WHITE.getPacked();
                    case Custom -> module.getCustomOutlineColor().getPacked();
                    case None -> 0;
                };

                if (relocatedF != null) outlineArgb = withAlphaMultiplied(outlineArgb, relocatedF.alpha.get() / 255.0);

                int width = client.textRenderer.getWidth(text);
                int drawX = (int) Math.round(anchorXF / scaleF - width / 2.0);
                int drawY = (int) Math.round(anchorYF / scaleF);

                if (module.getOutline() != XPLevelAdjust.OutlineColor.None) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue;
                            context.drawText(client.textRenderer, text, drawX + dx, drawY + dy, outlineArgb, false);
                        }
                    }
                }

                context.drawText(client.textRenderer, text, drawX, drawY, argbF, module.getShadow());
            } finally {
                matrices.pop();
            }
        });
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void onRenderExperienceLevelPositionHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (vlReplaying || ci.isCancelled()) return;

        VanillaXPLevelHud element = findVanillaElement(VanillaXPLevelHud.class);
        if (element == null || willXPLevelAdjustCancel()) return;

        ci.cancel();
        if (element.isHidden()) return;

        vlDeferred.add(() -> {
            // Vanilla's own renderExperienceLevel centers the number horizontally around the middle of
            // the screen (j = (scaledWindowWidth - textWidth) / 2) - defaultX matches that same true
            // anchor (its own top-left draw position). elementX below is NOT the box's own left edge
            // (element.x) - it's shifted left by half the CURRENT text's width, so the text's own
            // center (not its left edge) lands on the box's center regardless of digit count, matching
            // vanilla's own centered behavior instead of just anchoring the first character.
            String levelText = client.player != null ? String.valueOf(client.player.experienceLevel) : "0";
            int width = client.textRenderer.getWidth(levelText);
            double defaultX = (context.getScaledWindowWidth() - width) / 2.0;
            double defaultY = context.getScaledWindowHeight() - 31 - 4;
            double scale = guiScaleFactor(context);
            double elementX = pivotX(element, scale) - width / 2.0;

            pushVanillaTransform(context, elementX, element.y / scale, element.getScale() / scale, defaultX, defaultY, element.getRotation(), pivotX(element, scale), pivotY(element, scale), element.alpha.get() / 255.0);
            try {
                renderExperienceLevel(context, tickCounter);
            } finally {
                popVanillaTransform(context);
            }
        });
    }

    private boolean willXPLevelAdjustCancel() {
        if (client.player == null || Modules.get() == null) return false;

        XPLevelAdjust module = Modules.get().get(XPLevelAdjust.class);
        return module.isActive() && shouldRenderExperience();
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

    // Meteor's own HUD elements (Render2DEvent, so every HudElement.x/y) are positioned and drawn
    // in raw framebuffer pixels (Utils.unscaledProjection() - see InGameHudMixin.onRender) but
    // vanilla's own GUI drawing (and this class's own defaultX/defaultY math below) works in
    // scaled GUI-space pixels (framebuffer pixels / GUI Scale). Without this conversion, an
    // element's position only lined up with vanilla's coordinate space at GUI Scale exactly 1 -
    // at any other scale the relocated vanilla piece would land at the wrong (often off-screen)
    // spot while the element's own placeholder box, self-consistently unscaled throughout, still
    // showed up exactly where it was dragged - "just a transparent box, nothing else."
    private double guiScaleFactor(DrawContext context) {
        return client.getWindow().getFramebufferWidth() / (double) context.getScaledWindowWidth();
    }

    // contentScale is on top of the position conversion above: vanilla's own draw calls, run
    // inside this transform, already expand from GUI-scaled units to framebuffer pixels via the
    // vanilla rendering pipeline itself (that's what GUI Scale always does). The element's own
    // Scale setting and its placeholder box's size (HudElement.getWidth()/getHeight()) are both
    // plain framebuffer-pixel numbers with no such expansion - dividing the requested content
    // scale by the GUI Scale factor here cancels that extra expansion back out, so the actual
    // relocated content ends up the same framebuffer-pixel size the box claims, instead of GUI
    // Scale times bigger than it.
    private void pushVanillaTransform(DrawContext context, double elementX, double elementY, double contentScale, double defaultX, double defaultY) {
        pushVanillaTransform(context, elementX, elementY, contentScale, defaultX, defaultY, 0, 0, 0, 1.0);
    }

    // rotationDegrees spins the already-positioned-and-scaled content around pivotX/pivotY - a
    // point in this SAME local (already guiScale-divided) coordinate space as elementX/elementY,
    // not vanilla's own default anchor - since defaultX/defaultY is a per-piece anchor point
    // (e.g. the hotbar's own top-left) that doesn't correspond to any particular visual center,
    // while the element's own HUD box center (what the editor shows and what you actually dragged)
    // does. Applying the rotation FIRST (translate to pivot, rotate, translate back) means it wraps
    // the whole positioned/scaled result as one rigid rotation, rather than rotating the content
    // before it's been placed (which would rotate around vanilla's own anchor instead).
    private void pushVanillaTransform(DrawContext context, double elementX, double elementY, double contentScale, double defaultX, double defaultY, double rotationDegrees, double pivotX, double pivotY) {
        pushVanillaTransform(context, elementX, elementY, contentScale, defaultX, defaultY, rotationDegrees, pivotX, pivotY, 1.0);
    }

    // alpha (0-1) applies HudElement.alpha to the relocated vanilla content - these elements don't
    // draw through HudRenderer (see HudRenderer.setElementAlpha for the path every other element
    // uses), so this is the one place their own Alpha setting can be applied uniformly, via the
    // same global-shader-color trick RenderUtils/vanilla itself uses for fade effects. Reset
    // unconditionally in popVanillaTransform below rather than only when alpha < 1, so a stray
    // leftover tint from anywhere else can never survive past this element's own draw window.
    private void pushVanillaTransform(DrawContext context, double elementX, double elementY, double contentScale, double defaultX, double defaultY, double rotationDegrees, double pivotX, double pivotY, double alpha) {
        pushVanillaTransform(context, elementX, elementY, contentScale, contentScale, defaultX, defaultY, rotationDegrees, pivotX, pivotY, alpha);
    }

    // Independent X/Y content scale - only Vanilla XP Bar uses this overload so far (its own Scale
    // X/Scale Y settings, on top of its overall Scale), everything else still shares one uniform
    // scale via the overload above.
    private void pushVanillaTransform(DrawContext context, double elementX, double elementY, double contentScaleX, double contentScaleY, double defaultX, double defaultY, double rotationDegrees, double pivotX, double pivotY, double alpha) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();

        if (rotationDegrees != 0) {
            matrices.translate(pivotX, pivotY, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotationDegrees));
            matrices.translate(-pivotX, -pivotY, 0);
        }

        matrices.translate(elementX, elementY, 0);
        matrices.scale((float) contentScaleX, (float) contentScaleY, 1f);
        matrices.translate(-defaultX, -defaultY, 0);

        if (alpha < 1.0) {
            RenderSystem.setShaderColor(1f, 1f, 1f, (float) alpha);
            // See ItemRenderAlphaState's own class doc - without this, builtin-model items (player
            // heads, chests, anything routed through BuiltinModelItemRenderer) rendered as part of
            // this relocated vanilla content (the hotbar's own items, most commonly) ignore the
            // shader alpha above entirely, since they render through a solid/cutout layer instead
            // of the translucent one that actually respects it.
            ItemRenderAlphaState.forcingTranslucent = true;
        }
    }

    // Pivot for rotation: the element's own HUD box center (the same box the editor shows/drags),
    // converted to the local guiScale-divided space pushVanillaTransform's other arguments use.
    private double pivotX(HudElement element, double scale) {
        return (element.x + element.getWidth() / 2.0) / scale;
    }

    private double pivotY(HudElement element, double scale) {
        return (element.y + element.getHeight() / 2.0) / scale;
    }

    private void popVanillaTransform(DrawContext context) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        ItemRenderAlphaState.forcingTranslucent = false;
        context.getMatrices().pop();
    }

    /** Scales an ARGB int's existing alpha channel by a 0-1 factor - for the one hook (XP Level Adjust's relocated draw) that doesn't go through pushVanillaTransform's shader-color path. */
    private int withAlphaMultiplied(int argb, double factor) {
        int a = (int) Math.round(((argb >>> 24) & 0xFF) * factor);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    // Vanilla HUD element alpha, part 2 (see DrawContextMixin's vlPlusDrawColoredSprite/
    // vlPlusDrawColoredFrameSprite for why this redirect exists at all - the colorless
    // drawGuiTexture path doesn't visibly respect alpha, confirmed live via diagnostic logging).
    // Only takes the colored path when shader color alpha is actually < 1 (i.e. only during one of
    // this file's own alpha-enabled relocation windows) - a plain passthrough otherwise, so this
    // has no effect on anything this mod doesn't already control.
    //
    // Covers the plain (non-cropped) overload: armor points, mount hearts, and the XP bar's own
    // background track. Hearts and food each get their own dedicated handler below instead (Health
    // Bar Adjust/Stamina Bar Adjust tint those, not just alpha).
    @Redirect(method = {"renderExperienceBar", "renderMountHealth"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"))
    private void redirectDrawGuiTextureAlpha(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        if (texture.getPath().equals("hud/experience_bar_background")) {
            VanillaXPBarHud element = findVanillaElement(VanillaXPBarHud.class);
            if (element != null && element.shouldPreventEndSquish() && element.getRawScaleX() != element.getRawScaleY()) {
                float[] c = RenderSystem.getShaderColor();
                Sprite sprite = client.getGuiAtlasManager().getSprite(texture);
                drawXpBarSliced(context, sprite, width, height, 0, 0, x, y, width, height, c[0], c[1], c[2], c[3], element.getRawScaleX(), element.getRawScaleY());
                return;
            }
        }

        float[] c = RenderSystem.getShaderColor();
        if (c[3] >= 1.0f) {
            context.drawGuiTexture(texture, x, y, width, height);
            return;
        }

        Sprite sprite = client.getGuiAtlasManager().getSprite(texture);
        ((IDrawContext) (Object) context).meteor$vlPlusDrawColoredSprite(sprite, x, y, 0, width, height, c[0], c[1], c[2], c[3]);
    }

    // Vanilla XP Bar's Prevent Squished Ends: redraws the background texture as 5 pieces (top/
    // bottom/left/right 1px border strips + the center body) instead of vanilla's own single
    // stretched draw. Each border strip gets a local matrix correction, anchored at whichever edge
    // must stay fixed (the far edge from the bar's center), that cancels out just the non-uniform
    // "extra" stretch (rawScaleX/rawScaleY, on top of the element's own overall Scale) along its
    // short axis - keeping its thickness fixed and uniform regardless of how differently Scale X and
    // Scale Y are set, while the body between them (a noisy gradient texture, not a flat color)
    // still stretches normally. Without this, a large scale mismatch visibly smears/elongates the
    // border pixels especially near the bar's rounded corners, reading as "squished" ends.
    private void drawXpBarSliced(DrawContext context, Sprite sprite, int fullWidth, int fullHeight, int frameX, int frameY, int x, int y, int width, int height, float r, float g, float b, float a, double rawScaleX, double rawScaleY) {
        IDrawContext idc = (IDrawContext) (Object) context;
        MatrixStack matrices = context.getMatrices();

        int w = Math.min(width, fullWidth - frameX);
        int h = height;

        // Correcting the taller/wider axis all the way down to a fixed "scale-only" baseline works
        // fine when the bar is stretched TALL relative to its width, but breaks the other direction:
        // if Scale X is shrunk well below Scale Y (a narrow bar), a border strip corrected to that
        // same fixed baseline thickness can end up wider than the whole narrowed bar. Correcting
        // toward whichever raw axis is SMALLER instead - never toward a fixed baseline - fixes both
        // directions symmetrically: the smaller axis is always left uncorrected (correction factor
        // 1), so a border strip can never end up thicker than the bar's own narrower dimension.
        double minRawScale = Math.min(rawScaleX, rawScaleY);
        double correctionX = minRawScale / rawScaleX;
        double correctionY = minRawScale / rawScaleY;

        // Top strip (row 0) - anchored at the top edge, Y-corrected, thickness extends downward.
        pushAxisCorrection(matrices, x, y, 1.0, correctionY);
        idc.meteor$vlPlusDrawColoredFrameSprite(sprite, fullWidth, fullHeight, frameX, frameY, x, y, 0, w, 1, r, g, b, a);
        matrices.pop();

        // Bottom strip (last row) - anchored at the bottom edge, Y-corrected, thickness extends up.
        if (h > 1) {
            pushAxisCorrection(matrices, x, y + h, 1.0, correctionY);
            idc.meteor$vlPlusDrawColoredFrameSprite(sprite, fullWidth, fullHeight, frameX, frameY + h - 1, x, y + h - 1, 0, w, 1, r, g, b, a);
            matrices.pop();
        }

        // Left strip (col 0) - anchored at the left edge, X-corrected, thickness extends right.
        pushAxisCorrection(matrices, x, y, correctionX, 1.0);
        idc.meteor$vlPlusDrawColoredFrameSprite(sprite, fullWidth, fullHeight, frameX, frameY, x, y, 0, 1, h, r, g, b, a);
        matrices.pop();

        // Right strip (last col of this crop) - only a real "end" if this crop reaches the texture's
        // actual right edge (always true for the background, never cropped) - anchored at the right
        // edge, X-corrected, thickness extends left.
        if (w > 1 && frameX + w >= fullWidth) {
            pushAxisCorrection(matrices, x + w, y, correctionX, 1.0);
            idc.meteor$vlPlusDrawColoredFrameSprite(sprite, fullWidth, fullHeight, frameX + w - 1, frameY, x + w - 1, y, 0, 1, h, r, g, b, a);
            matrices.pop();
        }

        // Center body - untouched ambient scale, excluding the border rows/columns already drawn.
        if (w > 2 && h > 2) {
            idc.meteor$vlPlusDrawColoredFrameSprite(sprite, fullWidth, fullHeight, frameX + 1, frameY + 1, x + 1, y + 1, 0, w - 2, h - 2, r, g, b, a);
        }
    }

    // Pushes a matrix correction anchored at (anchorX, anchorY) - that exact point stays fixed on
    // screen, everything drawn relative to it scales by (correctionX, correctionY) on top of
    // whatever ambient scale is already active. Caller must matrices.pop() after drawing.
    private void pushAxisCorrection(MatrixStack matrices, double anchorX, double anchorY, double correctionX, double correctionY) {
        matrices.push();
        matrices.translate(anchorX, anchorY, 0);
        matrices.scale((float) correctionX, (float) correctionY, 1f);
        matrices.translate(-anchorX, -anchorY, 0);
    }

    // Health Bar Adjust: tints the real hearts, same "Colorize" idea as XP Bar Adjust's own texture
    // recolor - the heart TEXTURE'S SHAPE/SHADING stays exactly what it always was (container/full/
    // half/poisoned/etc, hardcore variants included), but drawn from a grayscale-normalized copy
    // (GrayscaleSpriteCache) rather than the original, so the tint multiply can actually reach its
    // real target color instead of only ever darkening toward it - multiplying the original (mostly
    // red, near-zero green/blue) heart texture by e.g. yellow can't add the green channel that
    // isn't there, so it just stayed reddish regardless of the chosen tint. Uses the module's OWN
    // color logic (Fixed/Rainbow/Gradient/Flashing/Hue Shift, Max/Low health) unconditionally
    // whenever the module is active - no separate "apply to vanilla" toggle, since whether this
    // affects anything visible is already controlled by whether the module itself is on. Falls back
    // to the real (non-grayscale) sprite when inactive, still respecting Alpha from a relocated
    // Vanilla Health element if present.
    @Redirect(method = "drawHeart", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"))
    private void redirectDrawHeartColor(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        HealthBarAdjust adjust = Modules.get().get(HealthBarAdjust.class);
        float[] shaderColor = RenderSystem.getShaderColor();
        // The module's own Alpha applies whether or not Vanilla Health is relocated - multiplies
        // with a relocated element's own Alpha (shaderColor[3]) rather than replacing it, so both
        // can be set independently and still combine sensibly.
        float effectiveAlpha = adjust.isActive() ? (float) (shaderColor[3] * adjust.getAlpha()) : shaderColor[3];

        if (!adjust.isActive() && effectiveAlpha >= 1.0f) {
            context.drawGuiTexture(texture, x, y, width, height);
            return;
        }

        if (adjust.isActive()) {
            float progress = client.player != null && client.player.getMaxHealth() > 0 ? client.player.getHealth() / client.player.getMaxHealth() : 1f;
            // CONTAINER is the always-drawn dark/empty heart outline every other heart layer draws
            // on top of (Identifier path always contains "container" - hud/heart/container,
            // container_blinking, container_hardcore, etc, see InGameHud.HeartType) - without this
            // split, Background Color had no effect on real hearts at all (everything got Fill
            // Color uniformly), and full/half/empty hearts all reading the same flat color made
            // them hard to tell apart at a glance - the outline now carries its own color again.
            boolean isContainer = texture.getPath().contains("container");
            Color tint = isContainer ? adjust.getBackgroundColor(Color.WHITE, progress) : adjust.getFillColor(Color.WHITE, progress);

            Identifier grayTexture = GrayscaleSpriteCache.get(texture);
            ((IDrawContext) (Object) context).meteor$vlPlusDrawColoredWholeTexture(grayTexture, x, y, 0, width, height, tint.r / 255f, tint.g / 255f, tint.b / 255f, effectiveAlpha);
            return;
        }

        Sprite sprite = client.getGuiAtlasManager().getSprite(texture);
        ((IDrawContext) (Object) context).meteor$vlPlusDrawColoredSprite(sprite, x, y, 0, width, height, 1f, 1f, 1f, effectiveAlpha);
    }

    // Stamina Bar Adjust: same as Health Bar Adjust above, for the real food/hunger icons.
    @Redirect(method = "renderFood", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"))
    private void redirectDrawFoodColor(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        StaminaBarAdjust adjust = Modules.get().get(StaminaBarAdjust.class);
        float[] shaderColor = RenderSystem.getShaderColor();
        float effectiveAlpha = adjust.isActive() ? (float) (shaderColor[3] * adjust.getAlpha()) : shaderColor[3];

        if (!adjust.isActive() && effectiveAlpha >= 1.0f) {
            context.drawGuiTexture(texture, x, y, width, height);
            return;
        }

        if (adjust.isActive()) {
            float progress = client.player != null ? client.player.getHungerManager().getFoodLevel() / 20f : 1f;
            // The empty/backdrop food icon is always drawn first, every other layer on top of it -
            // its Identifier path always contains "empty" (hud/food_empty, food_empty_hunger) - see
            // redirectDrawHeartColor's own container check above for why this split matters.
            boolean isEmpty = texture.getPath().contains("empty");
            Color tint = isEmpty ? adjust.getBackgroundColor(Color.WHITE, progress) : adjust.getFillColor(Color.WHITE, progress);

            Identifier grayTexture = GrayscaleSpriteCache.get(texture);
            ((IDrawContext) (Object) context).meteor$vlPlusDrawColoredWholeTexture(grayTexture, x, y, 0, width, height, tint.r / 255f, tint.g / 255f, tint.b / 255f, effectiveAlpha);
            return;
        }

        Sprite sprite = client.getGuiAtlasManager().getSprite(texture);
        ((IDrawContext) (Object) context).meteor$vlPlusDrawColoredSprite(sprite, x, y, 0, width, height, 1f, 1f, 1f, effectiveAlpha);
    }

    // Same as above, static variant - renderArmor is a static method, so its own redirect handler
    // has to be static too.
    @Redirect(method = "renderArmor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"))
    private static void redirectDrawGuiTextureAlphaStatic(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        float[] c = RenderSystem.getShaderColor();
        if (c[3] >= 1.0f) {
            context.drawGuiTexture(texture, x, y, width, height);
            return;
        }

        Sprite sprite = MinecraftClient.getInstance().getGuiAtlasManager().getSprite(texture);
        ((IDrawContext) (Object) context).meteor$vlPlusDrawColoredSprite(sprite, x, y, 0, width, height, c[0], c[1], c[2], c[3]);
    }

    // Covers the cropped/frame overload: only the XP bar's own progress/fill texture uses this one,
    // to show partial width. Parameter order matches DrawContext.drawGuiTexture(Identifier, int i,
    // int j, int k, int l, int x, int y, int width, int height) exactly - i/j are the sprite's own
    // reference dimensions, k/l the frame offset, NOT a second width/height pair (easy to
    // mismap - verified against the real call in InGameHud.renderExperienceBar: drawGuiTexture(
    // PROGRESS_TEXTURE, 182, 5, 0, 0, x, y, progressWidth, 5)).
    @Redirect(method = "renderExperienceBar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIIIIIII)V"))
    private void redirectDrawGuiTextureFrameAlpha(DrawContext context, Identifier texture, int fullWidth, int fullHeight, int frameX, int frameY, int x, int y, int width, int height) {
        if (texture.getPath().equals("hud/experience_bar_progress")) {
            VanillaXPBarHud element = findVanillaElement(VanillaXPBarHud.class);
            if (element != null && element.shouldPreventEndSquish() && element.getRawScaleX() != element.getRawScaleY()) {
                float[] c = RenderSystem.getShaderColor();
                Sprite sprite = client.getGuiAtlasManager().getSprite(texture);
                drawXpBarSliced(context, sprite, fullWidth, fullHeight, frameX, frameY, x, y, width, height, c[0], c[1], c[2], c[3], element.getRawScaleX(), element.getRawScaleY());
                return;
            }
        }

        float[] c = RenderSystem.getShaderColor();
        if (c[3] >= 1.0f) {
            context.drawGuiTexture(texture, fullWidth, fullHeight, frameX, frameY, x, y, width, height);
            return;
        }

        Sprite sprite = client.getGuiAtlasManager().getSprite(texture);
        ((IDrawContext) (Object) context).meteor$vlPlusDrawColoredFrameSprite(sprite, fullWidth, fullHeight, frameX, frameY, x, y, 0, width, height, c[0], c[1], c[2], c[3]);
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void onRenderHotbarHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (vlReplaying || ci.isCancelled()) return;

        VanillaHotbarHud element = findVanillaElement(VanillaHotbarHud.class);
        if (element == null) return;

        ci.cancel();
        if (element.isHidden()) return;

        vlDeferred.add(() -> {
            double defaultX = context.getScaledWindowWidth() / 2.0 - 91;
            double defaultY = context.getScaledWindowHeight() - 22;
            double scale = guiScaleFactor(context);
            double rotation = element.getRotation();

            pushVanillaTransform(context,
                element.x / scale, element.y / scale,
                element.getScale() / scale, defaultX, defaultY,
                rotation, pivotX(element, scale), pivotY(element, scale),
                element.alpha.get() / 255.0
            );

            VanillaHudRotationState.hotbarItemCounterRotation = (rotation != 0 && element.keepItemsUpright()) ? -rotation : 0;

            try {
                renderHotbar(context, tickCounter);
            } finally {
                popVanillaTransform(context);
                VanillaHudRotationState.hotbarItemCounterRotation = 0;
            }
        });
    }

    // Vanilla Hotbar's 3 independent alphas (item/background/selector) instead of one shared value.
    // renderHotbar's own drawGuiTexture(Identifier,int,int,int,int) call sites, in bytecode order:
    // ordinal 0 = the main hotbar frame (HOTBAR_TEXTURE), 1 = the selected-slot highlight
    // (HOTBAR_SELECTION_TEXTURE), 2/3 = the offhand slot frame (LEFT/RIGHT - mutually exclusive at
    // runtime, but both count as their own static call site for ordinal purposes) - the attack
    // indicator's own two draws come after these and are intentionally left alone, still only
    // affected by the general Alpha setting above like before.

    @Redirect(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V", ordinal = 0))
    private void redirectHotbarBackground(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        VanillaHotbarHud element = findVanillaElement(VanillaHotbarHud.class);
        if (element != null) RenderSystem.setShaderColor(1f, 1f, 1f, (float) element.getBackgroundAlpha());
        context.drawGuiTexture(texture, x, y, width, height);
    }

    @Redirect(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V", ordinal = 1))
    private void redirectHotbarSelector(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        VanillaHotbarHud element = findVanillaElement(VanillaHotbarHud.class);
        if (element != null) RenderSystem.setShaderColor(1f, 1f, 1f, (float) element.getSelectorAlpha());
        context.drawGuiTexture(texture, x, y, width, height);
    }

    @Redirect(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V", ordinal = 2))
    private void redirectHotbarOffhandLeft(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        VanillaHotbarHud element = findVanillaElement(VanillaHotbarHud.class);
        if (element != null) RenderSystem.setShaderColor(1f, 1f, 1f, (float) element.getBackgroundAlpha());
        context.drawGuiTexture(texture, x, y, width, height);
    }

    @Redirect(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V", ordinal = 3))
    private void redirectHotbarOffhandRight(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        VanillaHotbarHud element = findVanillaElement(VanillaHotbarHud.class);
        if (element != null) RenderSystem.setShaderColor(1f, 1f, 1f, (float) element.getBackgroundAlpha());
        context.drawGuiTexture(texture, x, y, width, height);
    }

    // Items: renderHotbarItem is called once per slot (main hand loop + offhand) - bracketing the
    // whole thing per-call, rather than trying to set the color once before the loop and hope it
    // survives untouched, since item rendering commonly resets shader color internally per item
    // (enchant glint, etc). Also flips ItemRenderAlphaState.forcingTranslucent (see its own class
    // doc) so builtin-model items - player heads, chests, anything routed through
    // BuiltinModelItemRenderer - actually respect this alpha too, not just normal flat-icon items.
    @Inject(method = "renderHotbarItem", at = @At("HEAD"))
    private void onRenderHotbarItemHead(DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        VanillaHotbarHud element = findVanillaElement(VanillaHotbarHud.class);
        if (element == null) return;

        double itemAlpha = element.getItemAlpha();
        RenderSystem.setShaderColor(1f, 1f, 1f, (float) itemAlpha);
        // Unconditional set (not just "if <1 set true") - this runs once per hotbar slot per frame,
        // so if a PREVIOUS slot's own item render threw partway through (a broken/modded custom
        // item renderer) and skipped its own TAIL reset below, this slot's HEAD would otherwise
        // inherit that stuck "true" even when ITS OWN alpha is 1.0 - self-heals every call instead
        // of only on the lucky case where TAIL actually ran last time.
        ItemRenderAlphaState.forcingTranslucent = itemAlpha < 1.0;
    }

    @Inject(method = "renderHotbarItem", at = @At("TAIL"))
    private void onRenderHotbarItemTail(DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        VanillaHotbarHud element = findVanillaElement(VanillaHotbarHud.class);
        if (element == null) return;

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        ItemRenderAlphaState.forcingTranslucent = false;
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void onRenderArmorHead(DrawContext context, PlayerEntity player, int i, int j, int k, int x, CallbackInfo ci) {
        if (vlReplaying || ci.isCancelled()) return;

        VanillaArmorHud element = staticFindVanillaElement(VanillaArmorHud.class);
        if (element == null) return;

        ci.cancel();
        if (element.isHidden()) return;

        vlDeferred.add(() -> {
            double defaultX = x;
            double defaultY = i - (j - 1) * k - 10;
            double scale = MinecraftClient.getInstance().getWindow().getFramebufferWidth() / (double) context.getScaledWindowWidth();

            MatrixStack matrices = context.getMatrices();
            matrices.push();

            try {
                if (element.getRotation() != 0) {
                    double pivotX = (element.x + element.getWidth() / 2.0) / scale;
                    double pivotY = (element.y + element.getHeight() / 2.0) / scale;

                    matrices.translate(pivotX, pivotY, 0);
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) element.getRotation()));
                    matrices.translate(-pivotX, -pivotY, 0);
                }

                matrices.translate(element.x / scale, element.y / scale, 0);
                float contentScale = (float) (element.getScale() / scale);
                matrices.scale(contentScale, contentScale, 1f);
                matrices.translate(-defaultX, -defaultY, 0);

                double rotation = element.getRotation();
                VanillaHudRotationState.statusBarSpriteCounterRotation = (rotation != 0 && element.keepSpritesUpright()) ? -rotation : 0;

                double alpha = element.alpha.get() / 255.0;
                if (alpha < 1.0) RenderSystem.setShaderColor(1f, 1f, 1f, (float) alpha);
                // Unconditional set - see ItemRenderAlphaState's own class doc for why this exists at
                // all (builtin-model items ignore shader alpha unless routed through a translucent layer).
                ItemRenderAlphaState.forcingTranslucent = alpha < 1.0;

                renderArmor(context, player, i, j, k, x);
            } finally {
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                ItemRenderAlphaState.forcingTranslucent = false;
                matrices.pop();
                VanillaHudRotationState.statusBarSpriteCounterRotation = 0;
            }
        });
    }

    private static <T extends HudElement> T staticFindVanillaElement(Class<T> type) {
        for (HudElement element : Hud.get()) {
            if (type.isInstance(element) && element.isActive()) return type.cast(element);
        }

        return null;
    }

    @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
    private void onRenderHealthBarHead(DrawContext context, PlayerEntity player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        if (vlReplaying || ci.isCancelled()) return;

        VanillaHealthHud element = findVanillaElement(VanillaHealthHud.class);
        if (element == null) return;

        ci.cancel();
        if (element.isHidden()) return;

        vlDeferred.add(() -> {
            double scale = guiScaleFactor(context);

            pushVanillaTransform(context,
                element.x / scale, element.y / scale,
                element.getScale() / scale, x, y,
                element.getRotation(), pivotX(element, scale), pivotY(element, scale),
                element.alpha.get() / 255.0
            );

            double rotation = element.getRotation();
            VanillaHudRotationState.statusBarSpriteCounterRotation = (rotation != 0 && element.keepSpritesUpright()) ? -rotation : 0;

            VanillaHudRotationState.spriteRowAnchorX = x;
            VanillaHudRotationState.spriteSpacing = element.getSpacing();

            try {
                renderHealthBar(context, player, x, y, lines, regeneratingHeartIndex, maxHealth, lastHealth, health, absorption, blinking);
            } finally {
                popVanillaTransform(context);
                VanillaHudRotationState.statusBarSpriteCounterRotation = 0;
                VanillaHudRotationState.spriteSpacing = 8;
            }
        });
    }

    // Health's Spacing setting: drawHeart is always called with the same vanilla-computed x for
    // its whole heart slot (background/absorption/regen/regular layers), so redirecting it here -
    // as a pure function of the original x, not a stateful counter - correctly redirects every
    // layer of a given heart to the same new position without drift, even though up to 4 of these
    // calls share one slot.
    @ModifyArg(method = "renderHealthBar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;drawHeart(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/gui/hud/InGameHud$HeartType;IIZZZ)V"), index = 2)
    private int onHealthHeartX(int x) {
        int spacing = VanillaHudRotationState.spriteSpacing;
        if (spacing == 8) return x;

        int n = Math.round((x - VanillaHudRotationState.spriteRowAnchorX) / 8f);
        return VanillaHudRotationState.spriteRowAnchorX + n * spacing;
    }

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void onRenderFoodHead(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo ci) {
        if (vlReplaying || ci.isCancelled()) return;

        VanillaHungerHud element = findVanillaElement(VanillaHungerHud.class);
        if (element == null) return;

        ci.cancel();
        if (element.isHidden()) return;

        vlDeferred.add(() -> {
            double defaultX = right - 81;
            double defaultY = top;
            double scale = guiScaleFactor(context);

            pushVanillaTransform(context,
                element.x / scale, element.y / scale,
                element.getScale() / scale, defaultX, defaultY,
                element.getRotation(), pivotX(element, scale), pivotY(element, scale),
                element.alpha.get() / 255.0
            );

            double rotation = element.getRotation();
            VanillaHudRotationState.statusBarSpriteCounterRotation = (rotation != 0 && element.keepSpritesUpright()) ? -rotation : 0;

            VanillaHudRotationState.spriteRowAnchorX = right - 9;
            VanillaHudRotationState.spriteSpacing = element.getSpacing();

            try {
                renderFood(context, player, top, right);
            } finally {
                popVanillaTransform(context);
                VanillaHudRotationState.statusBarSpriteCounterRotation = 0;
                VanillaHudRotationState.spriteSpacing = 8;
            }
        });
    }

    // Hunger's Spacing setting: same technique as Health's onHealthHeartX above - drawGuiTexture is
    // called up to 3 times per food icon slot (empty/full/half layers), all sharing the same
    // vanilla-computed x, and this is a pure function of that x, so every layer redirects together.
    @ModifyArg(method = "renderFood", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"), index = 1)
    private int onFoodIconX(int x) {
        int spacing = VanillaHudRotationState.spriteSpacing;
        if (spacing == 8) return x;

        int j = Math.round((VanillaHudRotationState.spriteRowAnchorX - x) / 8f);
        return VanillaHudRotationState.spriteRowAnchorX - j * spacing;
    }

    @Inject(method = "renderMountHealth", at = @At("HEAD"), cancellable = true)
    private void onRenderMountHealthHead(DrawContext context, CallbackInfo ci) {
        if (vlReplaying || ci.isCancelled()) return;

        VanillaMountHealthHud element = findVanillaElement(VanillaMountHealthHud.class);
        if (element == null) return;

        ci.cancel();
        if (element.isHidden()) return;

        vlDeferred.add(() -> {
            double defaultX = context.getScaledWindowWidth() / 2.0 + 91 - 81;
            double defaultY = context.getScaledWindowHeight() - 39;
            double scale = guiScaleFactor(context);

            pushVanillaTransform(context,
                element.x / scale, element.y / scale,
                element.getScale() / scale, defaultX, defaultY,
                element.getRotation(), pivotX(element, scale), pivotY(element, scale),
                element.alpha.get() / 255.0
            );

            double rotation = element.getRotation();
            VanillaHudRotationState.statusBarSpriteCounterRotation = (rotation != 0 && element.keepSpritesUpright()) ? -rotation : 0;

            try {
                renderMountHealth(context);
            } finally {
                popVanillaTransform(context);
                VanillaHudRotationState.statusBarSpriteCounterRotation = 0;
            }
        });
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
