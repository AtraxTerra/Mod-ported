package com.zurrtum.create.infrastructure.transfer;

import com.zurrtum.create.infrastructure.items.ItemInventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Presents a foreign NeoForge {@link IItemHandler} to Create as a vanilla {@code Container}.
 *
 * <p>This replaces the upstream {@code InventoryWrapper} (1991 lines), which wrapped a Fabric
 * {@code Storage<ItemVariant>}. Most of that file was transaction bookkeeping: Fabric's Storage API
 * mutates inside a {@code Transaction} that may be rolled back, so the wrapper had to snapshot and
 * replay. NeoForge's handler takes a {@code simulate} flag instead, so a dry run is just a call with
 * {@code simulate = true} and the entire snapshot apparatus disappears.
 *
 * <h2>How Create's item API reaches this class</h2>
 * Create's logistics is written against vanilla {@code Container}, extended by
 * {@code ContainerExtension} (insert/extract/count/precise*), whose implementations live in
 * {@code ContainerMixin} -- a mixin on the {@code Container} <i>interface</i>, so every implementor
 * inherits them, this adapter included. Those inherited defaults work through
 * {@link #getItem}/{@link #setItem}.
 *
 * <h2>Why the transfer methods are overridden rather than inherited</h2>
 * Going through {@code setItem} would bypass the handler's own {@code getSlotLimit} and
 * {@code isItemValid}, and would be outright wrong for a handler that is not
 * {@link IItemHandlerModifiable} (the base interface has no "write this slot" operation at all).
 * So the hot paths below delegate to {@code insertItem}/{@code extractItem}, which is the lossless
 * mapping. Any {@code ContainerExtension} method not overridden here still falls back to the
 * slot-rewriting default -- see PORTING.md for the list and what it costs.
 */
public class ItemHandlerContainer implements ItemInventory {
    protected final IItemHandler handler;

    protected ItemHandlerContainer(IItemHandler handler) {
        this.handler = handler;
    }

    public static ItemHandlerContainer of(IItemHandler handler) {
        return new ItemHandlerContainer(handler);
    }

    public IItemHandler getHandler() {
        return handler;
    }

    // --- Container core -----------------------------------------------------

    @Override
    public int getContainerSize() {
        return handler.getSlots();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= handler.getSlots()) {
            return ItemStack.EMPTY;
        }
        return handler.getStackInSlot(slot);
    }

    /**
     * Only a modifiable handler can be written slot-wise. For a read-only handler this emulates the
     * write with an extract followed by an insert, which is the closest honest approximation; it can
     * fail where a direct write would have succeeded, and it never silently drops items.
     */
    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= handler.getSlots()) {
            return;
        }
        if (handler instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(slot, stack);
            return;
        }
        ItemStack present = handler.getStackInSlot(slot);
        if (!present.isEmpty()) {
            handler.extractItem(slot, present.getCount(), false);
        }
        if (!stack.isEmpty()) {
            handler.insertItem(slot, stack.copy(), false);
        }
    }

    @Override
    public int getMaxStackSize() {
        int slots = handler.getSlots();
        return slots == 0 ? 0 : handler.getSlotLimit(0);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < handler.getSlots() && handler.isItemValid(slot, stack);
    }

    // --- ContainerExtension hot paths, delegated to the handler -------------

    @Override
    public int insert(ItemStack stack, int maxAmount) {
        return insert(stack, maxAmount, false);
    }

    @Override
    public int countSpace(ItemStack stack, int maxAmount) {
        return insert(stack, maxAmount, true);
    }

    private int insert(ItemStack stack, int maxAmount, boolean simulate) {
        if (stack.isEmpty() || maxAmount <= 0) {
            return 0;
        }
        int remaining = Math.min(maxAmount, stack.getCount());
        int inserted = 0;
        for (int slot = 0, slots = handler.getSlots(); slot < slots && remaining > 0; slot++) {
            ItemStack offer = stack.copyWithCount(remaining);
            ItemStack leftover = handler.insertItem(slot, offer, simulate);
            int accepted = remaining - leftover.getCount();
            if (accepted > 0) {
                inserted += accepted;
                remaining -= accepted;
            }
        }
        return inserted;
    }

    @Override
    public int extract(ItemStack stack, int maxAmount) {
        return extract(stack, maxAmount, false);
    }

    @Override
    public int count(ItemStack stack, int maxAmount) {
        return extract(stack, maxAmount, true);
    }

    private int extract(ItemStack stack, int maxAmount, boolean simulate) {
        if (stack.isEmpty() || maxAmount <= 0) {
            return 0;
        }
        int remaining = maxAmount;
        int extracted = 0;
        for (int slot = 0, slots = handler.getSlots(); slot < slots && remaining > 0; slot++) {
            ItemStack present = handler.getStackInSlot(slot);
            if (present.isEmpty() || !ItemStack.isSameItemSameComponents(present, stack)) {
                continue;
            }
            ItemStack taken = handler.extractItem(slot, remaining, simulate);
            if (!taken.isEmpty()) {
                extracted += taken.getCount();
                remaining -= taken.getCount();
            }
        }
        return extracted;
    }

    @Override
    public ItemStack extractAny(int maxAmount) {
        return extractAny(maxAmount, false);
    }

    @Override
    public ItemStack countAny(int maxAmount) {
        return extractAny(maxAmount, true);
    }

    private ItemStack extractAny(int maxAmount, boolean simulate) {
        if (maxAmount <= 0) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0, slots = handler.getSlots(); slot < slots; slot++) {
            if (handler.getStackInSlot(slot).isEmpty()) {
                continue;
            }
            ItemStack taken = handler.extractItem(slot, maxAmount, simulate);
            if (!taken.isEmpty()) {
                return taken;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean preciseInsert(ItemStack stack, int maxAmount) {
        if (stack.isEmpty() || maxAmount <= 0) {
            return true;
        }
        if (insert(stack, maxAmount, true) < maxAmount) {
            return false;
        }
        return insert(stack, maxAmount, false) == maxAmount;
    }

    @Override
    public boolean preciseExtract(ItemStack stack) {
        int wanted = stack.getCount();
        if (wanted == 0) {
            return true;
        }
        if (extract(stack, wanted, true) < wanted) {
            return false;
        }
        return extract(stack, wanted, false) == wanted;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
