package com.zurrtum.create.infrastructure.transfer;

import com.zurrtum.create.infrastructure.fluids.SidedFluidInventory;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.minecraft.core.Direction;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SidedFluidInventoryStorageImpl extends CombinedResourceHandler<FluidResource, ResourceView<FluidResource>> implements FluidInventoryStorage {
    private final FluidInventoryStorageImpl backingStorage;

    SidedFluidInventoryStorageImpl(FluidInventoryStorageImpl storage, Direction direction) {
        super(Collections.unmodifiableList(createWrapperList(storage, direction)));
        backingStorage = storage;
    }

    @Override
    public List<ResourceView<FluidResource>> getSlots() {
        return parts;
    }

    private static List<ResourceView<FluidResource>> createWrapperList(
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
