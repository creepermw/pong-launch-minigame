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
    private final java.util.Random random = new java.util.Random();

    /** Real loading progress (0..1) fed from the overlay's reload instance. -1 = unknown. */
    private float loadingProgress = -1.0f;
    private float progressLogTimer;
    private boolean titleSeen;

    // AI taunt state
    private static final String[] TAUNTS = {"菜就多练", "就这？", "再来再来", "就这水平？", "让让你", "多练练"};
    private static final float TAUNT_COOLDOWN = 2.5f;
    private static final float TAUNT_LIFETIME = 1.4f;
    private String tauntText;
    private float tauntTimer;
    private float tauntCooldown;
    private int lastAiHits;

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

        if (game.isFinished()) {
            return;
        }

        boolean overlayPhase = mc.getOverlay() instanceof LoadingOverlay;
        boolean extendedPhase = titleSeen && mc.screen instanceof TitleScreen;

        // If the player has left the (covered) main menu after the loading finished,
        // end the game gracefully - they moved on.
        if (titleSeen && !overlayPhase && !(mc.screen instanceof TitleScreen)) {
            finishWin("Player left the loading-screen game");
            return;
        }
        // Render only during the launch overlay, or during the extended play phase
        // drawn on top of the main menu.
        if (!overlayPhase && !extendedPhase) {
            return;
        }

        // The loading may have completed; the game keeps running until the
        // minimum play time regardless.
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

        updateTaunt(dt);
        logProgress(dt);
        checkWin();

        if (game.isActive()) {
            draw(guiGraphics, mc, w, h);
            logFps(dt, (float) mouseGuiY);
        }

        // Loss branch: throw on the render thread - the default crash handler takes it from here.
        if (game.isLost()) {
            PongLaunchMod.LOGGER.error("[Pong] LOSS TRIGGERED: ball escaped the right paddle! {}", PongLoseException.TROLL);
            throw new PongLoseException(game.getHits(), game.getBallSpeed(), game.getSurvivalSeconds());
        }
    }

    /** Feeds the real loading progress (0..1) from the overlay's reload instance. */
    public void setLoadingProgress(float progress) {
        this.loadingProgress = progress;
    }

    /** Pops an AI taunt when the AI hits the ball, respecting the cooldown. */
    private void updateTaunt(float dt) {
        if (game.getAiHits() > lastAiHits) {
            lastAiHits = game.getAiHits();
            if (tauntCooldown <= 0.0f) {
                tauntText = TAUNTS[random.nextInt(TAUNTS.length)];
                tauntTimer = TAUNT_LIFETIME;
                tauntCooldown = TAUNT_COOLDOWN;
                PongLaunchMod.LOGGER.debug("[Pong] AI taunt: {}", tauntText);
            }
        }
        if (tauntCooldown > 0.0f) {
            tauntCooldown -= dt;
        }
        if (tauntTimer > 0.0f) {
            tauntTimer -= dt;
        }
    }

    /** Debug-level progress telemetry (invisible at the production INFO level). */
    private void logProgress(float dt) {
        if (loadingProgress < 0.0f) {
            return;
        }
        progressLogTimer += dt;
        if (progressLogTimer >= 2.0f) {
            progressLogTimer = 0.0f;
            PongLaunchMod.LOGGER.debug("[Pong] loading progress {}%", Math.round(loadingProgress * 100.0f));
        }
    }

    /** Called by the reload listener the moment the initial resource reload completes. */
    public void onLoadingComplete() {
        this.loadingComplete = true;
        PongLaunchMod.LOGGER.info("[Pong] Resource reload complete -> extended play until {}s minimum.",
                Math.round(PongGame.MIN_PLAY_SECONDS));
    }

    /**
     * Called from {@code ScreenEvent.Opening} when the main menu ({@link TitleScreen}) appears.
     * The game does not end yet: it keeps running on top of the menu until the minimum
     * play time has been reached.
     */
    public void onTitleScreen() {
        if (game.isFinished()) {
            return;
        }
        titleSeen = true;
        PongLaunchMod.LOGGER.info("[Pong] TitleScreen detected at {}s - extended play until {}s.",
                String.format("%.1f", game.getSurvivalSeconds()), Math.round(PongGame.MIN_PLAY_SECONDS));
        checkWin();
    }

    /** Ends the game (win) once the title has appeared AND the minimum play time elapsed. */
    private void checkWin() {
        if (game.isFinished() || !titleSeen) {
            return;
        }
        if (game.getSurvivalSeconds() >= PongGame.MIN_PLAY_SECONDS) {
            finishWin("Minigame complete - Player SURVIVED");
        }
    }

    private void finishWin(String reason) {
        game.win();
        PongLaunchMod.LOGGER.info("[Pong] {} (hits={}, survived={}s, ballSpeed={})",
                reason, game.getHits(),
                String.format("%.1f", game.getSurvivalSeconds()), Math.round(game.getBallSpeed()));
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

        // Ball trail: fading afterimages (oldest dimmest, newest brightest).
        int br = (int) (PongGame.BALL_RADIUS * 2.0f);
        int trailLen = game.getTrailLength();
        int trailStart = game.getTrailIndex();
        for (int i = 0; i < trailLen; i++) {
            int idx = (trailStart + i) % trailLen;
            int alpha = 28 + (132 * (i + 1)) / trailLen; // ~28..160
            int tx = (int) (game.getTrailX(idx) - PongGame.BALL_RADIUS);
            int ty = (int) (game.getTrailY(idx) - PongGame.BALL_RADIUS);
            gg.fill(tx, ty, tx + br, ty + br, (alpha << 24) | 0xFFFFFF);
        }

        // Ball.
        int bx = (int) (game.getBallX() - PongGame.BALL_RADIUS);
        int by = (int) (game.getBallY() - PongGame.BALL_RADIUS);
        gg.fill(bx, by, bx + br, by + br, COLOR_BALL);

        // AI taunt: floating text near the left paddle.
        if (tauntText != null && tauntTimer > 0.0f) {
            float rise = (TAUNT_LIFETIME - tauntTimer) * 10.0f; // floats upward
            int ty = (int) (game.getLeftPaddleY() - 12.0f - rise);
            int alpha = tauntTimer < 0.4f ? (int) (255.0f * tauntTimer / 0.4f) : 255;
            gg.drawString(font, tauntText, leftX + (int) PongGame.PADDLE_WIDTH + 6, ty, (alpha << 24) | 0xFF5050);
        }

        // HUD text.
        String title = "PONG";
        gg.drawString(font, title, (int) (w / 2.0f - font.width(title) / 2.0f), 6, COLOR_WHITE);
        boolean extended = titleSeen && !(Minecraft.getInstance().getOverlay() instanceof LoadingOverlay);
        String sub;
        if (extended) {
            int remain = Math.max(0, Math.round(PongGame.MIN_PLAY_SECONDS - game.getSurvivalSeconds()));
            sub = "THE MENU IS READY - SURVIVE " + remain + "s MORE";
        } else {
            sub = "SURVIVE UNTIL THE MAIN MENU";
        }
        gg.drawString(font, sub, (int) (w / 2.0f - font.width(sub) / 2.0f), 18, COLOR_GRAY);
        String hint = "MOVE MOUSE TO CONTROL THE RIGHT PADDLE";
        gg.drawString(font, hint, (int) (w / 2.0f - font.width(hint) / 2.0f), (int) h - 34, COLOR_DIM);
        String hits = "HITS: " + game.getHits();
        gg.drawString(font, hits, 6, 6, COLOR_GRAY);

        // Real loading progress bar (victory meter).
        if (loadingProgress >= 0.0f) {
            float prog = Math.max(0.0f, Math.min(1.0f, loadingProgress));
            int barW = (int) (w * 0.45f);
            int barX = (int) (w / 2.0f - barW / 2.0f);
            int barY = (int) h - 16;
            gg.fill(barX, barY, barX + barW, barY + 5, 0x66FFFFFF);          // track
            gg.fill(barX, barY, barX + (int) (barW * prog), barY + 5, COLOR_PLAYER); // fill
            String pct = "LOADING " + Math.round(prog * 100.0f) + "%";
            gg.drawString(font, pct, (int) (w / 2.0f - font.width(pct) / 2.0f), (int) h - 24, COLOR_GRAY);
        }

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
