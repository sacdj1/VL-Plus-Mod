/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.events.render;

import net.minecraft.text.Text;

/**
 * Posted exactly once whenever the server actually (re)sets the title or subtitle - i.e. vanilla's
 * own InGameHud.setTitle/setSubtitle being called - not once per rendered frame while one happens
 * to still be on screen (see RenderTitleEvent, which fired continuously for the whole fade-in/stay/
 * fade-out duration). Carries both current fields regardless of which one actually just changed, so
 * a listener always has the full current pair.
 */
public class TitleSetEvent {
    private static final TitleSetEvent INSTANCE = new TitleSetEvent();

    public Text title;
    public Text subtitle;

    public static TitleSetEvent get(Text title, Text subtitle) {
        INSTANCE.title = title;
        INSTANCE.subtitle = subtitle;
        return INSTANCE;
    }
}
