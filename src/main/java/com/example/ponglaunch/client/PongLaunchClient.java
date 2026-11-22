package com.example.ponglaunch.client;

import com.example.ponglaunch.client.gui.PongGui;
import net.minecraft.client.Minecraft;

/** Shares the startup Pong session between the loading overlay and title screen. */
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
