package com.example.ponglaunch.client.gui;

import com.example.ponglaunch.PongLaunchMod;
import com.example.ponglaunch.client.game.PongGame;
import com.example.ponglaunch.client.game.PongLoseException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.TitleScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * The Pong "GUI overlay". Subclasses {@link Gui} (per the required architecture)
 * and overrides {@link #render(GuiGraphics, float)} - the actual 1.20.1
 * {@code Gui.render} signature.
 *
 * <p>Per the decision chain in the plan: {@code RenderGuiOverlayEvent} and
 * {@code RenderLevelStageEvent} are structurally unable to fire during the
 * 1.20.1 loading phase (they require {@code level != null}), so this render
 * method is instead invoked from a Mixin TAIL injection on
 * {@code LoadingOverlay#render} - i.e. after the original loading page has
 * been drawn, before the frame is presented. The in-game HUD is never touched
 * (the vanilla singleton {@code Minecraft.gui} is the only in-game HUD renderer).
 *
 * <p>When the ball escapes, {@link PongLoseException} is thrown here, on the
 * render (main) thread, so the default crash handler produces the troll crash report.
 */
public final class PongGui extends Gui {

    private static final int COLOR_BG = 0xFF101018;
    private static final int COLOR_NET = 0x66FFFFFF;
    private static final int COLOR_AI = 0xFFFF5050;
    private static final int COLOR_PLAYER = 0xFF50FF50;
    private static final int COLOR_BALL = 0xFFFFFFFF;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_GRAY = 0xFFAAAAAA;
    private static final int COLOR_DIM = 0xFF888888;

    private final PongGame game = new PongGame();

    /**
     * Set (from any thread) by the reload-listener callback the moment the
     * initial resource reload completes; applied on the render thread in
     * {@link #render}. Guarantees no loss can occur during the overlay fade-out.
     */
    private volatile boolean loadingComplete;

    private long lastNano;
    private float fpsTimer;
    private int fpsFrames;
    private float fpsAccum;

    public PongGui(Minecraft mc) {
        super(mc, mc.getItemRenderer());
    }

    /**
     * Overridden {@code Gui.render} (1.20.1 signature), invoked by the
     * {@code LoadingOverlay#render} TAIL injection.
     */
    @Override
    public void render(GuiGraphics guiGraphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();

        // Win branch: once the TitleScreen has appeared we never draw again.
        if (game.isFinished()) {
            return;
        }
        // Only render while the launch LoadingOverlay is on screen.
        if (!(mc.getOverlay() instanceof LoadingOverlay)) {
            return;
        }

        // Freeze the instant loading completes (idempotent).
        if (loadingComplete) {
            game.markLoadingComplete();
        }

        var window = mc.getWindow();
        float w = window.getGuiScaledWidth();
        float h = window.getGuiScaledHeight();

        game.setViewport(w, h);
        game.start();

        long now = System.nanoTime();
        float dt = lastNano == 0L ? 1.0f / 60.0f : Math.min((now - lastNano) / 1_000_000_000.0f, 0.05f);
        lastNano = now;

        double mouseGuiY = readRawMouseGuiY(mc, h);
        game.tick(dt, (float) mouseGuiY);

        if (game.isActive() || game.isFrozen()) {
            draw(guiGraphics, mc, w, h);
            logFps(dt, (float) mouseGuiY);
        }

        // Loss branch: throw on the render thread - the default crash handler takes it from here.
        if (game.isLost()) {
            PongLaunchMod.LOGGER.error("[Pong] LOSS TRIGGERED: ball escaped the right paddle! {}", PongLoseException.TROLL);
            throw new PongLoseException();
        }
    }

    /** Called by the reload listener the moment the initial resource reload completes. */
    public void onLoadingComplete() {
        this.loadingComplete = true;
        PongLaunchMod.LOGGER.info("[Pong] Resource reload complete -> freezing Pong (win branch armed, loss disabled).");
    }

    /** Called from {@code ScreenEvent.Opening} when the main menu ({@link TitleScreen}) appears. */
    public void onTitleScreen() {
        if (game.isFinished()) {
            return;
        }
        game.win();
        PongLaunchMod.LOGGER.info("[Pong] TitleScreen detected -> Pong ended. Player SURVIVED! (hits={}, ballSpeed={})",
                game.getHits(), game.getBallSpeed());
    }

    private void draw(GuiGraphics gg, Minecraft mc, float w, float h) {
        Font font = mc.font;
        if (font == null) {
            return; // fonts not ready yet - skip text, still safe
        }

        // The ForgeLoadingOverlay leaves a Y-up framebuffer-space projection active
        // after its render body. Under it, our Y-down gui-space drawing would be
        // vertically flipped and only cover part of the screen. Set a Y-down
        // gui-scaled projection for the minigame drawing and restore the previous
        // one afterwards. The (0.1, -0.1) near/far mirrors Forge's own overlay
        // ortho so z=0 vertices are not clipped.
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0.0F, w, h, 0.0F, 0.1F, -0.1F),
                VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            drawContent(gg, font, w, h);
        } finally {
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }

    private void drawContent(GuiGraphics gg, Font font, float w, float h) {
        // Solid backdrop: this is a full-screen minigame on top of the loading page.
        gg.fill(0, 0, (int) w, (int) h, COLOR_BG);

        // Center dashed net.
        for (int y = 14; y < (int) h - 14; y += 16) {
            gg.fill((int) (w / 2.0f) - 1, y, (int) (w / 2.0f) + 1, Math.min(y + 8, (int) h), COLOR_NET);
        }

        // AI paddle (left, red) and player paddle (right, green).
        int leftX = (int) PongGame.PADDLE_MARGIN;
        int rightX = (int) (w - PongGame.PADDLE_MARGIN - PongGame.PADDLE_WIDTH);
        gg.fill(leftX, (int) game.getLeftPaddleY(),
                leftX + (int) PongGame.PADDLE_WIDTH, (int) (game.getLeftPaddleY() + PongGame.PADDLE_HEIGHT), COLOR_AI);
        gg.fill(rightX, (int) game.getRightPaddleY(),
                rightX + (int) PongGame.PADDLE_WIDTH, (int) (game.getRightPaddleY() + PongGame.PADDLE_HEIGHT), COLOR_PLAYER);

        // Ball.
        int br = (int) (PongGame.BALL_RADIUS * 2.0f);
        int bx = (int) (game.getBallX() - PongGame.BALL_RADIUS);
        int by = (int) (game.getBallY() - PongGame.BALL_RADIUS);
        gg.fill(bx, by, bx + br, by + br, COLOR_BALL);

        // HUD text.
        String title = "PONG";
        gg.drawString(font, title, (int) (w / 2.0f - font.width(title) / 2.0f), 6, COLOR_WHITE);
        String sub = "SURVIVE UNTIL THE MAIN MENU";
        gg.drawString(font, sub, (int) (w / 2.0f - font.width(sub) / 2.0f), 18, COLOR_GRAY);
        String hint = "MOVE MOUSE TO CONTROL THE RIGHT PADDLE";
        gg.drawString(font, hint, (int) (w / 2.0f - font.width(hint) / 2.0f), (int) h - 22, COLOR_DIM);
        String hits = "HITS: " + game.getHits();
        gg.drawString(font, hits, 6, 6, COLOR_GRAY);

        gg.flush();
    }

    /**
     * Reads the raw GLFW cursor position and maps it to gui-scaled Y.
     *
     * <p>Vanilla {@code MouseHandler.onMove} deliberately ignores cursor updates while an
     * overlay is active ({@code if (getOverlay() != null) return;}), so {@code mouseHandler.ypos()}
     * is frozen at its initial value for the whole LoadingOverlay phase. Reading GLFW directly
     * gives the real cursor position so the player can actually steer the paddle on the
     * loading screen.
     */
    private double readRawMouseGuiY(Minecraft mc, float guiHeight) {
        double[] cx = new double[1];
        double[] cy = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().getWindow(), cx, cy);
        int[] winW = new int[1];
        int[] winH = new int[1];
        GLFW.glfwGetWindowSize(mc.getWindow().getWindow(), winW, winH);
        if (winH[0] <= 0) {
            return guiHeight / 2.0;
        }
        // GLFW cursor coords are relative to the window's client area, which is
        // stretched to the framebuffer (gui) space.
        return cy[0] * guiHeight / winH[0];
    }

    private void logFps(float dt, float mouseGuiY) {
        fpsTimer += dt;
        fpsAccum += dt;
        fpsFrames++;
        if (fpsTimer >= 1.0f) {
            int fps = Math.round(fpsFrames / fpsTimer);
            float avgMs = fpsAccum / fpsFrames * 1000.0f;
            int mcFps = Minecraft.getInstance().getFps();
            PongLaunchMod.LOGGER.info("[Pong] FPS: {} (avg frame {} ms over {} frames; Minecraft fps counter: {}; mouseGuiY={})",
                    fps, String.format("%.1f", avgMs), fpsFrames, mcFps, Math.round(mouseGuiY));
            fpsTimer = 0.0f;
            fpsAccum = 0.0f;
            fpsFrames = 0;
        }
    }
}
