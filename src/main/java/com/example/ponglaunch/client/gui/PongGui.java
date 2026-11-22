package com.example.ponglaunch.client.gui;

import com.example.ponglaunch.PongLaunchMod;
import com.example.ponglaunch.client.game.PongGame;
import com.example.ponglaunch.client.game.PongLoseException;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/** Renders the same Pong session on Forge's startup overlay and on the title screen. */
public final class PongGui {

    private static final int COLOR_BG = 0xFF101018;
    private static final int COLOR_NET = 0x66FFFFFF;
    private static final int COLOR_AI = 0xFFFF5050;
    private static final int COLOR_PLAYER = 0xFF50FF50;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_GRAY = 0xFFAAAAAA;
    private static final int COLOR_DIM = 0xFF888888;

    // Limit how far the ball can travel between mouse samples when startup stalls.
    private static final float MAX_FRAME_SECONDS = 0.05f;
    private static final String[] TAUNTS = {"菜就多练", "就这？", "再来再来", "就这水平？", "让让你", "多练练"};
    private static final float TAUNT_COOLDOWN = 2.5f;
    private static final float TAUNT_LIFETIME = 1.4f;

    private final Minecraft minecraft;
    private final PongGame game = new PongGame();
    private final Random random = new Random();
    private final double[] cursorY = new double[1];
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f previousModelView = new Matrix4f();

    private String tauntText;
    private float tauntTimer;
    private float tauntCooldown;
    private long lastFrameNano;

    public PongGui(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void renderLoading(GuiGraphics graphics, float progress) {
        if (!game.isFinished() && !game.isLost()) {
            render(graphics, false, Math.max(0.0f, Math.min(1.0f, progress)));
        }
    }

    public boolean renderTitle(GuiGraphics graphics) {
        return isPlaying() && render(graphics, true, 1.0f);
    }

    public boolean isPlaying() {
        return game.isActive();
    }

    private boolean render(GuiGraphics graphics, boolean onTitle, float progress) {
        float survived = game.getSurvivalSeconds();
        if ((onTitle || minecraft.screen instanceof TitleScreen) && survived >= PongGame.MIN_PLAY_SECONDS) {
            game.win();
            PongLaunchMod.LOGGER.info("[Pong] Player survived {}s (hits={}, maxSpeed={})",
                    Math.round(survived), game.getHits(), Math.round(game.getMaxBallSpeed()));
            return false;
        }

        Window window = minecraft.getWindow();
        int width = window.getGuiScaledWidth();
        int height = window.getGuiScaledHeight();
        int screenHeight = window.getScreenHeight();
        if (screenHeight <= 0 || !game.setViewport(width, height)) {
            lastFrameNano = 0L;
            return false;
        }

        game.start();
        long now = System.nanoTime();
        float dt = lastFrameNano == 0L ? 0.0f : Math.min((now - lastFrameNano) / 1_000_000_000.0f, MAX_FRAME_SECONDS);
        lastFrameNano = now;

        // The loading overlay suppresses vanilla cursor updates. GLFW reports logical
        // window coordinates, which must be scaled independently of the framebuffer.
        GLFW.glfwGetCursorPos(window.getWindow(), null, cursorY);
        float mouseY = (float) (cursorY[0] * height / screenHeight);
        int previousAiHits = game.getAiHits();
        game.tick(dt, mouseY);
        if (game.isLost()) {
            PongLaunchMod.LOGGER.error("[Pong] Ball escaped the player's paddle. {}", PongLoseException.TROLL);
            throw new PongLoseException(game.getHits(), game.getMaxBallSpeed(), game.getSurvivalSeconds());
        }

        updateTaunt(dt, game.getAiHits() != previousAiHits);
        draw(graphics, width, height, onTitle, progress, survived);
        return true;
    }

    private void updateTaunt(float dt, boolean aiHit) {
        tauntTimer -= dt;
        tauntCooldown -= dt;
        if (aiHit && tauntCooldown <= 0.0f) {
            tauntText = TAUNTS[random.nextInt(TAUNTS.length)];
            tauntTimer = TAUNT_LIFETIME;
            tauntCooldown = TAUNT_COOLDOWN;
        }
    }

    @SuppressWarnings("deprecation") // GuiGraphics.drawManaged batches fills in Minecraft 1.20.1.
    private void draw(GuiGraphics graphics, int width, int height, boolean onTitle, float progress, float survived) {
        // Flush the caller's geometry before changing its matrices.
        graphics.flush();
        Matrix4f previousProjection = RenderSystem.getProjectionMatrix();
        VertexSorting previousSorting = RenderSystem.getVertexSorting();
        Matrix4f modelView = RenderSystem.getModelViewMatrix();
        previousModelView.set(modelView);

        RenderSystem.setProjectionMatrix(projection.setOrtho(0.0f, width, height, 0.0f, 0.1f, -0.1f),
                VertexSorting.DISTANCE_TO_ORIGIN);
        modelView.identity();
        graphics.pose().pushPose();
        graphics.pose().setIdentity();
        try {
            // GuiGraphics normally flushes each fill. Submit all Pong rectangles together.
            graphics.drawManaged(() -> drawShapes(graphics, width, height, progress));
            if (minecraft.font != null) {
                drawText(graphics, minecraft.font, width, height, onTitle, progress, survived);
            }
        } finally {
            graphics.pose().popPose();
            modelView.set(previousModelView);
            RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
        }
    }

    private void drawShapes(GuiGraphics graphics, int width, int height, float progress) {
        graphics.fill(0, 0, width, height, COLOR_BG);

        int middleX = width / 2;
        for (int y = 14; y < height - 14; y += 16) {
            graphics.fill(middleX - 1, y, middleX + 1, y + 8, COLOR_NET);
        }

        int leftX = (int) PongGame.PADDLE_MARGIN;
        int paddleWidth = (int) PongGame.PADDLE_WIDTH;
        int paddleHeight = (int) PongGame.PADDLE_HEIGHT;
        int rightX = width - leftX - paddleWidth;
        int leftY = (int) game.getLeftPaddleY();
        int rightY = (int) game.getRightPaddleY();
        graphics.fill(leftX, leftY, leftX + paddleWidth, leftY + paddleHeight, COLOR_AI);
        graphics.fill(rightX, rightY, rightX + paddleWidth, rightY + paddleHeight, COLOR_PLAYER);

        int ballSize = (int) (PongGame.BALL_RADIUS * 2.0f);
        int trailCount = game.getTrailCount();
        // The newest sample is the current ball, which is drawn opaque below.
        for (int i = 0; i < trailCount - 1; i++) {
            int alpha = 28 + 132 * (i + 1) / trailCount;
            int x = (int) (game.getTrailX(i) - PongGame.BALL_RADIUS);
            int y = (int) (game.getTrailY(i) - PongGame.BALL_RADIUS);
            graphics.fill(x, y, x + ballSize, y + ballSize, (alpha << 24) | 0xFFFFFF);
        }
        int ballX = (int) (game.getBallX() - PongGame.BALL_RADIUS);
        int ballY = (int) (game.getBallY() - PongGame.BALL_RADIUS);
        graphics.fill(ballX, ballY, ballX + ballSize, ballY + ballSize, COLOR_WHITE);

        int barWidth = (int) (width * 0.45f);
        int barX = (width - barWidth) / 2;
        int barY = height - 16;
        graphics.fill(barX, barY, barX + barWidth, barY + 5, COLOR_NET);
        graphics.fill(barX, barY, barX + (int) (barWidth * progress), barY + 5, COLOR_PLAYER);
    }

    private void drawText(GuiGraphics graphics, Font font, int width, int height,
                          boolean onTitle, float progress, float survived) {
        if (tauntTimer > 0.0f) {
            float rise = (TAUNT_LIFETIME - tauntTimer) * 10.0f;
            int y = (int) (game.getLeftPaddleY() - 12.0f - rise);
            int alpha = Math.min(255, Math.max(4, (int) (255.0f * tauntTimer / 0.4f)));
            graphics.drawString(font, tauntText, (int) (PongGame.PADDLE_MARGIN + PongGame.PADDLE_WIDTH) + 6,
                    y, (alpha << 24) | 0xFF5050);
        }

        String status = "SURVIVE UNTIL THE MAIN MENU";
        if (onTitle) {
            int remaining = (int) Math.ceil(PongGame.MIN_PLAY_SECONDS - survived);
            status = "THE MENU IS READY - SURVIVE " + remaining + "s MORE";
        }

        graphics.drawCenteredString(font, "PONG", width / 2, 6, COLOR_WHITE);
        graphics.drawCenteredString(font, status, width / 2, 18, COLOR_GRAY);
        graphics.drawCenteredString(font, "MOVE MOUSE TO CONTROL THE RIGHT PADDLE", width / 2, height - 34, COLOR_DIM);
        graphics.drawCenteredString(font, "LOADING " + Math.round(progress * 100.0f) + "%", width / 2, height - 24, COLOR_GRAY);
        graphics.drawString(font, "HITS: " + game.getHits(), 6, 6, COLOR_GRAY);
    }
}
