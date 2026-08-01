package com.example.ponglaunch;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Pong Launch Minigame - a playable retro Pong game rendered on the Minecraft
 * 1.20.1 launch loading screen (LoadingOverlay phase). If the ball escapes the
 * player's right paddle the game throws a {@link com.example.ponglaunch.client.game.PongLoseException}
 * on the render thread, which the default crash handler turns into a crash report.
 * If the player survives until the TitleScreen appears, the game ends normally.
 */
@Mod(PongLaunchMod.MODID)
public final class PongLaunchMod {
    public static final String MODID = "pong_launch_minigame";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PongLaunchMod() {
        LOGGER.info("[Pong] Pong Launch Minigame initializing (MC 1.20.1 / Forge 47.2.0)");
    }
}
