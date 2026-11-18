// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.jesz.createdieselgenerators.client.render.AndesiteGirderWrenchPreview;
import com.jesz.createdieselgenerators.client.sound.CDGSounds;
import com.jesz.createdieselgenerators.content.track_layers_bag.TrackLayersBagPlacement;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class CDGClientEvents {
    private CDGClientEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            if (Minecraft.getInstance().level != null) {
                AndesiteGirderWrenchPreview.tick();
                TrackLayersBagPlacement.clientTick();
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> CDGSounds.reset());
    }
}
