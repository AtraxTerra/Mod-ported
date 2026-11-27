package de.mrjulsen.mcdragonlib.neoforge.client;

import de.mrjulsen.mcdragonlib.DragonLib;
import de.mrjulsen.mcdragonlib.client.render.pip.GuiBlockStateRenderState;
import de.mrjulsen.mcdragonlib.client.render.pip.GuiBlockStateRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

/**
 * NeoForge 26.2 port: client registrations the 26.2 rendering needs. Block models drawn inside GUIs
 * (WritableSignScreen) go through a picture-in-picture renderer.
 */
@EventBusSubscriber(modid = DragonLib.MODID, value = Dist.CLIENT)
public final class DragonLibClientRegistrations {
    private DragonLibClientRegistrations() {}

    @SubscribeEvent
    public static void registerPictureInPictureRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(GuiBlockStateRenderState.class, GuiBlockStateRenderer::new);
    }
}
