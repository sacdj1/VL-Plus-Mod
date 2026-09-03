/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.events.render;

import net.minecraft.text.Text;

public class RenderTitleEvent {
    private static final RenderTitleEvent INSTANCE = new RenderTitleEvent();

    public Text title;
    public Text subtitle;

    public static RenderTitleEvent get(Text title, Text subtitle) {
        INSTANCE.title = title;
        INSTANCE.subtitle = subtitle;
        return INSTANCE;
    }
}
