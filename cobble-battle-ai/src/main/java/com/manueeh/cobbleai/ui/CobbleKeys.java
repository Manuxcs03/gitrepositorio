package com.manueeh.cobbleai.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class CobbleKeys {
    private CobbleKeys() {}

    private static final String CATEGORY = "key.categories.cobblebattleai";

    public static final KeyMapping MODE = new KeyMapping("key.cobblebattleai.mode",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping EXECUTE = new KeyMapping("key.cobblebattleai.execute",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    public static void register() {
        KeyBindingHelper.registerKeyBinding(MODE);
        KeyBindingHelper.registerKeyBinding(EXECUTE);
    }
}
