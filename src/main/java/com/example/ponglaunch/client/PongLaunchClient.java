package com.example.ponglaunch.client;

import com.example.ponglaunch.client.gui.PongGui;
import net.minecraft.client.Minecraft;

/**
 * Client-side holder so the mixin (render hook) and the event handlers
 * ({@code ScreenOpenEvent}, reload listener) share one {@link PongGui} instance.
 */
public final class PongLaunchClient {

    private static PongGui pongGui;

    private PongLaunchClient() {
    }

    public static PongGui pongGui() {
        if (pongGui == null) {
            pongGui = new PongGui(Minecraft.getInstance());
        }
        return pongGui;
    }
}
