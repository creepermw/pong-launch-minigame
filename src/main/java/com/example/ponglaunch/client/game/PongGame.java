package com.example.ponglaunch.client.game;

import java.util.Random;

/**
 * Pure Pong game state + physics, with no Minecraft dependencies so it can be
 * reasoned about in isolation.
 *
 * <p>Rules:
 * <ul>
 *   <li>Player controls the RIGHT paddle (mouse position); the LEFT paddle is AI controlled.</li>
 *   <li>If the ball fully passes the right paddle, {@link #isLost()} becomes true and the
 *       renderer throws {@link PongLoseException}.</li>
 *   <li>Physics are frame-time based (frame-rate independent) - unlike the old
 *       WaitingTime reference mod whose ball speed was tied to FPS.</li>
 * </ul>
 */
public final class PongGame {

    public enum State {
        /** Not started yet (no LoadingOverlay render observed). */
        IDLE,
        /** Playing on the loading screen (and during extended play on the title screen). */
        ACTIVE,
        /** Physics stopped after a loss (the renderer throws PongLoseException). */
        FROZEN,
        /** Minigame won: title seen + minimum play time reached. */
        WON
    }

    // geometry (gui-scaled pixels)
    public static final float PADDLE_WIDTH = 6.0f;
    public static final float PADDLE_HEIGHT = 48.0f;
    public static final float PADDLE_MARGIN = 14.0f;
    public static final float BALL_RADIUS = 3.5f;

    /** The minigame always runs at least this long, even if the game finished loading earlier. */
    public static final float MIN_PLAY_SECONDS = 20.0f;

    private static final float BASE_SPEED = 200.0f;
    private static final float MAX_SPEED = 380.0f;
    private static final float SPEED_UP = 1.06f;
    private static final float AI_SPEED_FACTOR = 0.78f;
    private static final float AI_NOISE = 12.0f;      // px of imperfection so the AI is beatable
    private static final float MAX_BOUNCE_ANGLE_DEG = 60.0f;
    private static final float RESPAWN_ANGLE_DEG = 40.0f;
    private static final int TRAIL_LENGTH = 10;       // fading afterimages

    private final Random random = new Random();

    private State state = State.IDLE;
    private boolean loadingComplete;
    private boolean lost;

    private float width;
    private float height;

    private float ballX;
    private float ballY;
    private float ballVX;
    private float ballVY;
    private float ballSpeed;

    private float leftPaddleY;
    private float rightPaddleY;

    private int hits;
    private int aiHits;
    private long startNano = -1L;

    // ring buffer of recent ball positions (oldest at trailIndex)
    private final float[] trailX = new float[TRAIL_LENGTH];
    private final float[] trailY = new float[TRAIL_LENGTH];
    private int trailIndex;

    /** Called every frame with the current gui-scaled window size. */
    public void setViewport(float w, float h) {
        boolean first = width == 0.0f || height == 0.0f;
        this.width = w;
        this.height = h;
        if (first) {
            leftPaddleY = rightPaddleY = h / 2.0f - PADDLE_HEIGHT / 2.0f;
        } else {
            leftPaddleY = clamp(leftPaddleY, 0.0f, h - PADDLE_HEIGHT);
            rightPaddleY = clamp(rightPaddleY, 0.0f, h - PADDLE_HEIGHT);
        }
    }

    /** Starts the game on first LoadingOverlay frame. */
    public void start() {
        if (state != State.IDLE) {
            return;
        }
        state = State.ACTIVE;
        startNano = System.nanoTime();
        respawn(true);
    }

    /**
     * Called when the initial resource reload completes. The game is NOT frozen:
     * it keeps running until the minimum play time ({@link #MIN_PLAY_SECONDS}) has
     * elapsed, even after the main menu has appeared.
     */
    public void markLoadingComplete() {
        this.loadingComplete = true;
    }

    /** Called when the minigame is won (title seen + minimum play time reached). */
    public void win() {
        if (state == State.ACTIVE) {
            state = State.WON;
        }
    }

    /**
     * Advances the simulation by {@code dt} seconds.
     *
     * @param mouseGuiY player mouse Y in gui-scaled coordinates
     */
    public void tick(float dt, float mouseGuiY) {
        if (state != State.ACTIVE) {
            return;
        }

        // Player paddle follows the mouse instantly (clamped to the field).
        rightPaddleY = clamp(mouseGuiY - PADDLE_HEIGHT / 2.0f, 0.0f, height - PADDLE_HEIGHT);

        // AI paddle: chase the ball with capped speed + a little noise so it is beatable.
        float aiTarget = ballY - PADDLE_HEIGHT / 2.0f + (random.nextFloat() - 0.5f) * AI_NOISE;
        float maxMove = ballSpeed * AI_SPEED_FACTOR * dt;
        leftPaddleY = clamp(leftPaddleY + clamp(aiTarget - leftPaddleY, -maxMove, maxMove), 0.0f, height - PADDLE_HEIGHT);

        // Move the ball.
        ballX += ballVX * dt;
        ballY += ballVY * dt;

        // Bounce off top/bottom walls.
        if (ballY - BALL_RADIUS <= 0.0f) {
            ballY = BALL_RADIUS;
            ballVY = -ballVY;
        }
        if (ballY + BALL_RADIUS >= height) {
            ballY = height - BALL_RADIUS;
            ballVY = -ballVY;
        }

        float leftX = PADDLE_MARGIN;
        float rightX = width - PADDLE_MARGIN - PADDLE_WIDTH;

        // Left (AI) paddle hit -> ball bounces toward the player.
        if (ballVX < 0.0f && ballX - BALL_RADIUS <= leftX + PADDLE_WIDTH
                && ballX - BALL_RADIUS >= leftX - 10.0f
                && ballY >= leftPaddleY - 1.0f && ballY <= leftPaddleY + PADDLE_HEIGHT + 1.0f) {
            bounce(leftX + PADDLE_WIDTH + BALL_RADIUS, 1.0f, leftPaddleY);
        }
        // Right (player) paddle hit -> ball bounces toward the AI.
        if (ballVX > 0.0f && ballX + BALL_RADIUS >= rightX
                && ballX + BALL_RADIUS <= rightX + PADDLE_WIDTH + 10.0f
                && ballY >= rightPaddleY - 1.0f && ballY <= rightPaddleY + PADDLE_HEIGHT + 1.0f) {
            bounce(rightX - BALL_RADIUS, -1.0f, rightPaddleY);
        }

        // If the AI ever misses, keep the game alive: respawn the ball toward the player.
        if (ballX + BALL_RADIUS < leftX - 30.0f) {
            respawn(true);
        }

        // LOSS: the ball has fully passed the right paddle ("越过右侧挡板").
        if (ballX - BALL_RADIUS > rightX + PADDLE_WIDTH) {
            lost = true;
            state = State.FROZEN; // stop physics; the renderer throws PongLoseException.
        }

        // Record the ball position for the fading trail.
        trailX[trailIndex] = ballX;
        trailY[trailIndex] = ballY;
        trailIndex = (trailIndex + 1) % TRAIL_LENGTH;
    }

    private void bounce(float newX, float dir, float paddleY) {
        ballX = newX;
        hits++;
        if (dir > 0.0f) {
            aiHits++; // left (AI) paddle got the ball
        }
        ballSpeed = Math.min(ballSpeed * SPEED_UP, MAX_SPEED);
        float rel = (ballY - (paddleY + PADDLE_HEIGHT / 2.0f)) / (PADDLE_HEIGHT / 2.0f);
        rel = clamp(rel, -1.0f, 1.0f);
        float angle = rel * (float) Math.toRadians(MAX_BOUNCE_ANGLE_DEG);
        ballVX = (float) Math.cos(angle) * ballSpeed * dir;
        ballVY = (float) Math.sin(angle) * ballSpeed;
    }

    private void respawn(boolean towardPlayer) {
        ballX = width / 2.0f;
        ballY = height / 2.0f;
        ballSpeed = BASE_SPEED;
        float angle = (random.nextFloat() - 0.5f) * (float) Math.toRadians(RESPAWN_ANGLE_DEG);
        ballVX = (float) Math.cos(angle) * ballSpeed * (towardPlayer ? 1.0f : -1.0f);
        ballVY = (float) Math.sin(angle) * ballSpeed;
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    // --- getters -----------------------------------------------------------

    public State getState() {
        return state;
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }

    public boolean isFinished() {
        return state == State.WON;
    }

    public boolean isLost() {
        return lost;
    }

    public float getBallX() {
        return ballX;
    }

    public float getBallY() {
        return ballY;
    }

    public float getBallSpeed() {
        return ballSpeed;
    }

    public float getLeftPaddleY() {
        return leftPaddleY;
    }

    public float getRightPaddleY() {
        return rightPaddleY;
    }

    public int getHits() {
        return hits;
    }

    public int getAiHits() {
        return aiHits;
    }

    public float getSurvivalSeconds() {
        return startNano < 0L ? 0.0f : (System.nanoTime() - startNano) / 1_000_000_000.0f;
    }

    public int getTrailLength() {
        return TRAIL_LENGTH;
    }

    public int getTrailIndex() {
        return trailIndex;
    }

    public float getTrailX(int i) {
        return trailX[i];
    }

    public float getTrailY(int i) {
        return trailY[i];
    }
}
