package com.zurrtum.create.infrastructure.transfer;

import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import com.zurrtum.create.infrastructure.fluids.SidedFluidInventory;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntSortedMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class FluidInventoryWrapper<T extends ResourceHandler<FluidResource>, S extends ResourceView<FluidResource>> implements FluidInventory {
    protected T storage;
    protected int capacity;

    public FluidInventoryWrapper(T storage) {
        this.storage = storage;
        init();
    }

    /**
     * Wraps a foreign handler so Create can treat it as one of its own inventories.
     *
     * <p>Upstream chose between two subclasses here and, for a storage that was read-only or
     * write-only, wrapped the result in {@code Filter}. Neither applies on NeoForge: a
     * {@link ResourceHandler} is always indexed, and it exposes no insert/extract capability
     * query to branch on -- see {@link ResourceView#supportsInsertion}. A handler that refuses an
     * operation simply returns 0, so the unfiltered wrapper is correct for every case.
     */
    public static FluidInventory of(ResourceHandler<FluidResource> storage) {
        return new Slotted(storage);
    }

    @Override
    public void clearContent() {
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<FluidResource> view : ResourceView.nonEmpty(storage)) {
                view.extract(view.getResource(), view.getAmount(), transaction);
            }
            transaction.commit();
        }
    }

    @Override
    public int count(FluidStack stack) {
        int maxAmount = stack.getAmount();
        if (maxAmount == 0) {
            return 0;
        }
        return count(stack, maxAmount);
    }

    @Override
    public int count(FluidStack stack, int maxAmount) {
        try (Transaction transaction = Transaction.openRoot()) {
            long extract = storage.extract(
                FluidResource.of(stack.getFluid(), stack.getComponentChanges()),
                maxAmount,
                transaction
            );
            return (int) extract;
        }
    }

    @Override
    public FluidStack count(Predicate<FluidStack> predicate) {
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<FluidResource> view : ResourceView.nonEmpty(storage)) {
                FluidResource variant = view.getResource();
                FluidStack stack = FluidInventoryStorage.getCachedStack(variant);
                if (predicate.test(stack)) {
                    long extract = view.extract(variant, view.getAmount(), transaction);
                    if (extract == 0) {
                        continue;
                    }
                    return stack.directCopy((int) extract);
                }
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack count(Predicate<FluidStack> predicate, int maxAmount) {
        if (maxAmount == 0) {
            return FluidStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<FluidResource> view : ResourceView.nonEmpty(storage)) {
                FluidResource variant = view.getResource();
                FluidStack stack = FluidInventoryStorage.getCachedStack(variant);
                if (predicate.test(stack)) {
                    long extract = storage.extract(variant, maxAmount, transaction);
                    if (extract == 0) {
                        continue;
                    }
                    return stack.directCopy((int) extract);
                }
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack countAny() {
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<FluidResource> view : ResourceView.nonEmpty(storage)) {
                FluidResource variant = view.getResource();
                FluidStack stack = FluidInventoryStorage.getCachedStack(variant);
                long extract = view.extract(variant, view.getAmount(), transaction);
                if (extract == 0) {
                    continue;
                }
                return stack.directCopy((int) extract);
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack countAny(int maxAmount) {
        if (maxAmount == 0) {
            return FluidStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<FluidResource> view : ResourceView.nonEmpty(storage)) {
                FluidResource variant = view.getResource();
                FluidStack stack = FluidInventoryStorage.getCachedStack(variant);
                long extract = storage.extract(variant, maxAmount, transaction);
                if (extract == 0) {
                    continue;
                }
                return stack.directCopy((int) extract);
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int countSpace(FluidStack stack) {
        int maxAmount = stack.getAmount();
        if (maxAmount == 0) {
            return 0;
        }
        return countSpace(stack, maxAmount);
    }

    @Override
    public int countSpace(FluidStack stack, int maxAmount) {
        try (Transaction transaction = Transaction.openRoot()) {
            long insert = storage.insert(
                FluidResource.of(stack.getFluid(), stack.getComponentChanges()),
                maxAmount,
                transaction
            );
            return (int) insert;
        }
    }

    @Override
    public boolean countSpace(List<FluidStack> stacks) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return true;
        }
        if (listSize == 1) {
            FluidStack stack = stacks.getFirst();
            int amount = stack.getAmount();
            return countSpace(stack, amount) == amount;
        }
        Object2IntLinkedOpenCustomHashMap<FluidStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            FLUID_STACK_HASH_STRATEGY);
        for (FluidStack stack : stacks) {
            map.merge(stack, stack.getAmount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<FluidStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<FluidStack> entry = entries.first();
            FluidStack stack = entry.getKey();
            int amount = entry.getIntValue();
            return countSpace(stack, amount) == amount;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            ObjectIterator<Object2IntMap.Entry<FluidStack>> iterator = entries.fastIterator();
            do {
                Object2IntMap.Entry<FluidStack> entry = iterator.next();
                FluidStack stack = entry.getKey();
                int amount = entry.getIntValue();
                long insert = storage.insert(
                    FluidResource.of(stack.getFluid(), stack.getComponentChanges()),
                    amount,
                    transaction
                );
                if (insert < amount) {
                    return false;
                }
            } while (iterator.hasNext());
            return true;
        }
    }

    @Override
    public int extract(FluidStack stack) {
        int maxAmount = stack.getAmount();
        if (maxAmount == 0) {
            return 0;
        }
        return extract(stack, maxAmount);
    }

    @Override
    public int extract(FluidStack stack, int maxAmount) {
        try (Transaction transaction = Transaction.openRoot()) {
            long extract = storage.extract(
                FluidResource.of(stack.getFluid(), stack.getComponentChanges()),
                maxAmount,
                transaction
            );
            transaction.commit();
            return (int) extract;
        }
    }

    @Override
    public FluidStack extract(Predicate<FluidStack> predicate) {
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<FluidResource> view : ResourceView.nonEmpty(storage)) {
                FluidResource variant = view.getResource();
                FluidStack stack = FluidInventoryStorage.getCachedStack(variant);
                if (predicate.test(stack)) {
                    long extract = view.extract(variant, view.getAmount(), transaction);
                    if (extract == 0) {
                        continue;
                    }
                    transaction.commit();
                    return stack.directCopy((int) extract);
                }
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack extract(Predicate<FluidStack> predicate, int maxAmount) {
        if (maxAmount == 0) {
            return FluidStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<FluidResource> view : ResourceView.nonEmpty(storage)) {
                FluidResource variant = view.getResource();
                FluidStack stack = FluidInventoryStorage.getCachedStack(variant);
                if (predicate.test(stack)) {
                    long extract = storage.extract(variant, maxAmount, transaction);
                    if (extract == 0) {
                        continue;
                    }
                    transaction.commit();
                    return stack.directCopy((int) extract);
                }
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack extractAny() {
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<FluidResource> view : ResourceView.nonEmpty(storage)) {
                FluidResource variant = view.getResource();
                long extract = view.extract(variant, view.getAmount(), transaction);
                if (extract == 0) {
                    continue;
                }
                transaction.commit();
                return FluidInventoryStorage.getCachedStack(variant).directCopy((int) extract);
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack extractAny(int maxAmount) {
        if (maxAmount == 0) {
            return FluidStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<FluidResource> view : ResourceView.nonEmpty(storage)) {
                FluidResource variant = view.getResource();
                long extract = view.extract(variant, maxAmount, transaction);
                if (extract == 0) {
                    continue;
                }
                transaction.commit();
                return FluidInventoryStorage.getCachedStack(variant).directCopy((int) extract);
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int forceInsert(FluidStack stack) {
        return insert(stack);
    }

    @Override
    public int forceInsert(FluidStack stack, int maxAmount) {
        return insert(stack, maxAmount);
    }

    @Override
    public boolean forcePreciseInsert(FluidStack stack) {
        return preciseInsert(stack);
    }

    @Override
    public boolean forcePreciseInsert(FluidStack stack, int maxAmount) {
        return preciseInsert(stack, maxAmount);
    }

    @Override
    public int getMaxAmountPerStack() {
        return capacity;
    }

    @Nullable
    protected abstract S getSlot(int slot);

    @Override
    public FluidStack getStack(int slot) {
        if (slot >= size()) {
            return FluidStack.EMPTY;
        }
        ResourceView<FluidResource> view = getSlot(slot);
        if (view == null) {
            return FluidStack.EMPTY;
        }
        FluidResource variant = view.getResource();
        return new FluidStack(variant.getFluid(), view.getAmount(), variant.getComponentsPatch());
    }

    protected void init() {
        int max = 0;
        for (ResourceView<FluidResource> view : storage) {
            long capacity = view.getCapacity();
            if (max < capacity) {
                max = (int) capacity;
            }
        }
        capacity = max;
    }

    protected abstract void insert(S view, FluidResource variant, int amount, Transaction transaction);

    @Override
    public int insert(FluidStack stack) {
        int maxAmount = stack.getAmount();
        if (maxAmount == 0) {
            return 0;
        }
        return insert(stack, maxAmount);
    }

    @Override
    public int insert(FluidStack stack, int maxAmount) {
        try (Transaction transaction = Transaction.openRoot()) {
            long insert = storage.insert(
                FluidResource.of(stack.getFluid(), stack.getComponentChanges()),
                maxAmount,
                transaction
            );
            transaction.commit();
            return (int) insert;
        }
    }

    @Override
    public List<FluidStack> insert(List<FluidStack> stacks) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return stacks;
        }
        if (listSize == 1) {
            FluidStack stack = stacks.getFirst();
            int amount = stack.getAmount();
            int insert = insert(stacks.getFirst());
            if (amount == insert) {
                return List.of();
            }
            if (insert == 0) {
                return stacks;
            }
            return List.of(stack.directCopy(amount - insert));
        }
        Object2IntLinkedOpenCustomHashMap<FluidStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            FLUID_STACK_HASH_STRATEGY);
        for (FluidStack stack : stacks) {
            map.merge(stack, stack.getAmount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<FluidStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<FluidStack> entry = entries.first();
            FluidStack stack = entry.getKey();
            int amount = entry.getIntValue();
            int insert = insert(stack, amount);
            if (amount == insert) {
                return List.of();
            }
            if (insert == 0) {
                return stacks;
            }
            return List.of(stack.directCopy(amount - insert));
        }
        try (Transaction transaction = Transaction.openRoot()) {
            boolean dirty = false;
            ObjectIterator<Object2IntMap.Entry<FluidStack>> iterator = entries.fastIterator();
            do {
                Object2IntMap.Entry<FluidStack> entry = iterator.next();
                FluidStack stack = entry.getKey();
                int amount = entry.getIntValue();
                long insert = storage.insert(
                    FluidResource.of(stack.getFluid(), stack.getComponentChanges()),
                    amount,
                    transaction
                );
                if (insert == amount) {
                    iterator.remove();
                    if (entries.isEmpty()) {
                        transaction.commit();
                        return List.of();
                    }
                    dirty = true;
                } else if (insert != 0) {
                    entry.setValue(amount - (int) insert);
                    dirty = true;
                }
            } while (iterator.hasNext());
            if (dirty) {
                List<FluidStack> result = new ArrayList<>();
                for (Object2IntMap.Entry<FluidStack> entry : entries) {
                    FluidStack stack = entry.getKey();
                    int amount = entry.getIntValue();
                    if (stack.getAmount() == amount) {
                        result.add(stack);
                    } else {
                        result.add(stack.directCopy(amount));
                    }
                }
                transaction.commit();
                return result;
            }
            return stacks;
        }
    }

    @Override
    public int insertExist(FluidStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            long insert = storage.insert(
                FluidResource.of(stack.getFluid(), stack.getComponentChanges()),
                stack.getAmount(),
                transaction
            );
            transaction.commit();
            return (int) insert;
        }
    }

    @Override
    public boolean isEmpty() {
        for (ResourceView<FluidResource> _ : ResourceView.nonEmpty(storage)) {
            return false;
        }
        return true;
    }

    @Override
    public java.util.Iterator<FluidStack> iterator() {
        return ResourceView.supportsExtraction(storage) ? new Iterator(storage) : Collections.emptyIterator();
    }

    @Override
    public boolean preciseExtract(FluidStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int amount = stack.getAmount();
            long extract = storage.extract(
                FluidResource.of(stack.getFluid(), stack.getComponentChanges()),
                amount,
                transaction
            );
            if (extract < amount) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    @Override
    public FluidStack preciseExtract(Predicate<FluidStack> predicate, int maxAmount) {
        if (maxAmount == 0) {
            return FluidStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<FluidResource> view : ResourceView.nonEmpty(storage)) {
                FluidResource variant = view.getResource();
                FluidStack stack = FluidInventoryStorage.getCachedStack(variant);
                if (predicate.test(stack)) {
                    long extract = storage.extract(variant, maxAmount, transaction);
                    if (extract == 0) {
                        continue;
                    }
                    if (extract == maxAmount) {
                        transaction.commit();
                        return stack.directCopy(maxAmount);
                    }
                }
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public boolean preciseInsert(FluidStack stack) {
        int maxAmount = stack.getAmount();
        if (maxAmount == 0) {
            return true;
        }
        return preciseInsert(stack, maxAmount);
    }

    @Override
    public boolean preciseInsert(FluidStack stack, int maxAmount) {
        try (Transaction transaction = Transaction.openRoot()) {
            long insert = storage.insert(
                FluidResource.of(stack.getFluid(), stack.getComponentChanges()),
                maxAmount,
                transaction
            );
            if (insert < maxAmount) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    @Override
    public boolean preciseInsert(List<FluidStack> stacks) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return true;
        }
        if (listSize == 1) {
            return preciseInsert(stacks.getFirst());
        }
        Object2IntLinkedOpenCustomHashMap<FluidStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            FLUID_STACK_HASH_STRATEGY);
        for (FluidStack stack : stacks) {
            map.merge(stack, stack.getAmount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<FluidStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<FluidStack> entry = entries.first();
            return preciseInsert(entry.getKey(), entry.getIntValue());
        }
        try (Transaction transaction = Transaction.openRoot()) {
            ObjectIterator<Object2IntMap.Entry<FluidStack>> iterator = entries.fastIterator();
            do {
                Object2IntMap.Entry<FluidStack> entry = iterator.next();
                FluidStack stack = entry.getKey();
                int amount = entry.getIntValue();
                long insert = storage.insert(
                    FluidResource.of(stack.getFluid(), stack.getComponentChanges()),
                    amount,
                    transaction
                );
                if (insert < amount) {
                    return false;
                }
            } while (iterator.hasNext());
            transaction.commit();
            return true;
        }
    }

    @Override
    public FluidStack removeStack(int slot) {
        if (slot >= size()) {
            return FluidStack.EMPTY;
        }
        ResourceView<FluidResource> view = getSlot(slot);
        if (view == null) {
            return FluidStack.EMPTY;
        }
        FluidResource variant = view.getResource();
        long amount = view.getAmount();
        if (variant.isBlank() || amount == 0) {
            return FluidStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            amount = view.extract(variant, amount, transaction);
            transaction.commit();
        }
        return new FluidStack(variant.getFluid(), amount, variant.getComponentsPatch());
    }

    @Override
    public FluidStack removeStack(int slot, int amount) {
        if (slot >= size()) {
            return FluidStack.EMPTY;
        }
        ResourceView<FluidResource> view = getSlot(slot);
        if (view == null) {
            return FluidStack.EMPTY;
        }
        FluidResource variant = view.getResource();
        if (variant.isBlank() || view.getAmount() == 0) {
            return FluidStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            amount = (int) view.extract(variant, amount, transaction);
            transaction.commit();
        }
        return new FluidStack(variant.getFluid(), amount, variant.getComponentsPatch());
    }

    @Override
    public void setStack(int slot, FluidStack stack) {
        if (slot >= size()) {
            return;
        }
        S view = getSlot(slot);
        if (view == null) {
            return;
        }
        FluidResource variant = view.getResource();
        try (Transaction transaction = Transaction.openRoot()) {
            if (variant.isBlank() || view.getAmount() == 0) {
                if (stack.isEmpty()) {
                    return;
                }
                insert(
                    view,
                    FluidResource.of(stack.getFluid(), stack.getComponentChanges()),
                    stack.getAmount(),
                    transaction
                );
            } else if (FluidInventoryStorage.matches(variant, stack)) {
                int amount = stack.getAmount();
                int targetAmount = (int) view.getAmount();
                if (amount == targetAmount) {
                    return;
                }
                int change = targetAmount - amount;
                if (amount < targetAmount) {
                    insert(view, variant, change, transaction);
                } else {
                    view.extract(variant, change, transaction);
                }
            } else {
                view.extract(variant, view.getAmount(), transaction);
                insert(
                    view,
                    FluidResource.of(stack.getFluid(), stack.getComponentChanges()),
                    stack.getAmount(),
                    transaction
                );
            }
            transaction.commit();
        }
    }

    @Override
    public Stream<FluidStack> stream() {
        return ResourceView.supportsExtraction(storage) ? FluidInventory.super.stream() : Stream.empty();
    }

    public static class Filter implements SidedFluidInventory {
        private final int[] slots;
        private final boolean canInsert;
        private final boolean canExtract;
        private final FluidInventoryWrapper<?, ?> inventory;

        public Filter(FluidInventoryWrapper<?, ?> inventory) {
            this.inventory = inventory;
            slots = SlotRangeCache.get(inventory.size());
            canInsert = ResourceView.supportsInsertion(inventory.storage);
            canExtract = ResourceView.supportsExtraction(inventory.storage);
        }

        @Override
        public boolean canExtract(int slot, FluidStack stack, @Nullable Direction side) {
            return canExtract;
        }

        @Override
        public boolean canInsert(int slot, FluidStack stack, @Nullable Direction dir) {
            return canInsert;
        }

        @Override
        public void clearContent() {
            if (!canExtract) {
                return;
            }
            inventory.clearContent();
        }

        @Override
        public int count(FluidStack stack, @Nullable Direction side) {
            return count(stack);
        }

        @Override
        public int count(FluidStack stack) {
            if (!canExtract) {
                return 0;
            }
            return inventory.count(stack);
        }

        @Override
        public int count(FluidStack stack, int maxAmount, @Nullable Direction side) {
            return count(stack, maxAmount);
        }

        @Override
        public int count(FluidStack stack, int maxAmount) {
            if (!canExtract) {
                return 0;
            }
            return inventory.count(stack, maxAmount);
        }

        @Override
        public FluidStack count(Predicate<FluidStack> predicate, @Nullable Direction side) {
            return count(predicate);
        }

        @Override
        public FluidStack count(Predicate<FluidStack> predicate) {
            if (!canExtract) {
                return FluidStack.EMPTY;
            }
            return inventory.count(predicate);
        }

        @Override
        public FluidStack count(Predicate<FluidStack> predicate, int maxAmount, @Nullable Direction side) {
            return count(predicate, maxAmount);
        }

        @Override
        public FluidStack count(Predicate<FluidStack> predicate, int maxAmount) {
            if (!canExtract) {
                return FluidStack.EMPTY;
            }
            return inventory.count(predicate, maxAmount);
        }

        @Override
        public FluidStack countAny(@Nullable Direction side) {
            return countAny();
        }

        @Override
        public FluidStack countAny() {
            if (!canExtract) {
                return FluidStack.EMPTY;
            }
            return inventory.countAny();
        }

        @Override
        public FluidStack countAny(int maxAmount, @Nullable Direction side) {
            return countAny(maxAmount);
        }

        @Override
        public FluidStack countAny(int maxAmount) {
            if (!canExtract) {
                return FluidStack.EMPTY;
            }
            return inventory.countAny(maxAmount);
        }

        @Override
        public int countSpace(FluidStack stack, @Nullable Direction side) {
            return countSpace(stack);
        }

        @Override
        public int countSpace(FluidStack stack) {
            if (!canInsert) {
                return 0;
            }
            return inventory.countSpace(stack);
        }

        @Override
        public int countSpace(FluidStack stack, int maxAmount, @Nullable Direction side) {
            return countSpace(stack, maxAmount);
        }

        @Override
        public int countSpace(FluidStack stack, int maxAmount) {
            if (!canInsert) {
                return 0;
            }
            return inventory.countSpace(stack, maxAmount);
        }

        @Override
        public int extract(FluidStack stack, @Nullable Direction side) {
            return extract(stack);
        }

        @Override
        public int extract(FluidStack stack) {
            if (!canExtract) {
                return 0;
            }
            return inventory.extract(stack);
        }

        @Override
        public int extract(FluidStack stack, int maxAmount, @Nullable Direction side) {
            return extract(stack, maxAmount);
        }

        @Override
        public int extract(FluidStack stack, int maxAmount) {
            if (!canExtract) {
                return 0;
            }
            return inventory.extract(stack, maxAmount);
        }

        @Override
        public FluidStack extract(Predicate<FluidStack> predicate, @Nullable Direction side) {
            return extract(predicate);
        }

        @Override
        public FluidStack extract(Predicate<FluidStack> predicate) {
            if (!canExtract) {
                return FluidStack.EMPTY;
            }
            return inventory.extract(predicate);
        }

        @Override
        public FluidStack extract(Predicate<FluidStack> predicate, int maxAmount, @Nullable Direction side) {
            return extract(predicate, maxAmount);
        }

        @Override
        public FluidStack extract(Predicate<FluidStack> predicate, int maxAmount) {
            if (!canExtract) {
                return FluidStack.EMPTY;
            }
            return inventory.extract(predicate, maxAmount);
        }

        @Override
        public FluidStack extractAny(@Nullable Direction side) {
            return extractAny();
        }

        @Override
        public FluidStack extractAny() {
            if (!canExtract) {
                return FluidStack.EMPTY;
            }
            return inventory.extractAny();
        }

        @Override
        public FluidStack extractAny(int maxAmount, @Nullable Direction side) {
            return extractAny(maxAmount);
        }

        @Override
        public FluidStack extractAny(int maxAmount) {
            if (!canExtract) {
                return FluidStack.EMPTY;
            }
            return inventory.extractAny(maxAmount);
        }

        @Override
        public int[] getAvailableSlots(@Nullable Direction side) {
            return slots;
        }

        @Override
        public int getMaxAmountPerStack() {
            return inventory.getMaxAmountPerStack();
        }

        @Override
        public FluidStack getStack(int slot) {
            return inventory.getStack(slot);
        }

        @Override
        public int insert(FluidStack stack, @Nullable Direction side) {
            return insert(stack);
        }

        @Override
        public int insert(FluidStack stack) {
            if (!canInsert) {
                return 0;
            }
            return inventory.insert(stack);
        }

        @Override
        public int insert(FluidStack stack, int maxAmount, @Nullable Direction side) {
            return insert(stack, maxAmount);
        }

        @Override
        public int insert(FluidStack stack, int maxAmount) {
            if (!canInsert) {
                return 0;
            }
            return inventory.insert(stack, maxAmount);
        }

        @Override
        public List<FluidStack> insert(List<FluidStack> stacks, @Nullable Direction side) {
            return insert(stacks);
        }

        @Override
        public List<FluidStack> insert(List<FluidStack> stacks) {
            if (!canInsert) {
                return stacks;
            }
            return inventory.insert(stacks);
        }

        @Override
        public int insertExist(FluidStack stack, @Nullable Direction side) {
            return insertExist(stack);
        }

        @Override
        public int insertExist(FluidStack stack) {
            if (!canInsert) {
                return 0;
            }
            return inventory.insertExist(stack);
        }

        @Override
        public boolean isEmpty() {
            return inventory.isEmpty();
        }

        @Override
        public java.util.Iterator<FluidStack> iterator() {
            return inventory.iterator();
        }

        @Override
        public java.util.Iterator<FluidStack> iterator(@Nullable Direction side) {
            return inventory.iterator();
        }

        @Override
        public boolean preciseExtract(FluidStack stack, @Nullable Direction side) {
            return preciseExtract(stack);
        }

        @Override
        public boolean preciseExtract(FluidStack stack) {
            if (!canExtract) {
                return stack.isEmpty();
            }
            return inventory.preciseExtract(stack);
        }

        @Override
        public FluidStack preciseExtract(Predicate<FluidStack> predicate, int maxAmount, @Nullable Direction side) {
            return preciseExtract(predicate, maxAmount);
        }

        @Override
        public FluidStack preciseExtract(Predicate<FluidStack> predicate, int maxAmount) {
            if (!canExtract) {
                return FluidStack.EMPTY;
            }
            return inventory.preciseExtract(predicate, maxAmount);
        }

        @Override
        public boolean preciseInsert(FluidStack stack, @Nullable Direction side) {
            return preciseInsert(stack);
        }

        @Override
        public boolean preciseInsert(FluidStack stack) {
            if (!canInsert) {
                return stack.isEmpty();
            }
            return inventory.preciseInsert(stack);
        }

        @Override
        public boolean preciseInsert(FluidStack stack, int maxAmount, @Nullable Direction side) {
            return preciseInsert(stack, maxAmount);
        }

        @Override
        public boolean preciseInsert(FluidStack stack, int maxAmount) {
            if (!canInsert) {
                return stack.isEmpty();
            }
            return inventory.preciseInsert(stack, maxAmount);
        }

        @Override
        public boolean preciseInsert(List<FluidStack> stacks, @Nullable Direction side) {
            return preciseInsert(stacks);
        }

        @Override
        public boolean preciseInsert(List<FluidStack> stacks) {
            if (!canInsert) {
                return false;
            }
            return inventory.preciseInsert(stacks);
        }

        @Override
        public FluidStack removeStack(int slot) {
            if (!canExtract) {
                return FluidStack.EMPTY;
            }
            return inventory.removeStack(slot);
        }

        @Override
        public FluidStack removeStack(int slot, int amount) {
            if (!canExtract) {
                return FluidStack.EMPTY;
            }
            return inventory.removeStack(slot, amount);
        }

        @Override
        public void setStack(int slot, FluidStack stack) {
            inventory.setStack(slot, stack);
        }

        @Override
        public int size() {
            return inventory.size();
        }

        @Override
        public Stream<FluidStack> stream() {
            return inventory.stream();
        }

        @Override
        public Stream<FluidStack> stream(@Nullable Direction side) {
            return inventory.stream();
        }
    }

    // Upstream had two subclasses here, Direct and Slotted, because Fabric's Storage came in
    // two shapes: an opaque iterable one and an indexed one. NeoForge's ResourceHandler is
    // always indexed, so only the Slotted behaviour can apply and Direct has been removed
    // rather than ported -- it would have been unreachable code pretending to handle a case
    // that no longer exists.
    protected static class Slotted extends FluidInventoryWrapper<ResourceHandler<FluidResource>, ResourceView<FluidResource>> {
        public Slotted(ResourceHandler<FluidResource> storage) {
            super(storage);
        }

        @Override
        protected ResourceView<FluidResource> getSlot(int slot) {
            return new ResourceView<>(storage, slot);
        }

        @Override
        protected void insert(
            ResourceView<FluidResource> view,
            FluidResource variant,
            int amount,
            Transaction transaction
        ) {
            view.insert(variant, amount, transaction);
        }

        @Override
        public int insertExist(FluidStack stack) {
            if (stack.isEmpty()) {
                return 0;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                List<ResourceView<FluidResource>> emptys = new ArrayList<>();
                FluidResource variant = FluidResource.of(stack.getFluid(), stack.getComponentChanges());
                int maxAmount = stack.getAmount();
                long remaining = maxAmount;
                for (int i = 0, size = storage.size(); i < size; i++) {
                    ResourceView<FluidResource> target = new ResourceView<>(storage, i);
                    if (target.getAmount() == 0 || target.isResourceBlank()) {
                        emptys.add(target);
                    } else {
                        long insert = target.insert(variant, remaining, transaction);
                        if (insert == remaining) {
                            transaction.commit();
                            return maxAmount;
                        }
                        remaining -= insert;
                    }
                }
                for (ResourceView<FluidResource> target : emptys) {
                    long insert = target.insert(variant, remaining, transaction);
                    if (insert == remaining) {
                        transaction.commit();
                        return maxAmount;
                    }
                    remaining -= insert;
                }
                transaction.commit();
                return maxAmount - (int) remaining;
            }
        }

        @Override
        public int size() {
            return storage.size();
        }
    }

    static class Iterator implements java.util.Iterator<FluidStack> {
        private final java.util.Iterator<ResourceView<FluidResource>> iterator;

        public Iterator(ResourceHandler<FluidResource> storage) {
            iterator = storage.iterator();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public FluidStack next() {
            ResourceView<FluidResource> view = iterator.next();
            FluidResource variant = view.getResource();
            return new FluidStack(variant.getFluid(), view.getAmount(), variant.getComponentsPatch());
        }
    }
}
