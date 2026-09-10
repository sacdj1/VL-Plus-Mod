/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import meteordevelopment.meteorclient.mixininterface.IDrawContext;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.ItemInfo;
import meteordevelopment.meteorclient.utils.render.VanillaHudRotationState;
import meteordevelopment.meteorclient.utils.tooltip.MeteorTooltipData;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Mixin(value = DrawContext.class)
public abstract class DrawContextMixin implements IDrawContext {
    @Inject(method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;II)V", at = @At(value = "INVOKE", target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD)
    private void onDrawTooltip(TextRenderer textRenderer, List<Text> text, Optional<TooltipData> data, int x, int y, CallbackInfo ci, List<TooltipComponent> list) {
        if (data.isPresent() && data.get() instanceof MeteorTooltipData meteorTooltipData)
            list.add(meteorTooltipData.getComponent());
    }

    @ModifyReceiver(method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;II)V", at = @At(value = "INVOKE", target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"))
    private Optional<TooltipData> onDrawTooltip_modifyIfPresentReceiver(Optional<TooltipData> data, Consumer<TooltipData> consumer) {
        if (data.isPresent() && data.get() instanceof MeteorTooltipData) return Optional.empty();
        return data;
    }

    // VanillaHotbarHud's Keep Items Upright: counter-rotates each item slot's ENTIRE draw (icon,
    // count, durability bar, cooldown overlay, and Item Info's own badges below - all of it) around
    // that item's own 16x16 center, canceling out the hotbar-wide rotation InGameHudMixin applies
    // around the whole bar's center. Rotation composition is associative, so it doesn't matter that
    // this runs "inside" the already-rotated hotbar transform - rotating the local frame back by
    // the same angle here still yields an upright result. A no-op (0 degrees) outside a rotated,
    // Keep-Items-Upright hotbar, or in any other GUI screen, since the shared flag is only ever
    // non-zero for the duration of InGameHud.renderHotbar itself (see VanillaHudRotationState).
    @Inject(method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At("HEAD"))
    private void onDrawItemInSlotRotationHead(TextRenderer textRenderer, ItemStack stack, int x, int y, @Nullable String countOverride, CallbackInfo ci) {
        double angle = VanillaHudRotationState.hotbarItemCounterRotation;
        if (angle == 0) return;

        MatrixStack matrices = ((DrawContext) (Object) this).getMatrices();
        double centerX = x + 8, centerY = y + 8;

        matrices.push();
        matrices.translate(centerX, centerY, 0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) angle));
        matrices.translate(-centerX, -centerY, 0);
    }

    // Same counter-rotation as above, but for the actual item ICON draw call - InGameHud's
    // renderHotbarItem calls this 5-arg drawItem(LivingEntity, ...) SEPARATELY from (and before)
    // drawItemInSlot for the icon itself (count/durability/badges are all drawItemInSlot's own
    // job, drawItemInSlot itself never draws the icon) - without this, the icon inherits only the
    // hotbar's own outer rotation and visibly tilts with the bar, while everything drawItemInSlot
    // draws (Item Info's badges included) correctly stays upright, since only that method was
    // hooked before. Same shared flag/no-op guard as above.
    @Inject(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;III)V", at = @At("HEAD"))
    private void onDrawItemIconRotationHead(net.minecraft.entity.LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        double angle = VanillaHudRotationState.hotbarItemCounterRotation;
        if (angle == 0) return;

        MatrixStack matrices = ((DrawContext) (Object) this).getMatrices();
        double centerX = x + 8, centerY = y + 8;

        matrices.push();
        matrices.translate(centerX, centerY, 0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) angle));
        matrices.translate(-centerX, -centerY, 0);
    }

    @Inject(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;III)V", at = @At("TAIL"))
    private void onDrawItemIconRotationTail(net.minecraft.entity.LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (VanillaHudRotationState.hotbarItemCounterRotation != 0) ((DrawContext) (Object) this).getMatrices().pop();
    }

    // Armor/Health/Hunger/MountHealth's Keep Sprites Upright: all four route every icon they draw
    // through this exact vanilla method (armor pieces, hearts via drawHeart, food, mount hearts),
    // so one shared pair of hooks here covers all four - see VanillaHudRotationState for why a
    // single field is safe to share between them. Same technique as the hotbar item counter-
    // rotation above, just against drawGuiTexture's own x/y/width/height instead of drawItemInSlot's.
    // A no-op when the flag is 0, which includes every OTHER caller of this same vanilla method
    // (XP bar's texture, hotbar background/selection, etc.) - the flag is only ever non-zero for
    // the exact duration of one of those four methods' own Head-to-Tail window.
    @Inject(method = "drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V", at = @At("HEAD"))
    private void onDrawGuiTextureRotationHead(Identifier texture, int x, int y, int width, int height, CallbackInfo ci) {
        double angle = VanillaHudRotationState.statusBarSpriteCounterRotation;
        if (angle == 0) return;

        MatrixStack matrices = ((DrawContext) (Object) this).getMatrices();
        double centerX = x + width / 2.0, centerY = y + height / 2.0;

        matrices.push();
        matrices.translate(centerX, centerY, 0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) angle));
        matrices.translate(-centerX, -centerY, 0);
    }

    @Inject(method = "drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V", at = @At("TAIL"))
    private void onDrawGuiTextureRotationTail(Identifier texture, int x, int y, int width, int height, CallbackInfo ci) {
        if (VanillaHudRotationState.statusBarSpriteCounterRotation != 0) ((DrawContext) (Object) this).getMatrices().pop();
    }

    // Item Info: badges are drawn on top of every slot-rendered item (hotbar and inventory
    // screens both funnel through this one method), and the vanilla durability bar - normally
    // damage-based - is redirected to visualize Quality % instead whenever the module has a
    // Quality reading for this stack and its Quality display isn't off/keypress-gated-and-not-held.
    @Inject(method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
    private void onDrawItemInSlotTail(TextRenderer textRenderer, ItemStack stack, int x, int y, @Nullable String countOverride, CallbackInfo ci) {
        ItemInfo module = Modules.get().get(ItemInfo.class);
        if (module.isActive()) module.renderOverlay((DrawContext) (Object) this, textRenderer, stack, x, y);
    }

    // Declared after onDrawItemInSlotTail above so it pops after that hook's own badge draw - same
    // reasoning as InGameHudMixin's onRenderExperienceBarPositionTail (same-point injectors in one
    // class run in declaration order).
    @Inject(method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
    private void onDrawItemInSlotRotationTail(TextRenderer textRenderer, ItemStack stack, int x, int y, @Nullable String countOverride, CallbackInfo ci) {
        if (VanillaHudRotationState.hotbarItemCounterRotation != 0) ((DrawContext) (Object) this).getMatrices().pop();
    }

    @ModifyExpressionValue(method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isItemBarVisible()Z"))
    private boolean onItemBarVisible(boolean original, TextRenderer textRenderer, ItemStack stack, int x, int y, @Nullable String countOverride) {
        ItemInfo module = Modules.get().get(ItemInfo.class);
        if (module.isActive() && module.getDurabilityBarOverride(stack) != null) return true;
        return original;
    }

    @ModifyExpressionValue(method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getItemBarStep()I"))
    private int onItemBarStep(int original, TextRenderer textRenderer, ItemStack stack, int x, int y, @Nullable String countOverride) {
        ItemInfo module = Modules.get().get(ItemInfo.class);
        if (!module.isActive()) return original;

        ItemInfo.QualityInfo info = module.getDurabilityBarOverride(stack);
        return info != null ? module.getBarStep(info) : original;
    }

    @ModifyExpressionValue(method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getItemBarColor()I"))
    private int onItemBarColor(int original, TextRenderer textRenderer, ItemStack stack, int x, int y, @Nullable String countOverride) {
        ItemInfo module = Modules.get().get(ItemInfo.class);
        if (!module.isActive()) return original;

        ItemInfo.QualityInfo info = module.getDurabilityBarOverride(stack);
        return info != null ? module.getBarColor(info) : original;
    }

    // Vanilla HUD element alpha, part 2: drawGuiTexture's own colorless path (used for hearts/food/
    // armor-point/XP-bar sprites) draws via a shader (position_tex, no vertex color attribute) that
    // - confirmed live, via a diagnostic that logged the shader color right up to the moment of the
    // draw call - does NOT visibly respect RenderSystem.setShaderColor's alpha channel, unlike text
    // and most other 2D draws. drawTexturedQuad's OTHER overload (position_tex_color, vertex color
    // baked in) does support alpha properly - it's package-private, so this exposes it publicly for
    // InGameHudMixin's redirects to call instead of the colorless path whenever an Alpha setting is
    // actually in effect (see the drawGuiTexture-redirecting @Redirect hooks there).
    @Shadow
    abstract void drawTexturedQuad(Identifier texture, int x1, int x2, int y1, int y2, int z, float u1, float u2, float v1, float v2, float red, float green, float blue, float alpha);

    // Replicates onDrawGuiTextureRotationHead/Tail above (Keep Sprites Upright) manually, since
    // this bypasses drawGuiTexture's own body entirely (and with it, those HEAD/TAIL hooks) -
    // without this, Keep Sprites Upright silently stopped doing anything whenever an element's
    // Alpha setting was active, since alpha < 1 is exactly what routes a draw through this method
    // instead of the normal (rotation-hooked) drawGuiTexture path.
    @Override
    public void meteor$vlPlusDrawColoredSprite(Sprite sprite, int x, int y, int z, int width, int height, float red, float green, float blue, float alpha) {
        double angle = VanillaHudRotationState.statusBarSpriteCounterRotation;
        MatrixStack matrices = ((DrawContext) (Object) this).getMatrices();

        if (angle != 0) {
            double centerX = x + width / 2.0, centerY = y + height / 2.0;
            matrices.push();
            matrices.translate(centerX, centerY, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) angle));
            matrices.translate(-centerX, -centerY, 0);
        }

        this.drawTexturedQuad(sprite.getAtlasId(), x, x + width, y, y + height, z, sprite.getMinU(), sprite.getMaxU(), sprite.getMinV(), sprite.getMaxV(), red, green, blue, alpha);

        if (angle != 0) matrices.pop();
    }

    // fullWidth/fullHeight are the sprite's own reference dimensions (UV fraction denominators),
    // frameX/frameY the offset into it (numerators) - separate from width/height, the actual drawn
    // (possibly cropped, e.g. XP bar progress) size. Mirrors DrawContext's own private
    // drawSprite(Sprite, i, j, k, l, x, y, z, width, height) exactly, just with color added.
    @Override
    public void meteor$vlPlusDrawColoredFrameSprite(Sprite sprite, int fullWidth, int fullHeight, int frameX, int frameY, int x, int y, int z, int width, int height, float red, float green, float blue, float alpha) {
        this.drawTexturedQuad(sprite.getAtlasId(), x, x + width, y, y + height, z,
            sprite.getFrameU((float) frameX / fullWidth), sprite.getFrameU((float) (frameX + width) / fullWidth),
            sprite.getFrameV((float) frameY / fullHeight), sprite.getFrameV((float) (frameY + height) / fullHeight),
            red, green, blue, alpha);
    }
}
