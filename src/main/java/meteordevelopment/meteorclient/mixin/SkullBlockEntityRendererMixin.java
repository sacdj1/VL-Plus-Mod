/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.NameProtect;
import net.minecraft.client.render.block.entity.SkullBlockEntityRenderer;
import net.minecraft.component.type.ProfileComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SkullBlockEntityRenderer.class)
public abstract class SkullBlockEntityRendererMixin {
    @ModifyVariable(method = "getRenderLayer", at = @At("HEAD"), argsOnly = true)
    private static ProfileComponent onGetRenderLayer(ProfileComponent profile) {
        if (Modules.get() == null || profile == null) return profile;

        ProfileComponent override = Modules.get().get(NameProtect.class).getHeadOverride(profile);
        return override != null ? override : profile;
    }
}
