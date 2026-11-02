/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.utils.render.ItemRenderAlphaState;
import net.minecraft.block.SkullBlock;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.SkullBlockEntityRenderer;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Items whose CIT/resource-pack model resolves to "parent": "builtin/entity" (player heads,
 * chests, banners, shields, etc, see BuiltinModelItemRenderer) bypass ItemRenderer's normal baked-
 * quad path entirely (see ItemRendererMixin's own item-alpha fix) - they render through this
 * completely separate Java-code-driven subsystem instead, which picks its own render layer per
 * item type. This mixin covers the skull/player-head case specifically - see
 * ChestBlockEntityRendererMixin for the chest case.
 */
@Mixin(BuiltinModelItemRenderer.class)
public abstract class BuiltinModelItemRendererMixin {
    // SkullBlockEntityRenderer.getRenderLayer only returns a translucent (alpha-respecting) layer
    // for a PLAYER skull with an already-resolved owner profile - every other case (other skull
    // types, or a player head with no profile - the common case for a decorative/CIT-reskinned
    // head with no real owner attached) falls back to a cutout layer that ignores
    // RenderSystem.setShaderColor's alpha channel, same root cause as the BlockItem case
    // ItemRendererMixin already fixes.
    @Redirect(
        method = "render(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/block/entity/SkullBlockEntityRenderer;getRenderLayer(Lnet/minecraft/block/SkullBlock$SkullType;Lnet/minecraft/component/type/ProfileComponent;)Lnet/minecraft/client/render/RenderLayer;"
        )
    )
    private RenderLayer redirectSkullRenderLayer(SkullBlock.SkullType type, ProfileComponent profile) {
        if (!ItemRenderAlphaState.forcingTranslucent) return SkullBlockEntityRenderer.getRenderLayer(type, profile);

        Identifier texture = SkullBlockEntityRendererAccessor.getTextures().get(type);
        return RenderLayer.getEntityTranslucent(texture);
    }
}
