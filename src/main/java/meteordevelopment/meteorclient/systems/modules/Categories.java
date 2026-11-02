/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules;

import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import net.minecraft.item.Items;

public class Categories {
    public static final Category Player = new Category("Player", Items.ARMOR_STAND.getDefaultStack());
    public static final Category Render = new Category("Render", Items.GLASS.getDefaultStack());
    // Split out of Render to keep that tab manageable.
    public static final Category Camera = new Category("Camera", Items.SPYGLASS.getDefaultStack());
    public static final Category Hud = new Category("HUD", Items.PAINTING.getDefaultStack());
    public static final Category Items_ = new Category("Items", Items.CHEST.getDefaultStack());
    public static final Category World = new Category("World", Items.GRASS_BLOCK.getDefaultStack());
    public static final Category Misc = new Category("Misc", Items.LAVA_BUCKET.getDefaultStack());
    public static final Category Bosses = new Category("Bosses", Items.WITHER_SKELETON_SKULL.getDefaultStack());
    public static final Category Debug = new Category("Debug", Items.COMMAND_BLOCK.getDefaultStack());
    // No actual "Cheese" item exists in vanilla - milk bucket is the closest dairy-themed icon available.
    public static final Category Cheese = new Category("Cheese", Items.MILK_BUCKET.getDefaultStack());

    public static boolean REGISTERING;

    public static void init() {
        REGISTERING = true;

        // Meteor
        Modules.registerCategory(Player);
        Modules.registerCategory(Render);
        Modules.registerCategory(Camera);
        Modules.registerCategory(Hud);
        Modules.registerCategory(Items_);
        Modules.registerCategory(World);
        Modules.registerCategory(Misc);
        Modules.registerCategory(Bosses);
        Modules.registerCategory(Debug);
        Modules.registerCategory(Cheese);

        // Addons
        AddonManager.ADDONS.forEach(MeteorAddon::onRegisterCategories);

        REGISTERING = false;
    }
}
