# Pong Launch Minigame

A silly Minecraft **1.20.1 / Forge** mod that replaces the launch loading screen with a
playable retro **Pong** match against an AI.

> ⚠️ **Warning:** if the ball escapes past your paddle, the game crashes.
> Yes — intentionally. On purpose. *菜就多练.*

## How it works

- While the game loads (after the Mojang logo, before the main menu), a full-screen
  Pong match starts automatically.
- **You** control the right paddle with the mouse; the **AI** controls the left one.
- **Survive at least 20 seconds** → the minigame always runs for a minimum of 20s,
  even if the game finished loading much earlier: once the main menu appears, the
  minigame keeps running on top of it ("THE MENU IS READY - SURVIVE Ns MORE") until
  the 20s are up, then ends gracefully. On slow loads the game simply ends when the
  menu appears (which is already past 20s). Menu clicks and key presses are blocked
  while Pong is active on the title screen.
- **Miss the ball** (it passes your right paddle) → the mod throws a custom runtime
  exception (`PongLoseException`) on the render thread, the default crash handler
  writes a crash report — a full **practice report card** with your stats, **five
  repetitions of 菜就多练**, a "菜" glyph spelled out of the phrase itself, and a
  random piece of advice — then the game exits. No save is touched, no file is tampered with.

## v1.2.0 features

- **Minimum 20s play time**: the minigame cannot end normally before 20 seconds have
  elapsed, even if the loading screen finished long before. The game keeps running
  (playable, still losable) on top of the main menu, with a "SURVIVE Ns MORE" counter.

## v1.1.0 features

- **Victory meter**: a loading-progress bar (real `ReloadInstance` progress) at the
  bottom of the screen — you can see exactly how much longer you must survive.
- **Ball trail**: fading afterimages behind the ball for that retro-arcade feel.
- **AI taunts**: the AI occasionally floats taunts like 「菜就多练」「就这？」 above
  its paddle when it returns the ball.
- **Crash report variety**: every crash includes a practice report card (hits, max
  ball speed, survival time), a 菜 glyph built from the characters of 「菜就多练」,
  and a randomly chosen piece of advice.

## Requirements

- Minecraft **1.20.1**
- Forge **47.2.0** (any 1.20.1 `47.x` works)
- Java **17** (to build)

## Build

```powershell
./gradlew.bat build
```

The mod jar is produced at `build/libs/pong-launch-minigame-1.2.2.jar`.

## Install

Copy the jar into your `.minecraft/mods` folder (client side only) and launch the game.

## Controls

| Action                | Input            |
|-----------------------|------------------|
| Move the right paddle | Mouse (vertical) |

## Technical notes

- **Rendering hook**: the launch overlay in Forge 1.20.1 is
  `net.minecraftforge.client.loading.ForgeLoadingOverlay` — a subclass of the vanilla
  `LoadingOverlay` that completely replaces `render` and never calls `super.render()`.
  The minigame is drawn via a Mixin **TAIL injection** on `ForgeLoadingOverlay#render`,
  i.e. right after Forge's loading page is drawn, before the frame is presented.
- **Mouse control**: vanilla `MouseHandler.onMove` deliberately ignores cursor updates
  while an overlay is active, so the mod reads `glfwGetCursorPos` directly to steer the
  paddle on the loading screen.
- **Title screen handoff**: if the game loads before 20 seconds are up, Forge's
  `ScreenEvent.Render.Pre` lets Pong take over drawing the title screen until
  the challenge ends.
- **Crash effect**: throwing an ordinary unchecked exception from the render thread lets
  the default crash handler produce the report — nothing is forged or rewritten.

## License

The mod code is MIT licensed (see [`LICENSE`](LICENSE)). This project is built from the
official Forge MDK; see [`LICENSE.txt`](LICENSE.txt) and [`CREDITS.txt`](CREDITS.txt)
for the Forge portions.
