package com.zurrtum.create.infrastructure.transfer;

import com.zurrtum.create.infrastructure.items.ItemInventory;
import com.zurrtum.create.infrastructure.items.SidedItemInventory;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntSortedMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class InventoryWrapper<T extends ResourceHandler<ItemResource>, S extends ResourceView<ItemResource>> implements ItemInventory {
    protected T storage;
    protected int capacity;

    public InventoryWrapper(T storage) {
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
    public static Container of(ResourceHandler<ItemResource> storage) {
        return new Slotted(storage);
    }

    @Override
    public void clearContent() {
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                view.extract(view.getResource(), view.getAmountAsInt(), transaction);
            }
            transaction.commit();
        }
    }

    @Override
    public int count(ItemStack stack) {
        int maxAmount = stack.getCount();
        if (maxAmount == 0) {
            return 0;
        }
        return count(stack, maxAmount);
    }

    @Override
    public int count(ItemStack stack, int maxAmount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int extract = storage.extract(
                ItemResource.of(stack),
                maxAmount,
                transaction
            );
            return extract;
        }
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack count(Predicate<ItemStack> predicate) {
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                ItemResource variant = view.getResource();
                ItemStack stack = variant.toStack();
                if (predicate.test(stack)) {
                    int extract = view.extract(variant, view.getAmountAsInt(), transaction);
                    if (extract == 0) {
                        continue;
                    }
                    return directCopy(stack, (int) extract);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack count(Predicate<ItemStack> predicate, int maxAmount) {
        if (maxAmount == 0) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                ItemResource variant = view.getResource();
                ItemStack stack = variant.toStack();
                if (predicate.test(stack)) {
                    int extract = storage.extract(variant, maxAmount, transaction);
                    if (extract == 0) {
                        continue;
                    }
                    return directCopy(stack, (int) extract);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack preciseCount(Predicate<ItemStack> predicate, int maxAmount) {
        if (maxAmount == 0) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                ItemResource variant = view.getResource();
                ItemStack stack = variant.toStack();
                if (predicate.test(stack)) {
                    int extract = storage.extract(variant, maxAmount, transaction);
                    if (extract != maxAmount) {
                        continue;
                    }
                    return directCopy(stack, (int) extract);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public int countAll(Predicate<ItemStack> predicate, int maxAmount) {
        if (maxAmount == 0) {
            return 0;
        }
        int count = 0;
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                ItemResource variant = view.getResource();
                if (predicate.test(variant.toStack())) {
                    count += view.extract(variant, view.getAmountAsInt(), transaction);
                    if (count >= maxAmount) {
                        return maxAmount;
                    }
                }
            }
        }
        return count;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack countAny() {
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                ItemResource variant = view.getResource();
                ItemStack stack = variant.toStack();
                int extract = view.extract(variant, view.getAmountAsInt(), transaction);
                if (extract == 0) {
                    continue;
                }
                return directCopy(stack, (int) extract);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack countAny(int maxAmount) {
        if (maxAmount == 0) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                ItemResource variant = view.getResource();
                ItemStack stack = variant.toStack();
                int extract = storage.extract(variant, maxAmount, transaction);
                if (extract == 0) {
                    continue;
                }
                return directCopy(stack, (int) extract);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int countSpace(ItemStack stack) {
        int maxAmount = stack.getCount();
        if (maxAmount == 0) {
            return 0;
        }
        return countSpace(stack, maxAmount);
    }

    @Override
    public int countSpace(ItemStack stack, int maxAmount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int insert = storage.insert(
                ItemResource.of(stack),
                maxAmount,
                transaction
            );
            return insert;
        }
    }

    @Override
    public boolean countSpace(List<ItemStack> stacks) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return true;
        }
        if (listSize == 1) {
            ItemStack stack = stacks.getFirst();
            int count = stack.getCount();
            return countSpace(stack, count) == count;
        }
        Object2IntLinkedOpenCustomHashMap<ItemStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            ITEM_STACK_HASH_STRATEGY);
        for (ItemStack stack : stacks) {
            map.merge(stack, stack.getCount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<ItemStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<ItemStack> entry = entries.first();
            ItemStack stack = entry.getKey();
            int count = entry.getIntValue();
            return countSpace(stack, count) == count;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            ObjectIterator<Object2IntMap.Entry<ItemStack>> iterator = entries.fastIterator();
            do {
                Object2IntMap.Entry<ItemStack> entry = iterator.next();
                ItemStack stack = entry.getKey();
                int count = entry.getIntValue();
                int insert = storage.insert(
                    ItemResource.of(stack),
                    count,
                    transaction
                );
                if (insert < count) {
                    return false;
                }
            } while (iterator.hasNext());
            return true;
        }
    }

    @Override
    public int countSpace(ItemStack stack, int maxAmount, int start, int end) {
        return countSpace(stack, maxAmount);
    }

    @Override
    public boolean countSpace(List<ItemStack> stacks, int start, int end) {
        return countSpace(stacks);
    }

    @Override
    public int extract(ItemStack stack) {
        int maxAmount = stack.getCount();
        if (maxAmount == 0) {
            return 0;
        }
        return extract(stack, maxAmount);
    }

    @Override
    public int extract(ItemStack stack, int maxAmount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int extract = storage.extract(
                ItemResource.of(stack),
                maxAmount,
                transaction
            );
            transaction.commit();
            return extract;
        }
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack extract(Predicate<ItemStack> predicate) {
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                ItemResource variant = view.getResource();
                ItemStack stack = variant.toStack();
                if (predicate.test(stack)) {
                    int extract = view.extract(variant, view.getAmountAsInt(), transaction);
                    if (extract == 0) {
                        continue;
                    }
                    transaction.commit();
                    return directCopy(stack, (int) extract);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack extract(Predicate<ItemStack> predicate, int maxAmount) {
        if (maxAmount == 0) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                ItemResource variant = view.getResource();
                ItemStack stack = variant.toStack();
                if (predicate.test(stack)) {
                    int extract = storage.extract(variant, maxAmount, transaction);
                    if (extract == 0) {
                        continue;
                    }
                    transaction.commit();
                    return directCopy(stack, (int) extract);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<ItemStack> extract(List<ItemStack> stacks) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return stacks;
        }
        if (listSize == 1) {
            ItemStack stack = stacks.getFirst();
            int count = stack.getCount();
            int extract = extract(stacks.getFirst(), count);
            if (count == extract) {
                return List.of();
            }
            if (extract == 0) {
                return stacks;
            }
            return List.of(directCopy(stack, count - extract));
        }
        Object2IntLinkedOpenCustomHashMap<ItemStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            ITEM_STACK_HASH_STRATEGY);
        for (ItemStack stack : stacks) {
            map.merge(stack, stack.getCount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<ItemStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<ItemStack> entry = entries.first();
            ItemStack stack = entry.getKey();
            int count = entry.getIntValue();
            int extract = extract(stack, count);
            if (count == extract) {
                return List.of();
            }
            if (extract == 0) {
                return stacks;
            }
            return List.of(directCopy(stack, count - extract));
        }
        try (Transaction transaction = Transaction.openRoot()) {
            boolean dirty = false;
            Map<ItemStack, ItemResource> cache = new IdentityHashMap<>();
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                ObjectIterator<Object2IntMap.Entry<ItemStack>> iterator = entries.fastIterator();
                do {
                    Object2IntMap.Entry<ItemStack> entry = iterator.next();
                    ItemStack stack = entry.getKey();
                    int count = entry.getIntValue();
                    ItemResource variant = cache.computeIfAbsent(stack, ItemResource::of);
                    int extract = view.extract(variant, count, transaction);
                    if (extract == 0) {
                        continue;
                    }
                    if (extract == count) {
                        iterator.remove();
                        if (entries.isEmpty()) {
                            transaction.commit();
                            return List.of();
                        }
                    } else {
                        entry.setValue(count - (int) extract);
                    }
                    dirty = true;
                    break;
                } while (iterator.hasNext());
            }
            if (dirty) {
                List<ItemStack> result = new ArrayList<>();
                for (Object2IntMap.Entry<ItemStack> entry : entries) {
                    ItemStack stack = entry.getKey();
                    int count = entry.getIntValue();
                    if (stack.getCount() == count) {
                        result.add(stack);
                    } else {
                        result.add(directCopy(stack, count));
                    }
                }
                transaction.commit();
                return result;
            }
            return stacks;
        }
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack extractAny() {
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                ItemResource variant = view.getResource();
                int extract = view.extract(variant, view.getAmountAsInt(), transaction);
                if (extract == 0) {
                    continue;
                }
                transaction.commit();
                return directCopy(variant.toStack(), (int) extract);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack extractAny(int maxAmount) {
        if (maxAmount == 0) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                ItemResource variant = view.getResource();
                int extract = storage.extract(variant, maxAmount, transaction);
                if (extract == 0) {
                    continue;
                }
                transaction.commit();
                return directCopy(variant.toStack(), (int) extract);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack extractAnyMax() {
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                ItemResource variant = view.getResource();
                int maxAmount = variant.getComponents().getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
                int extract = storage.extract(variant, maxAmount, transaction);
                if (extract == 0) {
                    continue;
                }
                transaction.commit();
                return directCopy(variant.toStack(), (int) extract);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack extractMax(Predicate<ItemStack> predicate) {
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                ItemResource variant = view.getResource();
                ItemStack stack = variant.toStack();
                if (predicate.test(stack)) {
                    int maxAmount = variant.getComponents().getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
                    int extract = storage.extract(variant, maxAmount, transaction);
                    if (extract == 0) {
                        continue;
                    }
                    transaction.commit();
                    return directCopy(stack, (int) extract);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public int extractAll(Predicate<ItemStack> predicate, int maxAmount) {
        if (maxAmount == 0) {
            return 0;
        }
        int remaining = maxAmount;
        try (Transaction transaction = Transaction.openRoot()) {
            for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
                ItemResource variant = view.getResource();
                if (predicate.test(variant.toStack())) {
                    int extract = view.extract(variant, remaining, transaction);
                    if (extract == remaining) {
                        transaction.commit();
                        return maxAmount;
                    }
                    remaining -= extract;
                }
            }
            transaction.commit();
        }
        return (int) (maxAmount - remaining);
    }

    @Override
    public int forceInsert(ItemStack stack) {
        return insert(stack);
    }

    @Override
    public int forceInsert(ItemStack stack, int maxAmount) {
        return insert(stack, maxAmount);
    }

    @Override
    public boolean forcePreciseInsert(ItemStack stack) {
        return preciseInsert(stack);
    }

    @Override
    public boolean forcePreciseInsert(ItemStack stack, int maxAmount) {
        return preciseInsert(stack, maxAmount);
    }

    @Override
    public int getMaxStackSize() {
        return capacity;
    }

    @Nullable
    protected abstract S getSlotView(int slot);

    @Override
    public ItemStack getItem(int slot) {
        if (slot >= getContainerSize()) {
            return ItemStack.EMPTY;
        }
        ResourceView<ItemResource> view = getSlotView(slot);
        if (view == null) {
            return ItemStack.EMPTY;
        }
        ItemResource variant = view.getResource();
        return new ItemStack(variant.typeHolder(), view.getAmountAsInt(), variant.getComponentsPatch());
    }

    protected void init() {
        int max = 0;
        for (ResourceView<ItemResource> view : ResourceView.all(storage)) {
            int capacity = view.getCapacityAsInt();
            if (max < capacity) {
                max = capacity;
            }
        }
        capacity = max;
    }

    protected abstract void insert(S view, ItemResource variant, int amount, Transaction transaction);

    @Override
    public int insert(ItemStack stack) {
        int maxAmount = stack.getCount();
        if (maxAmount == 0) {
            return 0;
        }
        return insert(stack, maxAmount);
    }

    @Override
    public int insert(ItemStack stack, int maxAmount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int insert = storage.insert(
                ItemResource.of(stack),
                maxAmount,
                transaction
            );
            transaction.commit();
            return insert;
        }
    }

    @Override
    public List<ItemStack> insert(List<ItemStack> stacks) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return stacks;
        }
        if (listSize == 1) {
            ItemStack stack = stacks.getFirst();
            int count = stack.getCount();
            int insert = insert(stacks.getFirst(), count);
            if (count == insert) {
                return List.of();
            }
            if (insert == 0) {
                return stacks;
            }
            return List.of(directCopy(stack, count - insert));
        }
        Object2IntLinkedOpenCustomHashMap<ItemStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            ITEM_STACK_HASH_STRATEGY);
        for (ItemStack stack : stacks) {
            map.merge(stack, stack.getCount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<ItemStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<ItemStack> entry = entries.first();
            ItemStack stack = entry.getKey();
            int count = entry.getIntValue();
            int insert = insert(stack, count);
            if (count == insert) {
                return List.of();
            }
            if (insert == 0) {
                return stacks;
            }
            return List.of(directCopy(stack, count - insert));
        }
        try (Transaction transaction = Transaction.openRoot()) {
            boolean dirty = false;
            ObjectIterator<Object2IntMap.Entry<ItemStack>> iterator = entries.fastIterator();
            do {
                Object2IntMap.Entry<ItemStack> entry = iterator.next();
                ItemStack stack = entry.getKey();
                int count = entry.getIntValue();
                int insert = storage.insert(
                    ItemResource.of(stack),
                    count,
                    transaction
                );
                if (insert == count) {
                    iterator.remove();
                    if (entries.isEmpty()) {
                        transaction.commit();
                        return List.of();
                    }
                    dirty = true;
                } else if (insert != 0) {
                    entry.setValue(count - (int) insert);
                    dirty = true;
                }
            } while (iterator.hasNext());
            if (dirty) {
                List<ItemStack> result = new ArrayList<>();
                for (Object2IntMap.Entry<ItemStack> entry : entries) {
                    ItemStack stack = entry.getKey();
                    int count = entry.getIntValue();
                    if (stack.getCount() == count) {
                        result.add(stack);
                    } else {
                        result.add(directCopy(stack, count));
                    }
                }
                transaction.commit();
                return result;
            }
            return stacks;
        }
    }

    @Override
    public int insert(ItemStack stack, int maxAmount, int start, int end) {
        return insert(stack, maxAmount);
    }

    @Override
    public List<ItemStack> insert(List<ItemStack> stacks, int start, int end) {
        return insert(stacks);
    }

    @Override
    public int insertExist(ItemStack stack) {
        int maxAmount = stack.getCount();
        if (maxAmount == 0) {
            return 0;
        }
        return insertExist(stack, maxAmount);
    }

    @Override
    public int insertExist(ItemStack stack, int maxAmount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int insert = storage.insert(
                ItemResource.of(stack),
                maxAmount,
                transaction
            );
            transaction.commit();
            return insert;
        }
    }

    @Override
    public boolean isEmpty() {
        for (ResourceView<ItemResource> _ : ResourceView.nonEmpty(storage)) {
            return false;
        }
        return true;
    }

    @Override
    public java.util.Iterator<ItemStack> iterator() {
        return ResourceView.supportsExtraction(storage) ? new Iterator(storage) : Collections.emptyIterator();
    }

    @Override
    public boolean preciseExtract(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int amount = stack.getCount();
            int extract = storage.extract(
                ItemResource.of(stack),
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
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack preciseExtract(Predicate<ItemStack> predicate, int maxAmount) {
        if (maxAmount == 0) {
            return ItemStack.EMPTY;
        }
        for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
            ItemResource variant = view.getResource();
            ItemStack stack = variant.toStack();
            if (predicate.test(stack)) {
                try (Transaction transaction = Transaction.openRoot()) {
                    int extract = storage.extract(variant, maxAmount, transaction);
                    if (extract != maxAmount) {
                        continue;
                    }
                    transaction.commit();
                    return directCopy(stack, maxAmount);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean preciseInsert(ItemStack stack) {
        int maxAmount = stack.getCount();
        if (maxAmount == 0) {
            return true;
        }
        return preciseInsert(stack, maxAmount);
    }

    @Override
    public boolean preciseInsert(ItemStack stack, int maxAmount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int insert = storage.insert(
                ItemResource.of(stack),
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
    public boolean preciseInsert(List<ItemStack> stacks) {
        int listSize = stacks.size();
        if (listSize == 0) {
            return true;
        }
        if (listSize == 1) {
            return preciseInsert(stacks.getFirst());
        }
        Object2IntLinkedOpenCustomHashMap<ItemStack> map = new Object2IntLinkedOpenCustomHashMap<>(
            ITEM_STACK_HASH_STRATEGY);
        for (ItemStack stack : stacks) {
            map.merge(stack, stack.getCount(), Integer::sum);
        }
        Object2IntSortedMap.FastSortedEntrySet<ItemStack> entries = map.object2IntEntrySet();
        if (entries.size() == 1) {
            Object2IntMap.Entry<ItemStack> entry = entries.first();
            return preciseInsert(entry.getKey(), entry.getIntValue());
        }
        try (Transaction transaction = Transaction.openRoot()) {
            ObjectIterator<Object2IntMap.Entry<ItemStack>> iterator = entries.fastIterator();
            do {
                Object2IntMap.Entry<ItemStack> entry = iterator.next();
                ItemStack stack = entry.getKey();
                int count = entry.getIntValue();
                int insert = storage.insert(
                    ItemResource.of(stack),
                    count,
                    transaction
                );
                if (insert < count) {
                    return false;
                }
            } while (iterator.hasNext());
            transaction.commit();
            return true;
        }
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot >= getContainerSize()) {
            return ItemStack.EMPTY;
        }
        ResourceView<ItemResource> view = getSlotView(slot);
        if (view == null) {
            return ItemStack.EMPTY;
        }
        ItemResource variant = view.getResource();
        int amount = view.getAmountAsInt();
        if (variant.isEmpty() || amount == 0) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            amount = view.extract(variant, amount, transaction);
            transaction.commit();
        }
        return new ItemStack(variant.typeHolder(), (int) amount, variant.getComponentsPatch());
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot >= getContainerSize()) {
            return ItemStack.EMPTY;
        }
        ResourceView<ItemResource> view = getSlotView(slot);
        if (view == null) {
            return ItemStack.EMPTY;
        }
        ItemResource variant = view.getResource();
        if (variant.isEmpty() || view.getAmountAsInt() == 0) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            amount = (int) view.extract(variant, amount, transaction);
            transaction.commit();
        }
        return new ItemStack(variant.typeHolder(), amount, variant.getComponentsPatch());
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= getContainerSize()) {
            return;
        }
        S view = getSlotView(slot);
        if (view == null) {
            return;
        }
        ItemResource variant = view.getResource();
        try (Transaction transaction = Transaction.openRoot()) {
            if (variant.isEmpty() || view.getAmountAsInt() == 0) {
                if (stack.isEmpty()) {
                    return;
                }
                insert(
                    view,
                    ItemResource.of(stack),
                    stack.getCount(),
                    transaction
                );
            } else if (variant.matches(stack)) {
                int amount = stack.getCount();
                int targetCount = view.getAmountAsInt();
                if (amount == targetCount) {
                    return;
                }
                int change = targetCount - amount;
                if (amount < targetCount) {
                    insert(view, variant, change, transaction);
                } else {
                    view.extract(variant, change, transaction);
                }
            } else {
                view.extract(variant, view.getAmountAsInt(), transaction);
                insert(
                    view,
                    ItemResource.of(stack),
                    stack.getCount(),
                    transaction
                );
            }
            transaction.commit();
        }
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public boolean update(Predicate<ItemStack> predicate, Function<ItemStack, ItemStack> update) {
        for (ResourceView<ItemResource> view : ResourceView.nonEmpty(storage)) {
            ItemResource variant = view.getResource();
            ItemStack stack = variant.toStack();
            if (predicate.test(stack)) {
                try (Transaction transaction = Transaction.openRoot()) {
                    int amount = view.getAmountAsInt();
                    ItemStack replace = update.apply(new ItemStack(
                        variant.typeHolder(),
                        (int) amount,
                        variant.getComponentsPatch()
                    ));
                    if (ItemStack.isSameItemSameComponents(stack, replace)) {
                        int count = replace.getCount();
                        if (count == amount) {
                            return true;
                        }
                        long change;
                        if (count < amount) {
                            change = view.extract(variant, amount - count, transaction);
                        } else {
                            change = storage.insert(variant, count - amount, transaction);
                        }
                        if (change != 0) {
                            transaction.commit();
                            return true;
                        }
                    } else {
                        int extract = view.extract(variant, amount, transaction);
                        if (extract != 0) {
                            if (replace.isEmpty()) {
                                transaction.commit();
                                return true;
                            }
                            int insert = storage.insert(
                                ItemResource.of(replace),
                                replace.getCount(),
                                transaction
                            );
                            if (insert != 0) {
                                transaction.commit();
                                return true;
                            }
                        }
                        return false;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public Stream<ItemStack> stream() {
        return ResourceView.supportsExtraction(storage) ? ItemInventory.super.stream() : Stream.empty();
    }

    public static class Filter implements SidedItemInventory {
        private final int[] slots;
        private final boolean canInsert;
        private final boolean canExtract;
        private final InventoryWrapper<?, ?> inventory;

        public Filter(InventoryWrapper<?, ?> inventory) {
            this.inventory = inventory;
            slots = SlotRangeCache.get(inventory.getContainerSize());
            canInsert = ResourceView.supportsInsertion(inventory.storage);
            canExtract = ResourceView.supportsExtraction(inventory.storage);
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
            return canExtract;
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
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
        public int count(ItemStack stack, @Nullable Direction side) {
            return count(stack);
        }

        @Override
        public int count(ItemStack stack) {
            if (!canExtract) {
                return 0;
            }
            return inventory.count(stack);
        }

        @Override
        public int count(ItemStack stack, int maxAmount, @Nullable Direction side) {
            return count(stack, maxAmount);
        }

        @Override
        public int count(ItemStack stack, int maxAmount) {
            if (!canExtract) {
                return 0;
            }
            return inventory.count(stack, maxAmount);
        }

        @Override
        public ItemStack count(Predicate<ItemStack> predicate, @Nullable Direction side) {
            return count(predicate);
        }

        @Override
        public ItemStack count(Predicate<ItemStack> predicate) {
            if (!canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.count(predicate);
        }

        @Override
        public ItemStack count(Predicate<ItemStack> predicate, int maxAmount, @Nullable Direction side) {
            return count(predicate, maxAmount);
        }

        @Override
        public ItemStack count(Predicate<ItemStack> predicate, int maxAmount) {
            if (!canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.count(predicate, maxAmount);
        }

        @Override
        public ItemStack preciseCount(Predicate<ItemStack> predicate, int maxAmount, @Nullable Direction side) {
            return preciseCount(predicate, maxAmount);
        }

        @Override
        public ItemStack preciseCount(Predicate<ItemStack> predicate, int maxAmount) {
            if (!canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.preciseCount(predicate, maxAmount);
        }

        @Override
        public int countAll(Predicate<ItemStack> predicate, int maxAmount, @Nullable Direction side) {
            return countAll(predicate, maxAmount);
        }

        @Override
        public int countAll(Predicate<ItemStack> predicate, int maxAmount) {
            if (!canExtract) {
                return 0;
            }
            return inventory.countAll(predicate, maxAmount);
        }

        @Override
        public ItemStack countAny(@Nullable Direction side) {
            return countAny();
        }

        @Override
        public ItemStack countAny() {
            if (!canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.countAny();
        }

        @Override
        public ItemStack countAny(int maxAmount, @Nullable Direction side) {
            return countAny(maxAmount);
        }

        @Override
        public ItemStack countAny(int maxAmount) {
            if (!canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.countAny(maxAmount);
        }

        @Override
        public int countSpace(ItemStack stack, @Nullable Direction side) {
            return countSpace(stack);
        }

        @Override
        public int countSpace(ItemStack stack) {
            if (!canInsert) {
                return 0;
            }
            return inventory.countSpace(stack);
        }

        @Override
        public int countSpace(ItemStack stack, int maxAmount, @Nullable Direction side) {
            return countSpace(stack, maxAmount);
        }

        @Override
        public int countSpace(ItemStack stack, int maxAmount) {
            if (!canInsert) {
                return 0;
            }
            return inventory.countSpace(stack, maxAmount);
        }

        @Override
        public boolean countSpace(List<ItemStack> stacks, @Nullable Direction side) {
            return countSpace(stacks);
        }

        @Override
        public boolean countSpace(List<ItemStack> stacks) {
            if (!canInsert) {
                return false;
            }
            return inventory.countSpace(stacks);
        }

        @Override
        public int extract(ItemStack stack, @Nullable Direction side) {
            return extract(stack);
        }

        @Override
        public int extract(ItemStack stack) {
            if (!canExtract) {
                return 0;
            }
            return inventory.extract(stack);
        }

        @Override
        public int extract(ItemStack stack, int maxAmount, @Nullable Direction side) {
            return extract(stack, maxAmount);
        }

        @Override
        public int extract(ItemStack stack, int maxAmount) {
            if (!canExtract) {
                return 0;
            }
            return inventory.extract(stack, maxAmount);
        }

        @Override
        public ItemStack extract(Predicate<ItemStack> predicate, @Nullable Direction side) {
            return extract(predicate);
        }

        @Override
        public ItemStack extract(Predicate<ItemStack> predicate) {
            if (!canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.extract(predicate);
        }

        @Override
        public ItemStack extract(Predicate<ItemStack> predicate, int maxAmount, @Nullable Direction side) {
            return extract(predicate, maxAmount);
        }

        @Override
        public ItemStack extract(Predicate<ItemStack> predicate, int maxAmount) {
            if (!canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.extract(predicate, maxAmount);
        }

        @Override
        public List<ItemStack> extract(List<ItemStack> stacks, @Nullable Direction side) {
            return extract(stacks);
        }

        @Override
        public List<ItemStack> extract(List<ItemStack> stacks) {
            if (!canExtract) {
                return stacks;
            }
            return inventory.extract(stacks);
        }

        @Override
        public int extractAll(Predicate<ItemStack> predicate, int maxAmount, @Nullable Direction side) {
            return extractAll(predicate, maxAmount);
        }

        @Override
        public int extractAll(Predicate<ItemStack> predicate, int maxAmount) {
            if (!canExtract) {
                return 0;
            }
            return inventory.extractAll(predicate, maxAmount);
        }

        @Override
        public ItemStack extractAny(@Nullable Direction side) {
            return extractAny();
        }

        @Override
        public ItemStack extractAny() {
            if (!canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.extractAny();
        }

        @Override
        public ItemStack extractAny(int maxAmount, @Nullable Direction side) {
            return extractAny(maxAmount);
        }

        @Override
        public ItemStack extractAny(int maxAmount) {
            if (!canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.extractAny(maxAmount);
        }

        @Override
        public ItemStack extractAnyMax(@Nullable Direction side) {
            return extractAnyMax();
        }

        @Override
        public ItemStack extractAnyMax() {
            if (!canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.extractAnyMax();
        }

        @Override
        public ItemStack extractMax(Predicate<ItemStack> predicate, @Nullable Direction side) {
            return extractMax(predicate);
        }

        @Override
        public ItemStack extractMax(Predicate<ItemStack> predicate) {
            if (!canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.extractMax(predicate);
        }

        @Override
        public int forceInsert(ItemStack stack) {
            return inventory.forceInsert(stack);
        }

        @Override
        public int forceInsert(ItemStack stack, int maxAmount) {
            return inventory.forceInsert(stack, maxAmount);
        }

        @Override
        public boolean forcePreciseInsert(ItemStack stack) {
            return inventory.forcePreciseInsert(stack);
        }

        @Override
        public boolean forcePreciseInsert(ItemStack stack, int maxAmount) {
            return inventory.forcePreciseInsert(stack, maxAmount);
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return slots;
        }

        @Override
        public int getMaxStackSize() {
            return inventory.getMaxStackSize();
        }

        @Override
        public ItemStack getItem(int slot) {
            return inventory.getItem(slot);
        }

        @Override
        public int insert(ItemStack stack, @Nullable Direction side) {
            return insert(stack);
        }

        @Override
        public int insert(ItemStack stack) {
            if (!canInsert) {
                return 0;
            }
            return inventory.insert(stack);
        }

        @Override
        public int insert(ItemStack stack, int maxAmount, @Nullable Direction side) {
            return insert(stack, maxAmount);
        }

        @Override
        public int insert(ItemStack stack, int maxAmount) {
            if (!canInsert) {
                return 0;
            }
            return inventory.insert(stack, maxAmount);
        }

        @Override
        public int insert(ItemStack stack, int maxAmount, int start, int end, @Nullable Direction side) {
            return insert(stack, maxAmount);
        }

        @Override
        public int insert(ItemStack stack, int maxAmount, int start, int end) {
            if (!canInsert) {
                return 0;
            }
            return inventory.insert(stack, maxAmount, start, end);
        }

        @Override
        public List<ItemStack> insert(List<ItemStack> stacks, @Nullable Direction side) {
            return insert(stacks);
        }

        @Override
        public List<ItemStack> insert(List<ItemStack> stacks) {
            if (!canInsert) {
                return stacks;
            }
            return inventory.insert(stacks);
        }

        @Override
        public List<ItemStack> insert(List<ItemStack> stacks, int start, int end, @Nullable Direction side) {
            return insert(stacks, start, end);
        }

        @Override
        public List<ItemStack> insert(List<ItemStack> stacks, int start, int end) {
            if (!canInsert) {
                return stacks;
            }
            return inventory.insert(stacks, start, end);
        }

        @Override
        public int insertExist(ItemStack stack, @Nullable Direction side) {
            return insertExist(stack);
        }

        @Override
        public int insertExist(ItemStack stack) {
            if (!canInsert) {
                return 0;
            }
            return inventory.insertExist(stack);
        }

        @Override
        public int insertExist(ItemStack stack, int maxAmount, @Nullable Direction side) {
            if (!canInsert) {
                return 0;
            }
            return insertExist(stack, maxAmount);
        }

        @Override
        public int insertExist(ItemStack stack, int maxAmount) {
            return inventory.insertExist(stack, maxAmount);
        }

        @Override
        public boolean isEmpty() {
            return inventory.isEmpty();
        }

        @Override
        public java.util.Iterator<ItemStack> iterator() {
            return inventory.iterator();
        }

        @Override
        public java.util.Iterator<ItemStack> iterator(@Nullable Direction side) {
            return inventory.iterator();
        }

        @Override
        public boolean preciseExtract(ItemStack stack, @Nullable Direction side) {
            return preciseExtract(stack);
        }

        @Override
        public boolean preciseExtract(ItemStack stack) {
            if (!canExtract) {
                return stack.isEmpty();
            }
            return inventory.preciseExtract(stack);
        }

        @Override
        public ItemStack preciseExtract(Predicate<ItemStack> predicate, int maxAmount, @Nullable Direction side) {
            return preciseExtract(predicate, maxAmount);
        }

        @Override
        public ItemStack preciseExtract(Predicate<ItemStack> predicate, int maxAmount) {
            if (!canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.preciseExtract(predicate, maxAmount);
        }

        @Override
        public boolean preciseInsert(ItemStack stack, @Nullable Direction side) {
            return preciseInsert(stack);
        }

        @Override
        public boolean preciseInsert(ItemStack stack) {
            if (!canInsert) {
                return stack.isEmpty();
            }
            return inventory.preciseInsert(stack);
        }

        @Override
        public boolean preciseInsert(ItemStack stack, int maxAmount, @Nullable Direction side) {
            return preciseInsert(stack, maxAmount);
        }

        @Override
        public boolean preciseInsert(ItemStack stack, int maxAmount) {
            if (!canInsert) {
                return stack.isEmpty();
            }
            return inventory.preciseInsert(stack, maxAmount);
        }

        @Override
        public boolean preciseInsert(List<ItemStack> stacks, @Nullable Direction side) {
            return preciseInsert(stacks);
        }

        @Override
        public boolean preciseInsert(List<ItemStack> stacks) {
            if (!canInsert) {
                return false;
            }
            return inventory.preciseInsert(stacks);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (!canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.removeItemNoUpdate(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (!canExtract) {
                return ItemStack.EMPTY;
            }
            return inventory.removeItem(slot, amount);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            inventory.setItem(slot, stack);
        }

        @Override
        public int getContainerSize() {
            return inventory.getContainerSize();
        }

        @Override
        public Stream<ItemStack> stream() {
            return inventory.stream();
        }

        @Override
        public Stream<ItemStack> stream(@Nullable Direction side) {
            return inventory.stream();
        }
    }

    // Upstream had two subclasses here, Direct and Slotted, because Fabric's Storage came in
    // two shapes: an opaque iterable one and an indexed one. NeoForge's ResourceHandler is
    // always indexed, so only the Slotted behaviour can apply and Direct has been removed
    // rather than ported -- it would have been unreachable code pretending to handle a case
    // that no longer exists.
    protected static class Slotted extends InventoryWrapper<ResourceHandler<ItemResource>, ResourceView<ItemResource>> {
        public Slotted(ResourceHandler<ItemResource> storage) {
            super(storage);
        }

        @Override
        protected ResourceView<ItemResource> getSlotView(int slot) {
            return new ResourceView<>(storage, slot);
        }

        @Override
        protected void insert(
            ResourceView<ItemResource> view,
            ItemResource variant,
            int amount,
            Transaction transaction
        ) {
            view.insert(variant, amount, transaction);
        }

        @Override
        public int insertExist(ItemStack stack) {
            if (stack.isEmpty()) {
                return 0;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                List<ResourceView<ItemResource>> emptys = new ArrayList<>();
                ItemResource variant = ItemResource.of(stack);
                int maxAmount = stack.getCount();
                int remaining = maxAmount;
                for (int i = 0, size = storage.size(); i < size; i++) {
                    ResourceView<ItemResource> target = new ResourceView<>(storage, i);
                    if (target.getAmountAsInt() == 0 || target.isResourceBlank()) {
                        emptys.add(target);
                    } else {
                        int insert = target.insert(variant, remaining, transaction);
                        if (insert == remaining) {
                            transaction.commit();
                            return maxAmount;
                        }
                        remaining -= insert;
                    }
                }
                for (ResourceView<ItemResource> target : ResourceView.all(emptys)) {
                    int insert = target.insert(variant, remaining, transaction);
                    if (insert == remaining) {
                        transaction.commit();
                        return maxAmount;
                    }
                    remaining -= insert;
                }
                transaction.commit();
                return maxAmount - remaining;
            }
        }

        @Override
        public int countSpace(ItemStack stack, int maxAmount, int start, int end) {
            try (Transaction transaction = Transaction.openRoot()) {
                ItemResource variant = ItemResource.of(stack);
                int remaining = maxAmount;
                for (int i = start; i <= end; i++) {
                    ResourceView<ItemResource> target = new ResourceView<>(storage, i);
                    int insert = target.insert(variant, remaining, transaction);
                    if (insert == remaining) {
                        return maxAmount;
                    }
                    remaining -= insert;
                }
                return maxAmount - remaining;
            }
        }

        @Override
        public boolean countSpace(List<ItemStack> stacks, int start, int end) {
            int listSize = stacks.size();
            if (listSize == 0) {
                return true;
            }
            if (listSize == 1) {
                ItemStack stack = stacks.getFirst();
                int count = stack.getCount();
                return countSpace(stack, count, start, end) == count;
            }
            Object2IntLinkedOpenCustomHashMap<ItemStack> map = new Object2IntLinkedOpenCustomHashMap<>(
                ITEM_STACK_HASH_STRATEGY);
            for (ItemStack stack : stacks) {
                map.merge(stack, stack.getCount(), Integer::sum);
            }
            Object2IntSortedMap.FastSortedEntrySet<ItemStack> entries = map.object2IntEntrySet();
            if (entries.size() == 1) {
                Object2IntMap.Entry<ItemStack> entry = entries.first();
                ItemStack stack = entry.getKey();
                int count = entry.getIntValue();
                return countSpace(stack, count, start, end) == count;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                ObjectIterator<Object2IntMap.Entry<ItemStack>> iterator = entries.fastIterator();
                do {
                    Object2IntMap.Entry<ItemStack> entry = iterator.next();
                    ItemStack stack = entry.getKey();
                    int count = entry.getIntValue();
                    ItemResource variant = ItemResource.of(stack);
                    int remaining = count;
                    for (int i = start; i <= end; i++) {
                        ResourceView<ItemResource> target = new ResourceView<>(storage, i);
                        remaining -= target.insert(variant, remaining, transaction);
                        if (remaining == 0) {
                            break;
                        }
                    }
                    if (remaining == 0) {
                        iterator.remove();
                        if (entries.isEmpty()) {
                            return true;
                        }
                    } else if (remaining != count) {
                        entry.setValue((int) remaining);
                    }
                } while (iterator.hasNext());
                return false;
            }
        }

        @Override
        public int insert(ItemStack stack, int maxAmount, int start, int end) {
            try (Transaction transaction = Transaction.openRoot()) {
                ItemResource variant = ItemResource.of(stack);
                int remaining = maxAmount;
                for (int i = start; i <= end; i++) {
                    ResourceView<ItemResource> target = new ResourceView<>(storage, i);
                    int insert = target.insert(variant, remaining, transaction);
                    if (insert == remaining) {
                        transaction.commit();
                        return maxAmount;
                    }
                    remaining -= insert;
                }
                transaction.commit();
                return maxAmount - remaining;
            }
        }

        @Override
        public List<ItemStack> insert(List<ItemStack> stacks) {
            int listSize = stacks.size();
            if (listSize == 0) {
                return stacks;
            }
            if (listSize == 1) {
                ItemStack stack = stacks.getFirst();
                int count = stack.getCount();
                int insert = insert(stacks.getFirst(), count);
                if (count == insert) {
                    return List.of();
                }
                if (insert == 0) {
                    return stacks;
                }
                return List.of(directCopy(stack, count - insert));
            }
            Object2IntLinkedOpenCustomHashMap<ItemStack> map = new Object2IntLinkedOpenCustomHashMap<>(
                ITEM_STACK_HASH_STRATEGY);
            for (ItemStack stack : stacks) {
                map.merge(stack, stack.getCount(), Integer::sum);
            }
            Object2IntSortedMap.FastSortedEntrySet<ItemStack> entries = map.object2IntEntrySet();
            if (entries.size() == 1) {
                Object2IntMap.Entry<ItemStack> entry = entries.first();
                ItemStack stack = entry.getKey();
                int count = entry.getIntValue();
                int insert = insert(stack, count);
                if (count == insert) {
                    return List.of();
                }
                if (insert == 0) {
                    return stacks;
                }
                return List.of(directCopy(stack, count - insert));
            }
            try (Transaction transaction = Transaction.openRoot()) {
                boolean dirty = false;
                Map<ItemStack, ItemResource> cache = new IdentityHashMap<>();
                for (int i = 0, size = storage.size(); i < size; i++) {
                    ResourceView<ItemResource> slot = new ResourceView<>(storage, i);
                    ObjectIterator<Object2IntMap.Entry<ItemStack>> iterator = entries.fastIterator();
                    do {
                        Object2IntMap.Entry<ItemStack> entry = iterator.next();
                        ItemStack stack = entry.getKey();
                        int count = entry.getIntValue();
                        ItemResource variant = cache.computeIfAbsent(stack, ItemResource::of);
                        int insert = slot.insert(variant, count, transaction);
                        if (insert == 0) {
                            continue;
                        }
                        if (insert == count) {
                            iterator.remove();
                            if (entries.isEmpty()) {
                                transaction.commit();
                                return List.of();
                            }
                        } else {
                            entry.setValue(count - (int) insert);
                        }
                        dirty = true;
                        break;
                    } while (iterator.hasNext());
                }
                if (dirty) {
                    List<ItemStack> result = new ArrayList<>();
                    for (Object2IntMap.Entry<ItemStack> entry : entries) {
                        ItemStack stack = entry.getKey();
                        int count = entry.getIntValue();
                        if (stack.getCount() == count) {
                            result.add(stack);
                        } else {
                            result.add(directCopy(stack, count));
                        }
                    }
                    transaction.commit();
                    return result;
                }
                return stacks;
            }
        }

        @Override
        public List<ItemStack> insert(List<ItemStack> stacks, int start, int end) {
            int listSize = stacks.size();
            if (listSize == 0) {
                return stacks;
            }
            if (listSize == 1) {
                ItemStack stack = stacks.getFirst();
                int count = stack.getCount();
                int insert = insert(stacks.getFirst(), count, start, end);
                if (count == insert) {
                    return List.of();
                }
                if (insert == 0) {
                    return stacks;
                }
                return List.of(directCopy(stack, count - insert));
            }
            Object2IntLinkedOpenCustomHashMap<ItemStack> map = new Object2IntLinkedOpenCustomHashMap<>(
                ITEM_STACK_HASH_STRATEGY);
            for (ItemStack stack : stacks) {
                map.merge(stack, stack.getCount(), Integer::sum);
            }
            Object2IntSortedMap.FastSortedEntrySet<ItemStack> entries = map.object2IntEntrySet();
            if (entries.size() == 1) {
                Object2IntMap.Entry<ItemStack> entry = entries.first();
                ItemStack stack = entry.getKey();
                int count = entry.getIntValue();
                int insert = insert(stack, count, start, end);
                if (count == insert) {
                    return List.of();
                }
                if (insert == 0) {
                    return stacks;
                }
                return List.of(directCopy(stack, count - insert));
            }
            try (Transaction transaction = Transaction.openRoot()) {
                boolean dirty = false;
                ObjectIterator<Object2IntMap.Entry<ItemStack>> iterator = entries.fastIterator();
                do {
                    Object2IntMap.Entry<ItemStack> entry = iterator.next();
                    ItemStack stack = entry.getKey();
                    int count = entry.getIntValue();
                    ItemResource variant = ItemResource.of(stack);
                    int remaining = count;
                    for (int i = start; i <= end; i++) {
                        ResourceView<ItemResource> target = new ResourceView<>(storage, i);
                        remaining -= target.insert(variant, remaining, transaction);
                        if (remaining == 0) {
                            break;
                        }
                    }
                    if (remaining == 0) {
                        iterator.remove();
                        if (entries.isEmpty()) {
                            transaction.commit();
                            return List.of();
                        }
                        dirty = true;
                    } else if (remaining != count) {
                        entry.setValue((int) remaining);
                        dirty = true;
                    }
                } while (iterator.hasNext());
                if (dirty) {
                    List<ItemStack> result = new ArrayList<>();
                    for (Object2IntMap.Entry<ItemStack> entry : entries) {
                        ItemStack stack = entry.getKey();
                        int count = entry.getIntValue();
                        if (stack.getCount() == count) {
                            result.add(stack);
                        } else {
                            result.add(directCopy(stack, count));
                        }
                    }
                    transaction.commit();
                    return result;
                }
                return stacks;
            }
        }

        @Override
        @SuppressWarnings("UnstableApiUsage")
        public boolean update(Predicate<ItemStack> predicate, Function<ItemStack, ItemStack> update) {
            for (int i = 0, size = storage.size(); i < size; i++) {
                ResourceView<ItemResource> slot = new ResourceView<>(storage, i);
                ItemResource variant = slot.getResource();
                ItemStack stack = variant.toStack();
                if (predicate.test(stack)) {
                    try (Transaction transaction = Transaction.openRoot()) {
                        int amount = slot.getAmountAsInt();
                        ItemStack replace = update.apply(new ItemStack(
                            variant.typeHolder(),
                            (int) amount,
                            variant.getComponentsPatch()
                        ));
                        if (ItemStack.isSameItemSameComponents(stack, replace)) {
                            int count = replace.getCount();
                            if (count == amount) {
                                return true;
                            }
                            long change;
                            if (count < amount) {
                                change = slot.extract(variant, amount - count, transaction);
                            } else {
                                change = slot.insert(variant, count - amount, transaction);
                            }
                            if (change != 0) {
                                transaction.commit();
                                return true;
                            }
                        } else {
                            int extract = slot.extract(variant, amount, transaction);
                            if (extract != 0) {
                                if (replace.isEmpty()) {
                                    transaction.commit();
                                    return true;
                                }
                                int insert = slot.insert(
                                    ItemResource.of(replace),
                                    replace.getCount(),
                                    transaction
                                );
                                if (insert != 0) {
                                    transaction.commit();
                                    return true;
                                }
                            }
                            return false;
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public int getContainerSize() {
            return storage.size();
        }
    }

    static class Iterator implements java.util.Iterator<ItemStack> {
        private final java.util.Iterator<ResourceView<ItemResource>> iterator;

        public Iterator(ResourceHandler<ItemResource> storage) {
            iterator = storage.iterator();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public ItemStack next() {
            ResourceView<ItemResource> view = iterator.next();
            if (view.getAmountAsInt() == 0 || view.isResourceBlank()) {
                return ItemStack.EMPTY;
            }
            ItemResource variant = view.getResource();
            return new ItemStack(variant.typeHolder(), view.getAmountAsInt(), variant.getComponentsPatch());
        }
    }
}
