// Adapted from chaevsfe / Create Fly: Trackwright (https://github.com/chaevsfe/Trackwright)
// Licensed under GPL-3.0-or-later — extracted/adapted with AI assistance.
package dev.chaevsfe.trackwright.neoforge;

import dev.chaevsfe.trackwright.Trackwright;
import dev.chaevsfe.trackwright.markers.Markers;
import dev.chaevsfe.trackwright.network.TrackwrightNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Port: the NeoForge mod class. Trackwright itself starts through Create Fly's addon entrypoints
 * ({@link TrackwrightEntrypoint}), at the same points as upstream's Fabric entrypoints; this class only
 * hands NeoForge the two things that go through the mod event bus: the chunk attachment type and the
 * network payloads. The client's mod bus events are in {@code TrackwrightClientEvents}.
 */
@Mod(Trackwright.MOD_ID)
public final class TrackwrightNeoForge {
    public TrackwrightNeoForge(IEventBus modBus) {
        Markers.ATTACHMENT_TYPES.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, TrackwrightNetwork::registerPayloads);
    }
}
