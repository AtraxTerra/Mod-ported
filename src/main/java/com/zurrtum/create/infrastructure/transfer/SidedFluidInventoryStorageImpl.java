package com.zurrtum.create.infrastructure.transfer;

import com.zurrtum.create.infrastructure.fluids.SidedFluidInventory;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.minecraft.core.Direction;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One face of a {@link SidedFluidInventory}, exposed as a {@link FluidInventoryStorage}.
 * Holds its own part list for the same reason as {@link FluidInventoryStorageImpl}.
 */
public class SidedFluidInventoryStorageImpl implements FluidInventoryStorage {
    private final FluidInventoryStorageImpl backingStorage;
    private final List<SidedFluidInventorySlotWrapper> parts;

    SidedFluidInventoryStorageImpl(FluidInventoryStorageImpl storage, Direction direction) {
        parts = Collections.unmodifiableList(createWrapperList(storage, direction));
        backingStorage = storage;
    }

    @Override
    public List<? extends ResourceHandler<FluidResource>> getParts() {
        return parts;
    }

    private static List<SidedFluidInventorySlotWrapper> createWrapperList(
        FluidInventoryStorageImpl storage,
        Direction direction
    ) {
        SidedFluidInventory inventory = (SidedFluidInventory) storage.inventory;
        int[] availableSlots = inventory.getAvailableSlots(direction);
        SidedFluidInventorySlotWrapper[] slots = new SidedFluidInventorySlotWrapper[availableSlots.length];

        for (int i = 0; i < availableSlots.length; ++i) {
            slots[i] = new SidedFluidInventorySlotWrapper(
                storage.backingList.get(availableSlots[i]),
                inventory,
                direction
            );
        }

        return Arrays.asList(slots);
    }

    @Override
    public String toString() {
        return backingStorage.toString();
    }
}
