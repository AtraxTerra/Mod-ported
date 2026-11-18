// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.client.render;

import com.jesz.createdieselgenerators.CDGEntityTypes;
import com.jesz.createdieselgenerators.CreateDieselGenerators;
import com.jesz.createdieselgenerators.content.tools.ChemicalSprayerProjectileRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = CreateDieselGenerators.ID, value = Dist.CLIENT)
public final class CDGEntityRenders {
    private CDGEntityRenders() {
    }

    public static void register() {
        // Port: NeoForge registers entity renderers on EntityRenderersEvent.RegisterRenderers, below.
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CDGEntityTypes.CHEMICAL_SPRAYER_PROJECTILE.get(), ChemicalSprayerProjectileRenderer::new);
    }
}
