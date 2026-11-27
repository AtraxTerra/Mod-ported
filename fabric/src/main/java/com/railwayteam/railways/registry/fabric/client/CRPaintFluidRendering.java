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
import com.railwayteam.railways.content.palettes.PalettesColor;
import com.railwayteam.railways.content.palettes.painting.PaintFluid;
import com.railwayteam.railways.neoforge.RailwaysNeoForgeClientEvents;
import com.railwayteam.railways.registry.CRFluids;
import com.railwayteam.railways.registry.fabric.CRFluidsImpl;
import com.railwayteam.railways.shim.create.content.fluids.VirtualFluid;
import com.railwayteam.railways.util.Utils;
import com.zurrtum.create.client.AllFluidConfigs;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Port: paint's fluid model goes to NeoForge's RegisterFluidModelsEvent, which fires after this.
 *
 * <p>Upstream tints paint by the colour in its custom data through a Fabric
 * {@code FluidVariantRenderHandler}. Here the same colour feeds the two per-stack tint hooks of this
 * platform: Create Fly's own tint table, which its tanks, pipes, basins and spouts read, and the
 * fluid model's NeoForge {@link FluidTintSource}, which NeoForge reads for fluid stacks (fluid
 * container item models, recipe viewers). Like upstream, paint without a colour shows netherite.
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
                PaintTint.INSTANCE
            ),
            paint
        ));
        AllFluidConfigs.TINT.put(paint, (fluid, components) -> color(asGetter(components)));

        if (Utils.isDevEnv()) {
            DataComponentPatch redPaint = DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_DATA, CustomData.of(PaintFluid.setColor(new CompoundTag(), PalettesColor.RED)))
                .build();
            int expectedColor = 0xff000000 | PalettesColor.RED.getDiffuseColor();
            if (AllFluidConfigs.TINT.get(paint).get(paint, redPaint) != expectedColor
                || PaintTint.INSTANCE.colorAsStack(new FluidStack(paint, 1000, redPaint)) != expectedColor) {
                throw new IllegalStateException("Paint fluid component tint runtime check failed");
            }
            Railways.LOGGER.info("Paint fluid model and component-aware tint runtime check passed");
        }
    }

    private static DataComponentGetter asGetter(DataComponentPatch components) {
        return PatchedDataComponentMap.fromPatch(DataComponentMap.EMPTY, components);
    }

    private static int color(DataComponentGetter components) {
        return 0xff000000 | CRFluidsImpl.getPaintColor(components)
            .orElse(PalettesColor.NETHERITE)
            .getDiffuseColor();
    }

    private static final class PaintTint implements FluidTintSource {
        private static final PaintTint INSTANCE = new PaintTint();

        @Override
        public int color(FluidState state) {
            return 0xff000000 | PalettesColor.NETHERITE.getDiffuseColor();
        }

        @Override
        public int colorAsStack(FluidStack stack) {
            return CRPaintFluidRendering.color(stack.getComponents());
        }
    }
}
