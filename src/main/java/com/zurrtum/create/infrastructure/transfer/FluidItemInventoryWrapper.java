package com.zurrtum.create.infrastructure.transfer;

import com.zurrtum.create.infrastructure.fluids.FluidItemInventory;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

public class FluidItemInventoryWrapper extends FluidInventoryWrapper<ResourceHandler<FluidResource>, ResourceView<FluidResource>> implements FluidItemInventory {
    private static final Deque<FluidItemInventoryWrapper> POOL = new ArrayDeque<>();
    // Upstream carries a FluidItemContext here, Create's own port of Fabric's
    // ContainerItemContext, whose job was to describe where the stack lives and write the emptied
    // or filled stack back when the context closed. NeoForge's ItemAccess is that same concept,
    // supplied by the loader, so Create's class is no longer needed: the access owns the stack and
    // reports the current one directly.
    private ItemAccess access;
    private int size;

    private FluidItemInventoryWrapper(ResourceHandler<FluidResource> storage) {
        super(storage);
    }

    public static FluidItemInventory of(ResourceHandler<FluidResource> storage, ItemAccess access) {
        FluidItemInventoryWrapper inventory = POOL.pollFirst();
        if (inventory == null) {
            inventory = new FluidItemInventoryWrapper(storage);
        } else {
            inventory.storage = storage;
            inventory.init();
        }
        inventory.access = access;
        return inventory;
    }

    @Override
    public void close() {
        // No context to unwind: NeoForge has no transaction to end here. The pooling is
        // upstream's and is kept -- these wrappers are built per fluid-item interaction, which is
        // hot enough that upstream chose to recycle them.
        storage = null;
        access = null;
        POOL.addLast(this);
    }

    @Override
    public ItemStack getContainer() {
        return access.getResource().toStack(access.getAmount());
    }

    @Override
    public boolean isEmpty() {
        return access.getResource().isEmpty() || access.getAmount() <= 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int insert(FluidStack stack) {
        if (!ResourceView.supportsInsertion(storage)) {
            return 0;
        }
        return super.insert(stack);
    }

    @Override
    public int insert(FluidStack stack, int maxAmount) {
        if (!ResourceView.supportsInsertion(storage)) {
            return 0;
        }
        return super.insert(stack, maxAmount);
    }

    @Override
    public boolean preciseInsert(FluidStack stack) {
        if (!ResourceView.supportsInsertion(storage)) {
            return false;
        }
        return super.preciseInsert(stack);
    }

    @Override
    public int extract(FluidStack stack) {
        if (!ResourceView.supportsExtraction(storage)) {
            return 0;
        }
        return super.extract(stack);
    }

    @Override
    public int extract(FluidStack stack, int maxAmount) {
        if (!ResourceView.supportsExtraction(storage)) {
            return 0;
        }
        return super.extract(stack, maxAmount);
    }

    @Override
    public FluidStack extractAny(int maxAmount) {
        if (!ResourceView.supportsExtraction(storage)) {
            return FluidStack.EMPTY;
        }
        return super.extractAny(maxAmount);
    }

    @Override
    public boolean preciseExtract(FluidStack stack) {
        if (!ResourceView.supportsExtraction(storage)) {
            return false;
        }
        return super.preciseExtract(stack);
    }

    @Override
    public int count(FluidStack stack) {
        if (!ResourceView.supportsExtraction(storage)) {
            return 0;
        }
        return super.count(stack);
    }

    @Override
    public int count(FluidStack stack, int maxAmount) {
        if (!ResourceView.supportsExtraction(storage)) {
            return 0;
        }
        return super.count(stack, maxAmount);
    }

    @Override
    public int countSpace(FluidStack stack) {
        if (!ResourceView.supportsInsertion(storage)) {
            return 0;
        }
        return super.countSpace(stack);
    }

    @Override
    public int countSpace(FluidStack stack, int maxAmount) {
        if (!ResourceView.supportsInsertion(storage)) {
            return 0;
        }
        return super.countSpace(stack, maxAmount);
    }

    @Override
    public FluidStack removeStack(int slot) {
        if (!ResourceView.supportsExtraction(storage)) {
            return FluidStack.EMPTY;
        }
        return super.removeStack(slot);
    }

    @Override
    public FluidStack removeStack(int slot, int amount) {
        if (!ResourceView.supportsExtraction(storage)) {
            return FluidStack.EMPTY;
        }
        return super.removeStack(slot, amount);
    }

    @Override
    protected void insert(ResourceView<FluidResource> view, FluidResource variant, int amount, Transaction transaction) {
        storage.insert(variant, amount, transaction);
    }

    @Override
    @Nullable
    protected ResourceView<FluidResource> getSlot(int slot) {
        if (storage instanceof ResourceHandler<FluidResource> slottedStorage) {
            return new ResourceView<>(slottedStorage, slot);
        }
        int current = 0;
        for (ResourceView<FluidResource> view : ResourceView.all(storage)) {
            if (current == slot) {
                return view;
            }
            current++;
        }
        return null;
    }

    @Override
    protected void init() {
        if (storage instanceof ResourceHandler<FluidResource> slottedStorage) {
            size = slottedStorage.size();
            super.init();
        } else {
            int count = 0;
            int max = 0;
            for (ResourceView<FluidResource> view : ResourceView.all(storage)) {
                int capacity = view.getCapacityAsInt();
                if (max < capacity) {
                    max = capacity;
                }
                count++;
            }
            size = count;
            capacity = max;
        }
    }
}
