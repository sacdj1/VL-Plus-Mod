/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.gui.screens.DisguisedPlayersScreen;
import meteordevelopment.meteorclient.mixin.ChatHudAccessor;
import meteordevelopment.meteorclient.utils.misc.VLPlusAdditions;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ButtonSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextVisitFactory;
import net.minecraft.util.Formatting;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NameProtect extends Module {
    private static final String NAME_BASE = "Disguised";

    private static final Map<String, Formatting> RANK_COLORS = Map.of(
        "[VIP+]", Formatting.BLUE,
        "[VIP]", Formatting.DARK_AQUA,
        "[Prem+]", Formatting.GOLD,
        "[Prem]", Formatting.DARK_PURPLE
    );

    // "[GuildTag<guildRankSymbol>]" - confirmed exact symbol set from actual guild-member-list item
    // data: Recruit ✧ (U+2727), Member ✦ (U+2726), Officer ✫ (U+272B), Deputy ✯ (U+272F). Matching
    // this closed set instead of "any single symbol character" is what avoids false-positives on
    // other bracketed tags (tier labels, rank tags, etc.) that happen to end in one symbol.
    // Tolerates a single §-formatting-code pair right after "[" (e.g. "[§7Twk✵]", which is how the
    // server actually sends it).
    private static final Pattern GUILD_PATTERN = Pattern.compile("\\[(?:§.)?([A-Za-z0-9_]{2,16})([✧✦✫✯])\\]");

    // Novice, Journeyman, Veteran, Elite
    private static final Map<String, Formatting> MILESTONE_COLORS = Map.of(
        "✣", Formatting.WHITE,
        "✽", Formatting.WHITE,
        "❋", Formatting.WHITE,
        "❈", Formatting.WHITE
    );

    private final SettingGroup sgSelf = settings.createGroup("Your Player");
    private final SettingGroup sgOthers = settings.createGroup("Other Players");
    private final SettingGroup sgRanks = settings.createGroup("Ranks");

    private final Setting<Boolean> nameProtect = sgSelf.add(new BoolSetting.Builder()
        .name("name-protect")
        .description("Hides your name client-side.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> skinProtect = sgSelf.add(new BoolSetting.Builder()
        .name("skin-protect")
        .description("Make players become Steves.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> revertOnDisable = sgSelf.add(new BoolSetting.Builder()
        .name("revert-chat-on-disable")
        .description("Reverts disguised names back to real names in your chat history when the module is turned off.")
        .defaultValue(true)
        .build()
    );

    private final Setting<NumberingMode> numberingMode = sgSelf.add(new EnumSetting.Builder<NumberingMode>()
        .name("numbering")
        .description("How the numbers after \"Disguised\" are picked, for you and other players.")
        .defaultValue(NumberingMode.Sequential)
        .build()
    );

    private final Setting<Boolean> otherPlayers = sgOthers.add(new BoolSetting.Builder()
        .name("other-players")
        .description("Also disguises other players' names, using the same \"Disguised\" naming and numbering as you.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Void> viewDisguises = sgOthers.add(new ButtonSetting.Builder()
        .name("view-disguises")
        .description("Shows which real player is disguised as which name.")
        .buttonText("View")
        .screen(theme -> new DisguisedPlayersScreen(theme, this))
        .visible(otherPlayers::get)
        .build()
    );

    private final Setting<Boolean> otherPlayersSkins = sgOthers.add(new BoolSetting.Builder()
        .name("other-players-skins")
        .description("Also disguises other players' skins with a random default (Steve/Alex) skin each.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> otherPlayerHeads = sgOthers.add(new BoolSetting.Builder()
        .name("other-players-heads")
        .description("Also disguises player heads belonging to other players, placed in the world or shown as items in menus.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rankColors = VLPlusAdditions.markExperimental(sgRanks.add(new BoolSetting.Builder()
        .name("rank")
        .description("Experimental - colors known server rank tags, like [VIP] or [Prem+], and the player name after them. Not fully reliable yet.")
        .defaultValue(true)
        .build()
    ));

    private final Setting<Boolean> guildTags = sgRanks.add(new BoolSetting.Builder()
        .name("guild")
        .description("Disguises guild tags, like [Miao✵], as \"[Guild1~]\", \"[Guild2~]\" and so on.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> guildRankSymbol = sgRanks.add(new BoolSetting.Builder()
        .name("guild-rank")
        .description("Also hides the guild rank symbol next to a disguised guild tag (Recruit ✧, Member ✦, Officer ✫, Deputy ✯), not just the guild name.")
        .defaultValue(true)
        .visible(guildTags::get)
        .build()
    );

    private final Setting<Boolean> milestoneColors = VLPlusAdditions.markExperimental(sgRanks.add(new BoolSetting.Builder()
        .name("milestone-symbol")
        .description("Experimental - colors milestone rank symbols (✣ Novice, ✽ Journeyman, ❋ Veteran, ❈ Elite). Not fully reliable yet.")
        .defaultValue(true)
        .build()
    ));

    private String username = "If you see this, something is wrong.";
    private String selfName = NAME_BASE;
    private boolean suppressReplacement = false;

    private final Map<String, String> otherPlayerNames = new LinkedHashMap<>();
    private final Map<String, UUID> otherPlayerSkinSeeds = new HashMap<>();
    private final Map<String, String> guildNames = new LinkedHashMap<>();
    private final Map<String, String> guildRealSymbols = new HashMap<>();
    private int nextPlayerNumber = 1;
    private int nextGuildNumber = 1;

    public NameProtect() {
        super(Categories.Player, "disguise", "Hide player names and skins.", "name-protect");
    }

    @Override
    public void onActivate() {
        username = mc.getSession().getUsername();

        otherPlayerNames.clear();
        otherPlayerSkinSeeds.clear();
        guildNames.clear();
        guildRealSymbols.clear();
        nextPlayerNumber = 1;
        nextGuildNumber = 1;

        selfName = NAME_BASE + nextNumber();
    }

    @Override
    public void onDeactivate() {
        if (revertOnDisable.get()) revertChatHistory();
    }

    private String getOtherPlayerName(String realName) {
        return otherPlayerNames.computeIfAbsent(realName, name -> NAME_BASE + nextNumber());
    }

    private int nextNumber() {
        if (numberingMode.get() == NumberingMode.Random) {
            int number;
            String candidate;

            do {
                number = ThreadLocalRandom.current().nextInt(1, 10000);
                candidate = NAME_BASE + number;
            } while (candidate.equals(selfName) || otherPlayerNames.containsValue(candidate));

            return number;
        }

        return nextPlayerNumber++;
    }

    public enum NumberingMode {
        Sequential,
        Random
    }

    public boolean otherPlayersEnabled() {
        return otherPlayers.get();
    }

    public Map<String, String> getOtherPlayerNames() {
        return Collections.unmodifiableMap(otherPlayerNames);
    }

    public void refreshOtherPlayerNames() {
        if (mc.getNetworkHandler() == null) return;

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String realName = entry.getProfile().getName();
            if (!realName.equals(username)) getOtherPlayerName(realName);
        }
    }

    private UUID getOtherPlayerSkinSeed(String realName) {
        return otherPlayerSkinSeeds.computeIfAbsent(realName, name -> UUID.randomUUID());
    }

    public SkinTextures getSkinOverride(GameProfile profile) {
        if (!isActive()) return null;

        if (profile.getName().equals(username)) {
            if (skinProtect.get()) return DefaultSkinHelper.getSkinTextures(profile);
            return null;
        }

        if (otherPlayersSkins.get()) {
            return DefaultSkinHelper.getSkinTextures(getOtherPlayerSkinSeed(profile.getName()));
        }

        return null;
    }

    public ProfileComponent getHeadOverride(ProfileComponent original) {
        if (!isActive() || !otherPlayerHeads.get() || original == null) return null;

        String name = original.name().orElse(null);
        if (name == null || name.equals(username)) return null;

        UUID seed = getOtherPlayerSkinSeed(name);
        return new ProfileComponent(new GameProfile(seed, NAME_BASE));
    }

    /**
     * Runs the given action with name/rank/guild replacement temporarily suppressed - for UI that
     * needs to show REAL names even while Disguise is active, e.g. the "View Disguises" screen.
     * Without this, that screen's own labels get silently rewritten by this same class's global
     * text hook (TextVisitFactoryMixin -> replaceName) just like any other on-screen text would.
     */
    public void withRealNames(Runnable action) {
        boolean previous = suppressReplacement;
        suppressReplacement = true;
        try {
            action.run();
        } finally {
            suppressReplacement = previous;
        }
    }

    public String replaceName(String string) {
        if (string == null || !isActive() || suppressReplacement) return string;

        string = string.replace(username, selfName);

        if (otherPlayers.get() && mc.getNetworkHandler() != null) {
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                String realName = entry.getProfile().getName();
                if (realName.equals(username)) continue;

                if (string.contains(realName)) string = string.replace(realName, getOtherPlayerName(realName));
            }
        }

        if (guildTags.get()) string = disguiseGuilds(string);
        if (rankColors.get()) string = colorizeRanks(string);
        if (milestoneColors.get()) string = colorizeMilestones(string);

        return string;
    }

    private String colorizeMilestones(String string) {
        for (Map.Entry<String, Formatting> entry : MILESTONE_COLORS.entrySet()) {
            String symbol = entry.getKey();
            if (!string.contains(symbol)) continue;

            String code = Formatting.FORMATTING_CODE_PREFIX + "" + entry.getValue().getCode();
            String reset = Formatting.FORMATTING_CODE_PREFIX + "" + Formatting.RESET.getCode();

            string = string.replace(symbol, code + symbol + reset);
        }

        return string;
    }

    private String getGuildName(String realGuild) {
        return guildNames.computeIfAbsent(realGuild, g -> "Guild" + (nextGuildNumber++));
    }

    private String disguiseGuilds(String string) {
        Matcher matcher = GUILD_PATTERN.matcher(string);

        StringBuilder result = new StringBuilder();
        int last = 0;

        while (matcher.find()) {
            String realGuild = matcher.group(1);
            String realSymbol = matcher.group(2);
            guildRealSymbols.putIfAbsent(realGuild, realSymbol);

            String symbol = guildRankSymbol.get() ? "~" : realSymbol;

            result.append(string, last, matcher.start());
            result.append('[').append(getGuildName(realGuild)).append(symbol).append(']');

            last = matcher.end();
        }

        result.append(string.substring(last));
        return result.toString();
    }

    private String colorizeRanks(String string) {
        for (Map.Entry<String, Formatting> entry : RANK_COLORS.entrySet()) {
            String tag = entry.getKey();
            if (!string.contains(tag)) continue;

            String code = Formatting.FORMATTING_CODE_PREFIX + "" + entry.getValue().getCode();
            String reset = Formatting.FORMATTING_CODE_PREFIX + "" + Formatting.RESET.getCode();

            Pattern pattern = Pattern.compile(Pattern.quote(tag) + "( ?)([A-Za-z0-9_]{1,16})?");
            Matcher matcher = pattern.matcher(string);

            StringBuilder result = new StringBuilder();
            int last = 0;

            while (matcher.find()) {
                result.append(string, last, matcher.start());
                result.append(code).append(tag).append(reset);

                String space = matcher.group(1);
                String name = matcher.group(2);

                if (space != null) result.append(space);
                if (name != null) result.append(code).append(name).append(reset);

                last = matcher.end();
            }

            result.append(string.substring(last));
            string = result.toString();
        }

        return string;
    }

    /**
     * Text-returning wrapper around {@link #replaceName(String)} for mixins that need to return a
     * Text rather than a String (tab list, nametags, scoreboard). These are rendered via
     * TextRenderer.draw(Text, ...) -> Text.asOrderedText(), which walks the Text's own Style tree
     * directly (PlainTextContent.Literal.visit() just hands its raw string to the visitor under
     * ONE style - confirmed via decompile) and never reinterprets embedded §-codes the way the
     * String-overload draw path (used by chat) does via TextVisitFactory.visitFormatted.
     *
     * The server already sends the correct rank/milestone colors as §-codes embedded directly in
     * the name string - there's no need to recompute them from RANK_COLORS/MILESTONE_COLORS here
     * (that was the bug: reimplementing coloring from scratch, using a string that had already had
     * its §-codes stripped, produced wrong/missing colors and broke things that used to just work
     * by virtue of the §-codes being left alone). All this needs to do is substitute names/guild
     * tags in place (leaving every other character, § codes included, untouched) and then properly
     * PARSE the result into a real Style tree via TextVisitFactory.visitFormatted, instead of
     * dropping it in as one literal (which never reinterprets those codes) or guessing colors.
     */
    public Text replaceNameText(Text original) {
        if (original == null || !isActive() || suppressReplacement) return original;

        String raw = original.getString();
        String replaced = raw.replace(username, selfName);

        if (otherPlayers.get() && mc.getNetworkHandler() != null) {
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                String realName = entry.getProfile().getName();
                if (realName.equals(username)) continue;

                if (replaced.contains(realName)) replaced = replaced.replace(realName, getOtherPlayerName(realName));
            }
        }

        if (guildTags.get()) replaced = disguiseGuilds(replaced);

        if (replaced.equals(raw)) return original;

        return parseFormatted(replaced, original.getStyle());
    }

    /** Rebuilds a §-code-formatted string into a real Text tree with real Style objects. */
    private Text parseFormatted(String string, Style baseStyle) {
        MutableText result = Text.empty();
        StringBuilder run = new StringBuilder();
        Style[] runStyle = {baseStyle};

        TextVisitFactory.visitFormatted(string, baseStyle, (index, style, codePoint) -> {
            if (!style.equals(runStyle[0])) {
                if (!run.isEmpty()) {
                    result.append(Text.literal(run.toString()).setStyle(runStyle[0]));
                    run.setLength(0);
                }
                runStyle[0] = style;
            }

            run.appendCodePoint(codePoint);
            return true;
        });

        if (!run.isEmpty()) result.append(Text.literal(run.toString()).setStyle(runStyle[0]));

        return result;
    }

    public String getName(String original) {
        if (isActive()) {
            return selfName;
        }

        return original;
    }

    private void revertChatHistory() {
        if (mc.inGameHud == null) return;

        ChatHudAccessor chatHud = (ChatHudAccessor) mc.inGameHud.getChatHud();
        List<ChatHudLine> messages = chatHud.getMessages();

        for (int i = 0; i < messages.size(); i++) {
            ChatHudLine line = messages.get(i);
            String original = line.content().getString();
            String reverted = revertDisguise(original);

            if (!reverted.equals(original)) {
                Text newContent = Text.literal(reverted).setStyle(line.content().getStyle());
                messages.set(i, new ChatHudLine(line.creationTick(), newContent, line.signature(), line.indicator()));
            }
        }

        chatHud.invokeRefresh();
    }

    private String revertDisguise(String string) {
        string = string.replace(selfName, username);

        for (Map.Entry<String, String> entry : otherPlayerNames.entrySet()) {
            string = string.replace(entry.getValue(), entry.getKey());
        }

        for (Map.Entry<String, String> entry : guildNames.entrySet()) {
            String realGuild = entry.getKey();
            String disguisedGuild = entry.getValue();
            String realSymbol = guildRealSymbols.getOrDefault(realGuild, "~");

            string = string.replace("[" + disguisedGuild + "~]", "[" + realGuild + realSymbol + "]");
            string = string.replace("[" + disguisedGuild + realSymbol + "]", "[" + realGuild + realSymbol + "]");
        }

        return string;
    }
}
