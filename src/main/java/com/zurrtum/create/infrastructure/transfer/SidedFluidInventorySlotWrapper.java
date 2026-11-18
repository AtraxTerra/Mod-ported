package com.zurrtum.create.infrastructure.transfer;

import com.zurrtum.create.infrastructure.fluids.SidedFluidInventory;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.minecraft.core.Direction;

public class SidedFluidInventorySlotWrapper implements ResourceView<FluidResource> {
    private final FluidInventorySlotWrapper slotWrapper;
    private final SidedFluidInventory sidedInventory;
    private final Direction direction;

    SidedFluidInventorySlotWrapper(
        FluidInventorySlotWrapper slotWrapper,
        SidedFluidInventory sidedInventory,
        Direction direction
    ) {
        this.slotWrapper = slotWrapper;
        this.sidedInventory = sidedInventory;
        this.direction = direction;
    }

    @Override
    public long insert(FluidResource resource, long maxAmount, TransactionContext transaction) {
        if (!sidedInventory.canInsert(slotWrapper.slot, FluidInventoryStorage.getCachedStack(resource), direction)) {
            return 0;
        }
        return slotWrapper.insert(resource, maxAmount, transaction);
    }

    @Override
    public long extract(FluidResource resource, long maxAmount, TransactionContext transaction) {
        if (!sidedInventory.canExtract(slotWrapper.slot, FluidInventoryStorage.getCachedStack(resource), direction)) {
            return 0;
        }
        return slotWrapper.extract(resource, maxAmount, transaction);
    }

    @Override
    public boolean isResourceBlank() {
        return slotWrapper.isResourceBlank();
    }

    @Override
    public FluidResource getResource() {
        return slotWrapper.getResource();
    }

    @Override
    public long getAmount() {
        return slotWrapper.getAmount();
    }

    @Override
    public long getCapacity() {
        return slotWrapper.getCapacity();
    }

    @Override
    public ResourceView<FluidResource> getUnderlyingView() {
        return slotWrapper.getUnderlyingView();
    }

    @Override
    public String toString() {
        return "SidedInventorySlotWrapper[%s#%d/%s]".formatted(
            FluidInventoryStorage.toString(sidedInventory),
            slotWrapper.slot,
            direction.name()
        );
    }
}
