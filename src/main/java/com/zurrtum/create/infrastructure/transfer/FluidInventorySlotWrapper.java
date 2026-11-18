package com.zurrtum.create.infrastructure.transfer;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.minecraft.core.component.DataComponentType;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class FluidInventorySlotWrapper extends SingleFluidStackStorage {
    private final FluidInventoryStorageImpl storage;
    final int slot;
    private @Nullable FluidStack lastReleasedSnapshot;

    FluidInventorySlotWrapper(FluidInventoryStorageImpl storage, int slot) {
        this.storage = storage;
        this.slot = slot;
    }

    @Override
    protected FluidStack getStack() {
        return storage.inventory.getStack(slot);
    }

    @Override
    protected void setStack(FluidStack stack) {
        storage.inventory.setStack(slot, stack);
    }

    @Override
    public int insert(int index, FluidResource insertedVariant, int maxAmount, TransactionContext transaction) {
        if (!canInsert(slot, FluidInventoryStorage.getCachedStack(insertedVariant))) {
            return 0;
        }

        return super.insert(index, insertedVariant, maxAmount, transaction);
    }

    private boolean canInsert(int slot, FluidStack stack) {
        return storage.inventory.isValid(slot, stack);
    }

    @Override
    public int getCapacity(FluidResource insert, FluidStack stack) {
        Integer capacity = stack.get(AllDataComponents.FLUID_MAX_CAPACITY);
        if (capacity != null) {
            return Math.min(storage.inventory.getMaxAmountPerStack(), capacity);
        }
        return storage.inventory.getMaxAmount(FluidInventoryStorage.getCachedStack(insert));
    }

    @Override
    public void updateSnapshots(TransactionContext transaction) {
        storage.markDirtyParticipant.updateSnapshots(transaction);
        super.updateSnapshots(transaction);
    }

    @Override
    protected void releaseSnapshot(FluidStack snapshot) {
        lastReleasedSnapshot = snapshot;
    }

    // Fabric's onFinalCommit is NeoForge's onRootCommit, which also hands back the original
    // state. This body uses lastReleasedSnapshot, which releaseSnapshot still records, so the
    // parameter is unused rather than the field being replaced by it.
    @Override
    protected void onRootCommit(FluidStack originalState) {
        FluidStack original = lastReleasedSnapshot;
        FluidStack currentStack = getStack();

        if (!original.isEmpty() && original.getFluid() == currentStack.getFluid()) {
            if (!Objects.equals(original.getComponentChanges(), currentStack.getComponentChanges())) {
                for (DataComponentType<?> type : original.getComponents().keySet()) {
                    original.set(type, null);
                }

                original.applyComponentsFrom(currentStack.getComponents());
            }

            original.setAmount(currentStack.getAmount());
            setStack(original);
        } else {
            original.setAmount(0);
        }
    }

    @Override
    public String toString() {
        return "InventorySlotWrapper[%s#%d]".formatted(FluidInventoryStorage.toString(storage.inventory), slot);
    }
}
