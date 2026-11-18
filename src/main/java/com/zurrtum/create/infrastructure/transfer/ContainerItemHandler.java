package com.zurrtum.create.infrastructure.transfer;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jspecify.annotations.Nullable;

/**
 * Exposes one of Create's {@code Container}s to other mods as an {@link IItemHandlerModifiable}.
 * The outbound half of the bridge; {@link ItemHandlerContainer} is the inbound half.
 *
 * <p>NeoForge ships {@code InvWrapper} and {@code SidedInvWrapper}, which do this at the plain
 * vanilla level by reading and writing slots. That is not good enough here: Create's containers
 * carry real behaviour on top of {@code Container} through {@code ContainerExtension}
 * (belts that only accept at an end, tunnels that forward to a neighbour, filtered depots), and a
 * slot-level write bypasses all of it. So insertion and extraction go through the extension's own
 * {@code insert}/{@code extract}, which is what Create itself calls.
 *
 * <p>The {@code side} is carried because {@code ContainerExtension} takes an optional
 * {@code Direction} on every operation and {@code WorldlyContainer} uses it to decide which slots a
 * face may touch.
 */
public class ContainerItemHandler implements IItemHandlerModifiable {
    protected final Container container;
    protected final @Nullable Direction side;

    protected ContainerItemHandler(Container container, @Nullable Direction side) {
        this.container = container;
        this.side = side;
    }

    public static ContainerItemHandler of(Container container) {
        return new ContainerItemHandler(container, null);
    }

    public static ContainerItemHandler of(Container container, @Nullable Direction side) {
        return new ContainerItemHandler(container, side);
    }

    public Container getContainer() {
        return container;
    }

    @Override
    public int getSlots() {
        return container.getContainerSize();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= container.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return container.getItem(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= container.getContainerSize()) {
            return;
        }
        container.setItem(slot, stack);
        container.setChanged();
    }

    @Override
    public int getSlotLimit(int slot) {
        return container.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot < 0 || slot >= container.getContainerSize()) {
            return false;
        }
        if (!container.canPlaceItem(slot, stack)) {
            return false;
        }
        if (side != null && container instanceof WorldlyContainer worldly) {
            return worldly.canPlaceItemThroughFace(slot, stack, side);
        }
        return true;
    }

    /**
     * Note the contract difference: {@code IItemHandler.insertItem} returns the <b>remainder</b>,
     * while Create's {@code insert} returns the amount <b>accepted</b>. Mixing the two up is a silent
     * item duplication bug, so the conversion is spelled out here.
     */
    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int offered = stack.getCount();
        int accepted = simulate
            ? container.countSpace(stack, offered, side)
            : container.insert(stack, offered, side);
        if (accepted <= 0) {
            return stack;
        }
        if (!simulate) {
            container.setChanged();
        }
        if (accepted >= offered) {
            return ItemStack.EMPTY;
        }
        return stack.copyWithCount(offered - accepted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack present = getStackInSlot(slot);
        if (present.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int wanted = Math.min(amount, present.getCount());
        int taken = simulate
            ? container.count(present, wanted, side)
            : container.extract(present, wanted, side);
        if (taken <= 0) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            container.setChanged();
        }
        return present.copyWithCount(taken);
    }
}
