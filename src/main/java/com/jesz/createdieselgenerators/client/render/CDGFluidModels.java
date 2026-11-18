// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.client.render;

import com.jesz.createdieselgenerators.CDGFluids;
import com.jesz.createdieselgenerators.content.fluids.CDGFluidHolder;
import com.jesz.createdieselgenerators.registrate.entry.FluidEntry;
import com.zurrtum.create.client.AllFluidConfigs;
import com.zurrtum.create.infrastructure.fluids.FlowableFluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;

public final class CDGFluidModels {
    private CDGFluidModels() {
    }

    public static void register() {
        register(CDGFluids.PLANT_OIL);
        register(CDGFluids.CRUDE_OIL);
        register(CDGFluids.BIODIESEL);
        register(CDGFluids.DIESEL);
        register(CDGFluids.GASOLINE);
        register(CDGFluids.ETHANOL);
        for (FluidEntry<FlowableFluid> concrete : CDGFluids.CONCRETE)
            register(concrete);
    }

    private static void register(FluidEntry<FlowableFluid> entry) {
        CDGFluidHolder holder = entry.getHolder();
        FluidModel.Unbaked model = new FluidModel.Unbaked(new Material(holder.stillTexture), new Material(holder.flowingTexture), null, null);
        AllFluidConfigs.MODEL.put(holder.still, model);
    }
}
