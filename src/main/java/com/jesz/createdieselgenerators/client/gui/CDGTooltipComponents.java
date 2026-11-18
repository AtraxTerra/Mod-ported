// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.client.gui;

import com.jesz.createdieselgenerators.CreateDieselGenerators;
import com.jesz.createdieselgenerators.content.track_layers_bag.TrackLayersBagComponent;
import com.jesz.createdieselgenerators.content.track_layers_bag.TrackLayersBagTooltip;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

@EventBusSubscriber(modid = CreateDieselGenerators.ID, value = Dist.CLIENT)
public final class CDGTooltipComponents {
    private CDGTooltipComponents() {
    }

    public static void register() {
        // Port: registered on RegisterClientTooltipComponentFactoriesEvent, below.
    }

    @SubscribeEvent
    public static void onRegisterTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(TrackLayersBagTooltip.class, tooltip -> new TrackLayersBagComponent(tooltip.stack()));
    }
}
