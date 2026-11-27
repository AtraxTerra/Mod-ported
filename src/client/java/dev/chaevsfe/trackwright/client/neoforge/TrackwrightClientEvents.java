// Adapted from chaevsfe / Create Fly: Trackwright (https://github.com/chaevsfe/Trackwright)
// Licensed under GPL-3.0-or-later — extracted/adapted with AI assistance.
package dev.chaevsfe.trackwright.client.neoforge;

import dev.chaevsfe.trackwright.Trackwright;
import dev.chaevsfe.trackwright.client.TrackwrightClient;
import dev.chaevsfe.trackwright.client.guide.TrackGuideHud;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Port: the client's mod bus events, for what upstream registers through Fabric API in its client
 * entrypoint: the key mappings (queued by {@link KeyMappings}) and the guide's HUD element, drawn just
 * before the chat as upstream's {@code attachElementBefore(VanillaHudElements.CHAT, ...)}.
 */
@EventBusSubscriber(modid = Trackwright.MOD_ID, value = Dist.CLIENT)
public final class TrackwrightClientEvents {
    private TrackwrightClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KeyMappings.registerAll(event);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT, TrackwrightClient.GUIDE_LAYER, TrackGuideHud::extract);
    }
}
