package com.example.ponglaunch.client.event;

import com.example.ponglaunch.PongLaunchMod;
import com.example.ponglaunch.client.PongLaunchClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Win-branch detection + loading-completion hook + extended-play rendering.
 *
 * <ul>
 *   <li><b>Forge bus</b> - {@link ScreenEvent.Opening} fires right before any screen is
 *       opened. When the opened screen is the main menu ({@link TitleScreen}), the minigame
 *       starts its end sequence (deferred until the minimum play time has elapsed). Note:
 *       the plan's "GuiOpenEvent / ScreenOpenEvent" names do not exist in 1.20.1; the correct
 *       event is {@link ScreenEvent.Opening} with {@link ScreenEvent.Opening#getNewScreen()}.</li>
 *   <li><b>Forge bus</b> - {@link ScreenEvent.Render.Post} keeps the minigame rendering on
 *       top of the main menu after the loading overlay has gone, until the minimum play time
 *       (20s) is reached - "the menu is ready, but you still have to survive".</li>
 *   <li><b>Mod bus</b> - {@link RegisterClientReloadListenersEvent} registers a vanilla
 *       {@link PreparableReloadListener}; its {@code reload} future completes when the
 *       whole initial resource reload is done.</li>
 * </ul>
 */
public final class ClientEvents {

    private ClientEvents() {
    }

    @Mod.EventBusSubscriber(modid = PongLaunchMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void onScreenOpening(ScreenEvent.Opening event) {
            PongLaunchMod.LOGGER.info("[Pong] ScreenEvent.Opening -> {}", event.getNewScreen());
            if (event.getNewScreen() instanceof TitleScreen) {
                PongLaunchClient.pongGui().onTitleScreen();
            }
        }

        /** Extended play: draw the minigame over the main menu once the overlay is gone. */
        @SubscribeEvent
        public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
            if (event.getScreen() instanceof TitleScreen
                    && !(Minecraft.getInstance().getOverlay() instanceof LoadingOverlay)) {
                PongLaunchClient.pongGui().render(event.getGuiGraphics(), event.getPartialTick());
            }
        }
    }

    @Mod.EventBusSubscriber(modid = PongLaunchMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new PongReloadListener());
        }
    }

    /** Signals that the initial resource reload completed (the game keeps playing). */
    private static final class PongReloadListener implements PreparableReloadListener {
        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                              ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            Minecraft mc = Minecraft.getInstance();
            return barrier.wait(null)
                    .thenRun(() -> mc.execute(() -> PongLaunchClient.pongGui().onLoadingComplete()));
        }
    }
}
