// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.packets;

import com.jesz.createdieselgenerators.CreateDieselGenerators;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NeoForge: the payload is registered on RegisterPayloadHandlersEvent; handlers run on the main thread. */
@EventBusSubscriber(modid = CreateDieselGenerators.ID)
public class CDGPackets {

    public static void register() {
        // Port: registration happens in onRegisterPayloads below (NeoForge has no global registry).
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(EntityFilterScreenPacket.TYPE, EntityFilterScreenPacket.STREAM_CODEC,
            (payload, context) -> {
                if (context.player() instanceof ServerPlayer player)
                    payload.handle(player);
            });
    }
}
