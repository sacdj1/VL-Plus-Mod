/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.NameProtect;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The single point where a player's team prefix + name + suffix get combined into one Text -
 * PlayerEntity.getDisplayName() (nametags), PlayerListHud.getPlayerName() (tab list, when the
 * entry has no explicit custom display name) and InGameHud.renderScoreboardSidebar() (scoreboard,
 * called fresh every frame - not baked in once like the old ScoreboardEntry-constructor approach
 * was, which is why disguise never reverted there) all funnel through this. Hooking it here once
 * is simpler and more complete than hooking each of those three call sites separately.
 */
@Mixin(net.minecraft.scoreboard.Team.class)
public abstract class TeamMixin {
    @ModifyReturnValue(method = "decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;", at = @At("RETURN"))
    private static MutableText onDecorateName(MutableText result, AbstractTeam team, Text name) {
        if (Modules.get() == null) return result;

        NameProtect nameProtect = Modules.get().get(NameProtect.class);
        if (!nameProtect.isActive()) return result;

        return (MutableText) nameProtect.replaceNameText(result);
    }
}
