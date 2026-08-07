package com.example.ponglaunch.mixin;

import com.example.ponglaunch.PongLaunchMod;
import com.example.ponglaunch.client.PongLaunchClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraftforge.client.loading.ForgeLoadingOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders the Pong minigame after Forge's loading screen has been drawn.
 *
 * <p>In 1.20.1 the actual launch overlay is {@link ForgeLoadingOverlay} - a subclass of the
 * vanilla {@link net.minecraft.client.gui.screens.LoadingOverlay} that "completely replaces"
 * the {@code render} method and never calls {@code super.render()}. Injecting the vanilla
 * class therefore never fires at runtime. This TAIL injection on {@code ForgeLoadingOverlay#render}
 * runs after Forge's loading page has been drawn, before the frame is presented. The in-game
 * HUD is never touched.
 *
 * <p>{@link #reload} is shadowed only to feed the real loading progress into the minigame's
 * victory meter. Forge classes keep their names in production (not obfuscated), so the shadow
 * resolves in both dev and release environments.
 */
@Mixin(ForgeLoadingOverlay.class)
public abstract class ForgeLoadingOverlayMixin {

    @Shadow(remap = false) // Forge classes keep their names; no obfuscation mapping needed
    @Final
    private ReloadInstance reload;

    private static boolean loggedHook;

    @Inject(method = "render", at = @At("TAIL"), require = 1)
    private void pongLaunch$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!loggedHook) {
            loggedHook = true;
            PongLaunchMod.LOGGER.info("[Pong] Loading overlay render hook active.");
        }
        PongLaunchClient.pongGui().setLoadingProgress(this.reload.getActualProgress());
        PongLaunchClient.pongGui().render(guiGraphics, partialTick);
    }
}
