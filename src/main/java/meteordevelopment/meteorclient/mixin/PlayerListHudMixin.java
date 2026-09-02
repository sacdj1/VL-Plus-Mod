/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.NameProtect;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Shadow
    protected abstract List<PlayerListEntry> collectPlayerEntries();

    @ModifyConstant(method = "collectPlayerEntries", constant = @Constant(longValue = 80L))
    private long modifyCount(long count) {
        BetterTab module = Modules.get().get(BetterTab.class);
        return module.isActive() ? module.tabSize.get() : count;
    }

    @Inject(method = "getPlayerName", at = @At("HEAD"), cancellable = true)
    public void getPlayerName(PlayerListEntry playerListEntry, CallbackInfoReturnable<Text> info) {
        BetterTab betterTab = Modules.get().get(BetterTab.class);
        if (betterTab.isActive()) {
            info.setReturnValue(betterTab.getPlayerName(playerListEntry));
        }
    }

    // The tab list builds its display name as a structured Text tree, which never goes through
    // TextVisitFactory's legacy §-code string parsing - NameProtect's guild/rank/milestone
    // colorizing (which works by inserting literal §-codes into a flat string) needs its own hook
    // here. @ModifyReturnValue (not @Inject at RETURN) so this still sees BetterTab's replacement
    // name when that's active - BetterTab's own HEAD injection above cancels the method early,
    // and ModifyReturnValue is the one injector type MixinExtras guarantees still fires on that
    // early-return path.
    @ModifyReturnValue(method = "getPlayerName", at = @At("RETURN"))
    private Text onGetPlayerNameNameProtect(Text name) {
        if (Modules.get() == null || name == null) return name;

        return Modules.get().get(NameProtect.class).replaceNameText(name);
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), index = 0)
    private int modifyWidth(int width) {
        BetterTab module = Modules.get().get(BetterTab.class);
        return module.isActive() && module.accurateLatency.get() ? width + 30 : width;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", shift = At.Shift.BEFORE))
    private void modifyHeight(CallbackInfo ci, @Local(ordinal = 5) LocalIntRef rows, @Local(ordinal = 6) LocalIntRef columns) {
        BetterTab module = Modules.get().get(BetterTab.class);
        if (!module.isActive()) return;

        int totalPlayers = collectPlayerEntries().size();
        int newColumns = 1;
        int newRows = totalPlayers;

        while (newRows > module.tabHeight.get()) {
            newColumns++;
            newRows = (totalPlayers + newColumns - 1) / newColumns;
        }

        rows.set(newRows);
        columns.set(newColumns);
    }

    @Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true)
    private void onRenderLatencyIcon(DrawContext context, int width, int x, int y, PlayerListEntry entry, CallbackInfo ci) {
        BetterTab betterTab = Modules.get().get(BetterTab.class);
        if (betterTab.isActive() && betterTab.accurateLatency.get()) {
            TextRenderer textRenderer = mc.textRenderer;
            int latency = MathHelper.clamp(entry.getLatency(), 0, 9999);
            int color = latency < 150 ? 59760 : (latency < 300 ? 15192096 : 14107192);
            String text = latency + "ms";
            context.drawTextWithShadow(textRenderer, text, x + width - textRenderer.getWidth(text), y, color);
            ci.cancel();
        }
    }
}
