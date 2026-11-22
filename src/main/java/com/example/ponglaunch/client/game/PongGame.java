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
 *   <li>Physics advance in bounded steps to keep collisions reliable on slow frames.</li>
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
    private static final float LEFT_CONTACT_X = PADDLE_MARGIN + PADDLE_WIDTH + BALL_RADIUS;

    /** The minigame always runs at least this long, even if the game finished loading earlier. */
    public static final float MIN_PLAY_SECONDS = 20.0f;

    private static final float BASE_SPEED = 200.0f;
    private static final float MAX_SPEED = 380.0f;
    private static final float SPEED_UP = 1.06f;
    private static final float AI_SPEED_FACTOR = 0.78f;
    private static final float AI_NOISE = 12.0f;      // px of imperfection so the AI is beatable
    private static final float MAX_BOUNCE_ANGLE_DEG = 60.0f;
    private static final float RESPAWN_ANGLE_DEG = 40.0f;
    private static final float MAX_STEP_SECONDS = 1.0f / 120.0f;
    private static final int TRAIL_LENGTH = 10;       // fading afterimages

    private final Random random = new Random();

    private State state = State.IDLE;
    private float width;
    private float height;

    private float ballX;
    private float ballY;
    private float ballVX;
    private float ballVY;
    private float ballSpeed;
    private float maxBallSpeed;

    private float leftPaddleY;
    private float rightPaddleY;

    private int hits;
    private int aiHits;
    private long startNano;

    // Ring buffer of recent ball positions. trailIndex is the next slot to write.
    private final float[] trailX = new float[TRAIL_LENGTH];
    private final float[] trailY = new float[TRAIL_LENGTH];
    private int trailIndex;
    private int trailCount;

    /** Updates the GUI-scaled playfield and reports whether it fits the paddles. */
    public boolean setViewport(float w, float h) {
        if (w <= 2.0f * LEFT_CONTACT_X || h < PADDLE_HEIGHT) {
            return false;
        }
        if (width == w && height == h) {
            return true;
        }
        if (width == 0.0f) {
            leftPaddleY = rightPaddleY = (h - PADDLE_HEIGHT) / 2.0f;
        } else {
            leftPaddleY = clamp(leftPaddleY, 0.0f, h - PADDLE_HEIGHT);
            rightPaddleY = clamp(rightPaddleY, 0.0f, h - PADDLE_HEIGHT);
            ballX = clamp(ballX * w / width, LEFT_CONTACT_X, w - LEFT_CONTACT_X);
            ballY = clamp(ballY * h / height, BALL_RADIUS, h - BALL_RADIUS);
            trailCount = 0;
        }
        this.width = w;
        this.height = h;
        return true;
    }

    /** Starts the game on first LoadingOverlay frame. */
    public void start() {
        if (state != State.IDLE || width == 0.0f) {
            return;
        }
        state = State.ACTIVE;
        startNano = System.nanoTime();
        maxBallSpeed = BASE_SPEED;
        respawn();
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

        int steps = (int) Math.ceil(dt / MAX_STEP_SECONDS);
        if (steps == 0) {
            return;
        }
        float stepSeconds = dt / steps;
        float aiOffset = (random.nextFloat() - 0.5f) * AI_NOISE;
        for (int i = 0; i < steps && isActive(); i++) {
            step(stepSeconds, aiOffset);
        }

        trailX[trailIndex] = ballX;
        trailY[trailIndex] = ballY;
        trailIndex = (trailIndex + 1) % TRAIL_LENGTH;
        trailCount = Math.min(trailCount + 1, TRAIL_LENGTH);
    }

    private void step(float dt, float aiOffset) {
        // AI paddle: chase the ball with capped speed + a little noise so it is beatable.
        float aiTarget = ballY - PADDLE_HEIGHT / 2.0f + aiOffset;
        float maxMove = ballSpeed * AI_SPEED_FACTOR * dt;
        leftPaddleY = clamp(leftPaddleY + clamp(aiTarget - leftPaddleY, -maxMove, maxMove), 0.0f, height - PADDLE_HEIGHT);

        // Move the ball.
        ballX += ballVX * dt;
        ballY += ballVY * dt;

        // Bounce off top/bottom walls.
        if (ballY < BALL_RADIUS) {
            ballY = 2.0f * BALL_RADIUS - ballY;
            ballVY = -ballVY;
        } else if (ballY > height - BALL_RADIUS) {
            ballY = 2.0f * (height - BALL_RADIUS) - ballY;
            ballVY = -ballVY;
        }

        float leftX = PADDLE_MARGIN;
        float rightX = width - PADDLE_MARGIN - PADDLE_WIDTH;

        // Left (AI) paddle hit -> ball bounces toward the player.
        if (ballVX < 0.0f && ballX - BALL_RADIUS <= leftX + PADDLE_WIDTH
                && ballX - BALL_RADIUS >= leftX - 10.0f
                && ballY >= leftPaddleY - 1.0f && ballY <= leftPaddleY + PADDLE_HEIGHT + 1.0f) {
            bounce(leftX + PADDLE_WIDTH + BALL_RADIUS, 1.0f, leftPaddleY);
        } else if (ballVX > 0.0f && ballX + BALL_RADIUS >= rightX
                && ballX + BALL_RADIUS <= rightX + PADDLE_WIDTH + 10.0f
                && ballY >= rightPaddleY - 1.0f && ballY <= rightPaddleY + PADDLE_HEIGHT + 1.0f) {
            // Right (player) paddle hit -> ball bounces toward the AI.
            bounce(rightX - BALL_RADIUS, -1.0f, rightPaddleY);
        }

        // If the AI ever misses, keep the game alive: respawn the ball toward the player.
        if (ballX + BALL_RADIUS < leftX - 30.0f) {
            respawn();
        }

        // LOSS: the ball has fully passed the right paddle ("越过右侧挡板").
        if (ballX - BALL_RADIUS > rightX + PADDLE_WIDTH) {
            state = State.FROZEN; // stop physics; the renderer throws PongLoseException.
        }
    }

    private void bounce(float newX, float dir, float paddleY) {
        ballX = newX;
        hits++;
        if (dir > 0.0f) {
            aiHits++; // left (AI) paddle got the ball
        }
        ballSpeed = Math.min(ballSpeed * SPEED_UP, MAX_SPEED);
        maxBallSpeed = Math.max(maxBallSpeed, ballSpeed);
        float rel = (ballY - (paddleY + PADDLE_HEIGHT / 2.0f)) / (PADDLE_HEIGHT / 2.0f);
        rel = clamp(rel, -1.0f, 1.0f);
        float angle = rel * (float) Math.toRadians(MAX_BOUNCE_ANGLE_DEG);
        ballVX = (float) Math.cos(angle) * ballSpeed * dir;
        ballVY = (float) Math.sin(angle) * ballSpeed;
    }

    private void respawn() {
        ballX = width / 2.0f;
        ballY = height / 2.0f;
        ballSpeed = BASE_SPEED;
        float angle = (random.nextFloat() - 0.5f) * (float) Math.toRadians(RESPAWN_ANGLE_DEG);
        ballVX = (float) Math.cos(angle) * ballSpeed;
        ballVY = (float) Math.sin(angle) * ballSpeed;
        trailCount = 0;
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
        return state == State.FROZEN;
    }

    public float getBallX() {
        return ballX;
    }

    public float getBallY() {
        return ballY;
    }

    public float getMaxBallSpeed() {
        return maxBallSpeed;
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
        return state == State.IDLE ? 0.0f : (System.nanoTime() - startNano) / 1_000_000_000.0f;
    }

    public int getTrailCount() {
        return trailCount;
    }

    public float getTrailX(int i) {
        return trailX[trailSlot(i)];
    }

    public float getTrailY(int i) {
        return trailY[trailSlot(i)];
    }

    private int trailSlot(int i) {
        return (trailIndex - trailCount + TRAIL_LENGTH + i) % TRAIL_LENGTH;
    }
}
