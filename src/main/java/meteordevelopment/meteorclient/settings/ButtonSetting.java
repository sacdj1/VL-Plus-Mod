/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.gui.utils.IScreenFactory;
import net.minecraft.nbt.NbtCompound;

public class ButtonSetting extends Setting<Void> {
    public final String buttonText;
    public final Runnable action;
    public final IScreenFactory screenFactory;

    public ButtonSetting(String name, String description, String buttonText, Runnable action, IScreenFactory screenFactory, IVisible visible) {
        super(name, description, null, null, null, visible);

        this.buttonText = buttonText;
        this.action = action;
        this.screenFactory = screenFactory;
    }

    @Override
    protected void resetImpl() {}

    @Override
    protected Void parseImpl(String str) {
        return null;
    }

    @Override
    protected boolean isValueValid(Void value) {
        return true;
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        return tag;
    }

    @Override
    protected Void load(NbtCompound tag) {
        return null;
    }

    public void press() {
        if (action != null) action.run();
    }

    public static class Builder extends SettingBuilder<Builder, Void, ButtonSetting> {
        private String buttonText = "Click";
        private Runnable action;
        private IScreenFactory screenFactory;

        public Builder() {
            super(null);
        }

        public Builder buttonText(String buttonText) {
            this.buttonText = buttonText;
            return this;
        }

        public Builder action(Runnable action) {
            this.action = action;
            return this;
        }

        public Builder screen(IScreenFactory screenFactory) {
            this.screenFactory = screenFactory;
            return this;
        }

        @Override
        public ButtonSetting build() {
            return new ButtonSetting(name, description, buttonText, action, screenFactory, visible);
        }
    }
}
