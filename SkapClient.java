package com.skapclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SkapClient implements ClientModInitializer {
    public static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        // K by default (Right Shift is already used by Lunar Client's own menu). Rebind in Controls.
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skapclient.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K,
                KeyBinding.Category.create(Identifier.of("skapclient", "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new SkapScreen(null));
            }
        });
    }
}
