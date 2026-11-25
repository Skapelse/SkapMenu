package com.skapclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SkapClient implements ClientModInitializer {
    public static KeyBinding openKey;
    private static boolean pendingOpen = false;
    private static boolean wasDown = false;

    @Override
    public void onInitializeClient() {
        Config.load();

        // 1) Normal Minecraft keybind (shows up in Controls). Default: K
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skapclient.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K,
                KeyBinding.Category.create(Identifier.of("skapclient", "main"))));

        // 2) Chat command fallback: type /skap
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("skap").executes(ctx -> {
                    pendingOpen = true;
                    return 1;
                })));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) pendingOpen = true;

            // 3) Raw K key check, in case the launcher interferes with the keybind system
            if (client.getWindow() != null) {
                boolean down = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;
                if (down && !wasDown && client.currentScreen == null) pendingOpen = true;
                wasDown = down;
            }

            if (pendingOpen) {
                pendingOpen = false;
                if (client.currentScreen == null || client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) {
                    client.setScreen(new SkapScreen(null));
                }
            }
        });
    }
}
