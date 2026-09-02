/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.NameProtect;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.component.type.ProfileComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkullBlockEntity.class)
public abstract class SkullBlockEntityMixin {
    @Inject(method = "getOwner", at = @At("RETURN"), cancellable = true)
    private void onGetOwner(CallbackInfoReturnable<ProfileComponent> info) {
        if (Modules.get() == null) return;

        ProfileComponent override = Modules.get().get(NameProtect.class).getHeadOverride(info.getReturnValue());
        if (override != null) info.setReturnValue(override);
    }
}
