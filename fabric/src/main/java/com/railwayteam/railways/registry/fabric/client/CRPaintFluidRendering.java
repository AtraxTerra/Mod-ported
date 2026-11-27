/*
 * Steam 'n' Rails
 * Copyright (c) 2026 The Railways Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.railwayteam.railways.registry.fabric.client;

import com.railwayteam.railways.Railways;
import com.railwayteam.railways.neoforge.RailwaysNeoForgeClientEvents;
import com.railwayteam.railways.registry.CRFluids;
import com.railwayteam.railways.shim.create.content.fluids.VirtualFluid;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;

/**
 * Port: paint's fluid model goes to NeoForge's RegisterFluidModelsEvent, which fires after this.
 *
 * <p>Upstream also registers a Fabric {@code FluidVariantRenderHandler} that tints paint by the
 * colour in its custom data. NeoForge 26.2 has no per-stack fluid tint hook (see Create Fly's
 * {@code platform/FluidRendering}, which returns white for the same reason), so paint shows its
 * white texture in tanks, pipes and basins until that exists. Paint's colour itself is untouched:
 * names, recipes, pitchers and painting all read the custom data.
 */
public final class CRPaintFluidRendering {
    private CRPaintFluidRendering() {
    }

    public static void register() {
        VirtualFluid paint = CRFluids.PAINT.get();
        RailwaysNeoForgeClientEvents.FLUID_MODELS.add(event -> event.register(
            new FluidModel.Unbaked(
                new Material(Railways.asResource("fluid/paint_still/white")),
                new Material(Railways.asResource("fluid/paint_flow/white")),
                null,
                null
            ),
            paint
        ));
    }
}
