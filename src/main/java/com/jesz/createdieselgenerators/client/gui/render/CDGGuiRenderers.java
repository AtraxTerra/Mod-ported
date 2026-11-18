// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.client.gui.render;

import com.jesz.createdieselgenerators.CreateDieselGenerators;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

@EventBusSubscriber(modid = CreateDieselGenerators.ID, value = Dist.CLIENT)
public final class CDGGuiRenderers {
    private CDGGuiRenderers() {
    }

    public static void register() {
        // Port: NeoForge takes PiP renderer factories per state class, on the event below.
    }

    @SubscribeEvent
    public static void onRegisterPictureInPicture(RegisterPictureInPictureRenderersEvent event) {
        event.register(BasinFermentingRenderState.class, BasinFermentingRenderer::new);
        event.register(CastingSpoutRenderState.class, CastingSpoutRenderer::new);
        event.register(DistillationTowerRenderState.class, DistillationTowerRenderer::new);
    }
}
