/*
 * Steam 'n' Rails
 * Copyright (c) 2025 The Railways Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.railwayteam.railways.registry.fabric;

import com.railwayteam.railways.Railways;
import com.railwayteam.railways.content.palettes.PalettesColor;
import com.railwayteam.railways.content.palettes.painting.PaintFluid;
import com.railwayteam.railways.neoforge.RailwaysNeoForgeEvents;
import com.railwayteam.railways.shim.create.content.fluids.VirtualFluid;
import com.railwayteam.railways.shim.registrate.util.entry.FluidEntry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.Optional;

public class CRFluidsImpl {
    public static FluidEntry<VirtualFluid> registerPaint() {
        VirtualFluid paint = Registry.register(BuiltInRegistries.FLUID, Railways.asResource("paint"), new Paint());
        return new FluidEntry<>(Railways.asResource("paint"), paint);
    }

    public static void initRendering() {
        com.railwayteam.railways.registry.fabric.client.CRPaintFluidRendering.register();
    }

    /** The paint colour carried by a fluid stack's or resource's custom data. */
    public static Optional<PalettesColor> getPaintColor(DataComponentGetter components) {
        CustomData data = components.get(DataComponents.CUSTOM_DATA);
        return data == null ? Optional.empty() : PaintFluid.getColor(data.copyTag());
    }

    /**
     * Port: NeoForge asks every fluid for its {@link FluidType} (an entity in it, a fluid stack's name,
     * JEI), and the default only knows vanilla's. Paint's type is registered with the other fluid types
     * (see {@link RailwaysNeoForgeEvents}) and names each colour, as upstream's Fabric
     * {@code FluidVariantAttributeHandler} did.
     */
    private static final class Paint extends VirtualFluid {
        @Override
        public FluidType getFluidType() {
            return RailwaysNeoForgeEvents.paintFluidType();
        }
    }
}
