/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.QuickActions;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends HandledScreen<PlayerScreenHandler> {
    public InventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    // The off hand slot in PlayerScreenHandler sits at (77, 62) relative to this screen's own drawn
    // background (this.x/this.y, the vanilla inventory texture's top-left) - a small button right
    // above it, same width as the 18px slot, so it reads as "belonging" to that slot without covering
    // anything else in the crafting/armor layout around it.
    @Inject(method = "init", at = @At("TAIL"))
    private void vlAddQuickActionsButton(CallbackInfo ci) {
        Modules modules = Modules.get();
        QuickActions quickActions = modules != null ? modules.get(QuickActions.class) : null;
        if (quickActions == null || !quickActions.inventoryButtonEnabled()) return;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("QA"), button -> quickActions.open())
            .dimensions(this.x + 77, this.y + 62 - 16, 18, 14)
            .build());
    }
}
