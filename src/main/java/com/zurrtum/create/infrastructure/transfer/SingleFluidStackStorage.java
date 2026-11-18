package com.zurrtum.create.infrastructure.transfer;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A single {@link FluidStack} exposed as a one-slot {@link ResourceHandler}.
 *
 * <h2>Why this is a handler and not a {@link ResourceView}</h2>
 * Upstream implements Fabric's {@code SingleSlotStorage}, which is both a storage and a view of its
 * own single slot -- Fabric lets one object be both. NeoForge separates them: a
 * {@link ResourceHandler} is addressed by index, and {@link ResourceView} here is a plain record
 * over (handler, index) that cannot be implemented.
 *
 * <p>So this becomes a handler whose {@link #size()} is 1, and every operation ignores the index.
 * The insert and extract bodies are upstream's, unchanged apart from the index parameter and
 * {@code long} becoming {@code int} -- NeoForge's amounts are ints, where Fabric's were longs.
 *
 * <p>{@link SnapshotJournal} is NeoForge's {@code SnapshotParticipant}: same contract, and
 * {@code readSnapshot} is spelled {@code revertToSnapshot}.
 */
public abstract class SingleFluidStackStorage extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
    protected abstract FluidStack getStack();

    protected abstract void setStack(FluidStack stack);

    protected boolean canInsert(FluidResource variant) {
        return true;
    }

    protected boolean canExtract(FluidResource variant) {
        return true;
    }

    public int getCapacity(FluidResource insert, FluidStack stack) {
        return stack.getMaxAmount();
    }

    /**
     * Not part of {@link ResourceHandler}; kept because Create's own code asks a single-slot storage
     * this directly.
     */
    public boolean isResourceBlank() {
        return getStack().isEmpty();
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        FluidStack stack = getStack();
        return FluidResource.of(stack.getFluid(), stack.getComponentChanges());
    }

    @Override
    public long getAmountAsLong(int index) {
        return getStack().getAmount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return getCapacity(resource, getStack());
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        FluidStack currentStack = getStack();
        return (FluidInventoryStorage.matches(resource, currentStack) || currentStack.isEmpty())
            && canInsert(resource);
    }

    @Override
    public int insert(int index, FluidResource insertedVariant, int maxAmount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(insertedVariant, maxAmount);

        FluidStack currentStack = getStack();

        if ((FluidInventoryStorage.matches(insertedVariant, currentStack) || currentStack.isEmpty()) && canInsert(
            insertedVariant)) {
            int insertedAmount = Math.min(
                maxAmount,
                getCapacity(insertedVariant, currentStack) - currentStack.getAmount()
            );

            if (insertedAmount > 0) {
                updateSnapshots(transaction);
                currentStack = getStack();

                if (currentStack.isEmpty()) {
                    Integer capacity = currentStack.get(AllDataComponents.FLUID_MAX_CAPACITY);
                    currentStack = new FluidStack(
                        insertedVariant.getFluid(),
                        insertedAmount,
                        insertedVariant.getComponentsPatch()
                    );
                    if (capacity != null) {
                        currentStack.set(AllDataComponents.FLUID_MAX_CAPACITY, capacity);
                    }
                } else {
                    currentStack.increment(insertedAmount);
                }

                setStack(currentStack);

                return insertedAmount;
            }
        }

        return 0;
    }

    @Override
    public int extract(int index, FluidResource variant, int maxAmount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(variant, maxAmount);

        FluidStack currentStack = getStack();

        if (FluidInventoryStorage.matches(variant, currentStack) && canExtract(variant)) {
            int extracted = Math.min(currentStack.getAmount(), maxAmount);

            if (extracted > 0) {
                updateSnapshots(transaction);
                currentStack = getStack();
                currentStack.decrement(extracted);
                setStack(currentStack);

                return extracted;
            }
        }

        return 0;
    }

    @Override
    protected FluidStack createSnapshot() {
        FluidStack original = getStack();
        setStack(original.copy());
        return original;
    }

    @Override
    protected void revertToSnapshot(FluidStack snapshot) {
        setStack(snapshot);
    }

    @Override
    public String toString() {
        return "SingleFluidStackStorage[" + getStack() + "]";
    }
}
