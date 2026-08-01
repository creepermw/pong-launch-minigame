# Pong Launch Minigame

A silly Minecraft **1.20.1 / Forge** mod that replaces the launch loading screen with a
playable retro **Pong** match against an AI.

> ⚠️ **Warning:** if the ball escapes past your paddle, the game crashes.
> Yes — intentionally. On purpose. *菜就多练.*

## How it works

- While the game loads (after the Mojang logo, before the main menu), a full-screen
  Pong match starts automatically.
- **You** control the right paddle with the mouse; the **AI** controls the left one.
- **Survive until the main menu appears** → the minigame ends gracefully and the game
  continues normally (the ball physics freeze the moment the loading finishes, so you
  can never lose during the fade-out).
- **Miss the ball** (it passes your right paddle) → the mod throws a custom runtime
  exception (`PongLoseException`) on the render thread, the default crash handler
  writes a crash report — complete with **five repetitions of 菜就多练** — and the
  game exits. No save is touched, no file is tampered with.

## Requirements

- Minecraft **1.20.1**
- Forge **47.2.0** (any 1.20.1 `47.x` works)
- Java **17** (to build)

## Build

```powershell
./gradlew.bat build
```

The mod jar is produced at `build/libs/pong-launch-minigame-1.0.0.jar`.

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
- **Win detection**: the main-menu condition is detected via `ScreenEvent.Opening`
  (`net.minecraftforge.client.event.ScreenEvent.Opening`) when the opened screen is a
  `TitleScreen` (`GuiOpenEvent` / `ScreenOpenEvent` do not exist in 1.20.1).
- **Crash effect**: throwing an ordinary unchecked exception from the render thread lets
  the default crash handler produce the report — nothing is forged or rewritten.

## License

The mod code is MIT licensed (see [`LICENSE`](LICENSE)). This project is built from the
official Forge MDK; see [`LICENSE.txt`](LICENSE.txt) and [`CREDITS.txt`](CREDITS.txt)
for the Forge portions.
