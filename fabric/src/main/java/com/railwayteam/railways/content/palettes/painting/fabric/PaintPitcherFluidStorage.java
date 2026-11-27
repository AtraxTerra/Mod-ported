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

package com.railwayteam.railways.content.palettes.painting.fabric;

import com.railwayteam.railways.content.palettes.PalettesColor;
import com.railwayteam.railways.content.palettes.painting.PaintFluid;
import com.railwayteam.railways.content.palettes.painting.PaintPitcherItem;
import com.railwayteam.railways.content.palettes.painting.PitcherColor;
import com.railwayteam.railways.registry.CRFluids;
import com.zurrtum.create.platform.FluidUnits;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import static com.railwayteam.railways.content.palettes.painting.PaintPitcherItem.FLUID_PER_LEVEL;
import static com.railwayteam.railways.content.palettes.painting.PaintPitcherItem.MAX_LEVELS;

/**
 * Port: upstream's Fabric {@code SingleSlotStorage<FluidVariant>} as NeoForge's one-slot
 * {@code ResourceHandler<FluidResource>}, with the same rules: paint of the pitcher's colour (or
 * sandy water) goes in and out a level at a time, a level being {@link PaintPitcherItem#FLUID_PER_LEVEL}.
 *
 * <p>NeoForge capabilities count millibuckets while Create Fly, and so the pitcher's own constants,
 * count droplets. A level is 125 mB either way; amounts are converted here and nowhere else.
 */
class PaintPitcherFluidStorage implements ResourceHandler<FluidResource> {
    /** One level of the pitcher in millibuckets (FLUID_PER_LEVEL droplets, an eighth of a bucket). */
    private static final int MB_PER_LEVEL = FluidUnits.toMillibuckets(FLUID_PER_LEVEL);

    private final ItemAccess context;

    public PaintPitcherFluidStorage(ItemAccess context) {
        this.context = context;
    }

    private @Nullable PitcherColor getColor() {
        if (context.getResource().getItem() instanceof PaintPitcherItem item) {
            return new PitcherColor(item.getColor());
        }
        return null;
    }

    private int getLevels() {
        ItemStack stack = context.getResource().toStack();
        if (!(stack.getItem() instanceof PaintPitcherItem item)) return 0;
        return item.getLevels(stack);
    }

    private static @Nullable PitcherColor getVariantColor(FluidResource resource) {
        if (Fluids.WATER.isSame(resource.getFluid()))
            return PitcherColor.SANDY_WATER;
        if (!CRFluids.PAINT.get().isSame(resource.getFluid()))
            return null;
        CustomData data = resource.get(DataComponents.CUSTOM_DATA);
        PalettesColor fluidColor = data == null ? null : PaintFluid.getColor(data.copyTag()).orElse(null);
        if (fluidColor == null)
            return null;
        return new PitcherColor(fluidColor);
    }

    @Nullable
    private PitcherColor getColorIfValid(FluidResource resource) {
        PitcherColor color = getColor();
        PitcherColor fluidColor = getVariantColor(resource);
        if (fluidColor == null) return null;

        // Color mismatches can never be inserted or extracted
        if (color != null && !color.equals(fluidColor)) {
            return null;
        }

        return color == null ? fluidColor : color;
    }

    private ItemResource makeFilledVariant(PitcherColor color, int levels) {
        return ItemResource.of(color.getItemEntry().get()
            .copyAsFilledStack(context.getResource().toStack(), levels));
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        ItemStack stack = context.getResource().toStack();
        if (!(stack.getItem() instanceof PaintPitcherItem item)) return FluidResource.EMPTY;
        if (item.getLevels(stack) == 0) return FluidResource.EMPTY;
        PalettesColor color = item.getColor();
        return color == null ? FluidResource.of(Fluids.WATER) : FluidResource.of(
            CRFluids.PAINT.get(),
            DataComponentPatch.builder()
                .set(
                    DataComponents.CUSTOM_DATA,
                    CustomData.of(PaintFluid.setColor(new CompoundTag(), item.getColor()))
                )
                .build()
        );
    }

    @Override
    public long getAmountAsLong(int index) {
        return (long) getLevels() * MB_PER_LEVEL;
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return (long) MAX_LEVELS * MB_PER_LEVEL;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return getColorIfValid(resource) != null;
    }

    @Override
    public int insert(int index, FluidResource resource, int maxAmount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, maxAmount);
        PitcherColor color = getColorIfValid(resource);
        if (color == null) return 0;

        int currentLevels = getLevels();
        int levelCapacity = MAX_LEVELS - currentLevels;
        if (levelCapacity <= 0) return 0;

        int filledLevels = Math.min(maxAmount / MB_PER_LEVEL, levelCapacity);
        if (filledLevels <= 0) return 0;

        ItemResource newVariant = makeFilledVariant(color, currentLevels + filledLevels);
        if (context.exchange(newVariant, 1, transaction) == 1) {
            return filledLevels * MB_PER_LEVEL;
        }
        return 0;
    }

    @Override
    public int extract(int index, FluidResource resource, int maxAmount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, maxAmount);
        PitcherColor color = getColorIfValid(resource);
        if (color == null) return 0;

        int currentLevels = getLevels();
        int drainedLevels = Math.min(maxAmount / MB_PER_LEVEL, currentLevels);
        if (drainedLevels <= 0) return 0;

        ItemResource newVariant = makeFilledVariant(color, currentLevels - drainedLevels);
        if (context.exchange(newVariant, 1, transaction) == 1) {
            return drainedLevels * MB_PER_LEVEL;
        }
        return 0;
    }

    @Override
    public String toString() {
        return "PaintPitcherFluidStorage[" + context + "]";
    }
}
