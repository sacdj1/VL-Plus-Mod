/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.ItemDisplaySettings;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.utils.render.ItemRenderAlphaState;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @ModifyArgs(
        method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/ItemRenderer;renderBakedItemModel(Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/item/ItemStack;IILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;)V"
        )
    )
    private void modifyEnchant(Args args, ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model) {
        if (!Modules.get().get(NoRender.class).noEnchantGlint()) return;
        boolean bl = (renderMode == ModelTransformationMode.GUI || renderMode.isFirstPerson() || !(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof TransparentBlock) && !(blockItem.getBlock() instanceof StainedGlassPaneBlock));
        args.set(5, vertexConsumers.getBuffer(RenderLayers.getItemLayer(stack, bl)));
    }

    // HUD item alpha (HudRenderer.item()): RenderLayers.getItemLayer only ever picks a translucent
    // (alpha-respecting) render layer for non-BlockItems - any BlockItem (stone, chests, any
    // placeable block) routes through RenderLayers.getEntityBlockLayer instead, which for most
    // blocks resolves to a solid/cutout layer whose shader doesn't apply the alpha channel of
    // RenderSystem.setShaderColor the same way the translucent layer does. Forces the same
    // translucent layer non-BlockItems already correctly fade with, whenever a HUD element's Alpha
    // setting is active - without this, block-type item icons silently ignored Alpha entirely.
    @ModifyArgs(
        method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/ItemRenderer;renderBakedItemModel(Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/item/ItemStack;IILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;)V"
        )
    )
    private void modifyForItemAlpha(Args args, ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model) {
        if (!ItemRenderAlphaState.forcingTranslucent) return;

        args.set(5, vertexConsumers.getBuffer(TexturedRenderLayers.getEntityTranslucentCull()));
    }

    // ItemDisplaySettings module: nudges or replaces the model's own display transform right before
    // it's applied - runs after whichever BakedModel a resource pack's CIT model swap already picked
    // (model is captured straight off this very method's own parameter list), so it layers on top of
    // that instead of fighting it or needing any CIT-specific handling at all.
    @Redirect(
        method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/model/json/Transformation;apply(ZLnet/minecraft/client/util/math/MatrixStack;)V"
        )
    )
    private void vlplus$applyDisplayOverride(Transformation transformation, boolean leftHanded, MatrixStack matrices, ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded2, MatrixStack matrices2, VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model) {
        ItemDisplaySettings module = Modules.get().get(ItemDisplaySettings.class);
        Transformation adjusted = module != null ? module.apply(stack, model, transformation) : transformation;

        adjusted.apply(leftHanded, matrices);
    }
}
