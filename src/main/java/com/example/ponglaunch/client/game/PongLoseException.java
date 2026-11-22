package com.example.ponglaunch.client.game;

import java.util.Random;

/**
 * Thrown on the Minecraft render (main) thread when the Pong ball escapes the
 * player's right paddle. It propagates up through {@code LoadingOverlay.render}
 * -&gt; {@code Minecraft.runTick} -&gt; {@code Minecraft.run}, where the default
 * crash handler catches it, writes a crash report (containing this message,
 * including multiple repetitions of "菜就多练") and exits the game.
 *
 * <p>No CrashReport file is forged or tampered with: we only throw an ordinary
 * unchecked exception from the render thread and let the engine handle it.
 *
 * <p>The message is a small troll "practice report card": the player's stats,
 * the required repetitions of 菜就多练, a "菜" glyph assembled out of the phrase
 * characters, and a randomly picked piece of advice.
 */
public final class PongLoseException extends RuntimeException {

    /** Repeated on purpose for the troll effect in the crash report / logs. */
    public static final String TROLL = "菜就多练 菜就多练 菜就多练 菜就多练 菜就多练";

    private static final String[] ADVICE = {
            "建议：多练。真的。",
            "提示：挡板跟着鼠标走。",
            "别灰心，等待加载也是一种技术活。",
            "下次试试：球往哪跑，鼠标就往哪追。",
            "本局评价：操作很有灵性，建议多练。",
            "温馨提示：鼠标动快一点，球就追不上你了。",
    };

    private static final Random RANDOM = new Random();

    public PongLoseException(int hits, float maxBallSpeed, float survivalSeconds) {
        super(buildMessage(hits, maxBallSpeed, survivalSeconds));
    }

    private static String buildMessage(int hits, float maxBallSpeed, float survivalSeconds) {
        return "You lost! The Pong ball escaped the right paddle!\n"
                + "==================================================\n"
                + "              【 练 习 成 绩 单 】\n"
                + "  击球数 Hits      : " + hits + "\n"
                + "  最高球速 Max spd : " + Math.round(maxBallSpeed) + "\n"
                + "  存活时间 Survived: " + String.format("%.1f", survivalSeconds) + "s\n"
                + "  评级 Grade       : " + TROLL + "\n"
                + "==================================================\n"
                + "用「菜就多练」拼成的「菜」：\n"
                + CAI_ART
                + ADVICE[RANDOM.nextInt(ADVICE.length)];
    }

    /** A 9x10 pixel "菜" glyph filled with the cycling phrase characters. */
    private static final String CAI_ART = buildCaiArt();

    private static String buildCaiArt() {
        String[] rows = {
                "..#.#.#..",
                ".#######.",
                "..#.#.#..",
                "...###...",
                "...###...",
                "....#....",
                "...#.#...",
                "..#####..",
                "..#.#.#..",
                "..#...#..",
        };
        String phrase = "菜就多练";
        StringBuilder sb = new StringBuilder();
        int ci = 0;
        for (String row : rows) {
            for (char c : row.toCharArray()) {
                if (c == '#') {
                    sb.append(phrase.charAt(ci % phrase.length()));
                    ci++;
                } else {
                    sb.append(' ');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
