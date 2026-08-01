package com.example.ponglaunch.client.event;

import com.example.ponglaunch.PongLaunchMod;
import com.example.ponglaunch.client.PongLaunchClient;
import net.minecraft.client.Minecraft;
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
 * Win-branch detection + loading-completion hook.
 *
 * <ul>
 *   <li><b>Forge bus</b> - {@link ScreenEvent.Opening} fires right before any screen is
 *       opened. When the opened screen is the main menu ({@link TitleScreen}, set by the
 *       LoadingOverlay once the initial resource reload completes), the minigame ends
 *       gracefully (win branch, highest priority - it never crashes). Note: the plan's
 *       "GuiOpenEvent / ScreenOpenEvent" names do not exist in 1.20.1; the correct event
 *       is {@link ScreenEvent.Opening} with {@link ScreenEvent.Opening#getNewScreen()}.</li>
 *   <li><b>Mod bus</b> - {@link RegisterClientReloadListenersEvent} registers a vanilla
 *       {@link PreparableReloadListener}; its {@code reload} future completes when the
 *       whole initial resource reload is done, which freezes the minigame so no loss can
 *       occur during the overlay fade-out (guarantees the "title screen wins" rule).</li>
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

    /** Freezes the minigame the instant the initial resource reload completes. */
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
