package com.zurrtum.create.infrastructure.transfer;

import com.google.common.collect.MapMaker;
import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.SidedFluidInventory;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A Create {@link FluidInventory} exposed as a {@link FluidInventoryStorage}.
 *
 * <p>Upstream extends Fabric's {@code CombinedStorage}, which keeps a mutable {@code parts} field
 * that this class reassigns whenever the inventory changes size. NeoForge's
 * {@code CombinedResourceHandler} cannot be used for that: its children are final and its own
 * javadoc says the wrapper must be recreated to follow a size change. Create's inventories do
 * change size at runtime, so this holds the part list itself and the interface's default methods
 * delegate to it.
 */
public class FluidInventoryStorageImpl implements FluidInventoryStorage {
    private static final Map<FluidInventory, FluidInventoryStorageImpl> WRAPPERS = new MapMaker().weakValues()
        .makeMap();

    public static FluidInventoryStorage of(FluidInventory inventory) {
        FluidInventoryStorageImpl storage = WRAPPERS.computeIfAbsent(inventory, FluidInventoryStorageImpl::new);
        storage.resizeSlotList();
        return storage;
    }

    public static FluidInventoryStorage of(FluidInventory inventory, @Nullable Direction direction) {
        FluidInventoryStorageImpl storage = WRAPPERS.computeIfAbsent(inventory, FluidInventoryStorageImpl::new);
        storage.resizeSlotList();
        return storage.getSidedWrapper(direction);
    }

    final FluidInventory inventory;
    final List<FluidInventorySlotWrapper> backingList;
    final MarkDirtyParticipant markDirtyParticipant = new MarkDirtyParticipant();

    /** Replaced wholesale on resize; never mutated in place. */
    private List<FluidInventorySlotWrapper> parts = Collections.emptyList();

    public FluidInventoryStorageImpl(FluidInventory inventory) {
        this.inventory = inventory;
        backingList = new ArrayList<>();
    }

    @Override
    public List<? extends ResourceHandler<FluidResource>> getParts() {
        return parts;
    }

    private void resizeSlotList() {
        int inventorySize = inventory.size();

        if (inventorySize != parts.size()) {
            while (backingList.size() < inventorySize) {
                backingList.add(new FluidInventorySlotWrapper(this, backingList.size()));
            }

            parts = Collections.unmodifiableList(backingList.subList(0, inventorySize));
        }
    }

    private FluidInventoryStorage getSidedWrapper(@Nullable Direction direction) {
        if (inventory instanceof SidedFluidInventory && direction != null) {
            return new SidedFluidInventoryStorageImpl(this, direction);
        }
        return this;
    }

    @Override
    public String toString() {
        return "FluidInventoryStorage[" + FluidInventoryStorage.toString(inventory) + "]";
    }

    class MarkDirtyParticipant extends SnapshotJournal<Boolean> {
        @Override
        protected Boolean createSnapshot() {
            return Boolean.TRUE;
        }

        @Override
        protected void revertToSnapshot(Boolean snapshot) {
        }

        @Override
        protected void onFinalCommit() {
            inventory.markDirty();
        }
    }
}
