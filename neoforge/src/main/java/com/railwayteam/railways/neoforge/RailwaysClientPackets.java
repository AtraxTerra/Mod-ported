// Adapted from chaevsfe / Create Fly: Steam 'n' Rails (https://github.com/chaevsfe/Create-Steam-n-Rails-Fly)
// Based on original work from Create: Steam 'n' Rails by The Railways Team (https://github.com/Layers-of-Railways/Railway)
// Licensed under LGPL-3.0-only — extracted/adapted with AI assistance.
package com.railwayteam.railways.neoforge;

import com.railwayteam.railways.multiloader.PacketSet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client half of the payload handlers: kept out of {@code PacketSetImpl} so a dedicated server,
 * which registers the same handlers, never loads a class that names {@link Minecraft}.
 */
public final class RailwaysClientPackets {
    private RailwaysClientPackets() {
    }

    public static void handle(PacketSet set, FriendlyByteBuf buf) {
        set.handleS2CPacket(Minecraft.getInstance(), buf);
    }
}
