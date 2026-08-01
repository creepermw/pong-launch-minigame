package com.example.ponglaunch.client.game;

/**
 * Thrown on the Minecraft render (main) thread when the Pong ball escapes the
 * player's right paddle. It propagates up through {@code LoadingOverlay.render}
 * -&gt; {@code Minecraft.runTick} -&gt; {@code Minecraft.run}, where the default
 * crash handler catches it, writes a crash report (containing this message,
 * including multiple repetitions of "菜就多练") and exits the game.
 *
 * <p>No CrashReport file is forged or tampered with: we only throw an ordinary
 * unchecked exception from the render thread and let the engine handle it.
 */
public final class PongLoseException extends RuntimeException {

    /** Repeated on purpose for the troll effect in the crash report / logs. */
    public static final String TROLL = "菜就多练 菜就多练 菜就多练 菜就多练 菜就多练";

    public PongLoseException() {
        super("You lost! The Pong ball escaped the right paddle and reached the window edge! " + TROLL);
    }
}
