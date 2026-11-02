/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.utils.render.ItemRenderAlphaState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LidOpenable;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.ChestBlockEntityRenderer;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Function;

/**
 * Chest-family items (chests, trapped/ender chests, and any CIT model whose "parent" chain
 * resolves back to one of those, see BuiltinModelItemRendererMixin's own class doc) render through
 * this block-entity-style renderer, which always picks RenderLayer.getEntityCutout - a layer that
 * ignores RenderSystem.setShaderColor's alpha channel entirely. Forces a translucent layer instead
 * whenever a HUD element's Alpha setting is active on this draw.
 */
@Mixin(ChestBlockEntityRenderer.class)
public abstract class ChestBlockEntityRendererMixin<T extends BlockEntity & LidOpenable> {
    @Redirect(
        method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/util/SpriteIdentifier;getVertexConsumer(Lnet/minecraft/client/render/VertexConsumerProvider;Ljava/util/function/Function;)Lnet/minecraft/client/render/VertexConsumer;"
        )
    )
    private VertexConsumer redirectChestVertexConsumer(SpriteIdentifier spriteIdentifier, VertexConsumerProvider vertexConsumers, Function<Identifier, RenderLayer> layerFactory) {
        if (ItemRenderAlphaState.forcingTranslucent) layerFactory = RenderLayer::getEntityTranslucent;

        return spriteIdentifier.getVertexConsumer(vertexConsumers, layerFactory);
    }
}
