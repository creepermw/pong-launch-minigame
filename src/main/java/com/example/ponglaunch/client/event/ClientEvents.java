package com.example.ponglaunch.client.event;

import com.example.ponglaunch.PongLaunchMod;
import com.example.ponglaunch.client.PongLaunchClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PongLaunchMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof TitleScreen && Minecraft.getInstance().getOverlay() == null
                && PongLaunchClient.pongGui().renderTitle(event.getGuiGraphics())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (isCoveredTitle(event.getScreen())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (isCoveredTitle(event.getScreen())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (isCoveredTitle(event.getScreen())) {
            event.setCanceled(true);
        }
    }

    private static boolean isCoveredTitle(Screen screen) {
        return screen instanceof TitleScreen && PongLaunchClient.pongGui().isPlaying();
    }
}
