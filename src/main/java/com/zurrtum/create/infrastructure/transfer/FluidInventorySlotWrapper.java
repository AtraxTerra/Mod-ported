package com.zurrtum.create.infrastructure.transfer;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.minecraft.core.component.DataComponentType;

import java.util.Objects;

public class FluidInventorySlotWrapper extends SingleFluidStackStorage {
    private final FluidInventoryStorageImpl storage;
    final int slot;

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

    // Fabric's onFinalCommit is NeoForge's onRootCommit. Fabric releases the root snapshot first, and
    // upstream read it back from the last released snapshot; NeoForge calls onRootCommit before it
    // releases anything and hands that snapshot over as originalState: the stack that was in the slot
    // when the transaction began (createSnapshot keeps it and puts a copy in the slot). Reading the
    // last released snapshot instead gave null on a first transaction, so every insert into a Create
    // tank through Capabilities.Fluid.BLOCK crashed on commit, or a stale stack from an earlier one.
    @Override
    protected void onRootCommit(FluidStack originalState) {
        FluidStack original = originalState;
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
