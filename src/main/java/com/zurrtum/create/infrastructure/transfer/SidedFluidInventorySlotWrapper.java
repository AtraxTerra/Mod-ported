package com.zurrtum.create.infrastructure.transfer;

import com.zurrtum.create.infrastructure.fluids.SidedFluidInventory;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.minecraft.core.Direction;

/**
 * One slot of a {@link SidedFluidInventory}, seen from a particular face.
 *
 * <p>A decorator over {@link FluidInventorySlotWrapper} that adds the per-face insert and extract
 * filtering. Upstream implements Fabric's {@code SingleSlotStorage}; here it is a one-slot
 * {@link ResourceHandler}, for the reason given on {@link SingleFluidStackStorage}: NeoForge
 * addresses a handler by index and {@link ResourceView} is a record that cannot be implemented.
 */
public class SidedFluidInventorySlotWrapper implements ResourceHandler<FluidResource> {
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
    public int insert(int index, FluidResource resource, int maxAmount, TransactionContext transaction) {
        if (!sidedInventory.canInsert(slotWrapper.slot, FluidInventoryStorage.getCachedStack(resource), direction)) {
            return 0;
        }
        return slotWrapper.insert(index, resource, maxAmount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int maxAmount, TransactionContext transaction) {
        if (!sidedInventory.canExtract(slotWrapper.slot, FluidInventoryStorage.getCachedStack(resource), direction)) {
            return 0;
        }
        return slotWrapper.extract(index, resource, maxAmount, transaction);
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        return slotWrapper.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return slotWrapper.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return slotWrapper.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return sidedInventory.canInsert(
            slotWrapper.slot,
            FluidInventoryStorage.getCachedStack(resource),
            direction
        ) && slotWrapper.isValid(index, resource);
    }

    public boolean isResourceBlank() {
        return slotWrapper.isResourceBlank();
    }

    // Fabric's getUnderlyingView had no NeoForge counterpart and is dropped: it existed so a
    // decorated view could be compared against the one it wraps, which the capability system does
    // not need.

    @Override
    public String toString() {
        return "SidedInventorySlotWrapper[%s#%d/%s]".formatted(
            FluidInventoryStorage.toString(sidedInventory),
            slotWrapper.slot,
            direction.name()
        );
    }
}
