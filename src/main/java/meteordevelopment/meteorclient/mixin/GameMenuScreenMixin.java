/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.QuickActions;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void vlAddQuickActionsButton(CallbackInfo ci) {
        Modules modules = Modules.get();
        QuickActions quickActions = modules != null ? modules.get(QuickActions.class) : null;
        if (quickActions == null || !quickActions.pauseMenuButtonEnabled()) return;

        ButtonWidget replacement = ButtonWidget.builder(Text.literal(quickActions.pauseMenuButtonText()), button -> quickActions.open()).build();

        // Takes the Advancements button's exact spot in the vanilla grid (easier to reach than a corner
        // button, and it's one of the least-used buttons on this server) instead of adding a new one -
        // find it by its vanilla text, since the grid layout itself isn't something a mixin can address
        // by name. Falls back to a small corner button if that text isn't found (a different mod already
        // changed/removed it, or a future Minecraft version renames it).
        String advancementsText = Text.translatable("gui.advancements").getString();

        for (Element element : new ArrayList<>(this.children())) {
            if (!(element instanceof ClickableWidget widget) || !advancementsText.equals(widget.getMessage().getString())) continue;

            replacement.setX(widget.getX());
            replacement.setY(widget.getY());
            replacement.setWidth(widget.getWidth());
            replacement.setDimensions(widget.getWidth(), widget.getHeight());

            this.remove(widget);
            this.addDrawableChild(replacement);
            return;
        }

        int width = Math.max(90, this.textRenderer.getWidth(quickActions.pauseMenuButtonText()) + 12);
        replacement.setDimensions(width, 20);
        replacement.setX(this.width - width - 4);
        replacement.setY(4);
        this.addDrawableChild(replacement);
    }
}
