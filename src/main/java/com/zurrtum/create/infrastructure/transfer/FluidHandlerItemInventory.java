package com.zurrtum.create.infrastructure.transfer;

import com.zurrtum.create.infrastructure.fluids.FluidItemInventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * A fluid-bearing item (bucket, bottle, foreign tank item) seen by Create as a
 * {@link FluidItemInventory}.
 *
 * <p>Replaces the upstream {@code FluidItemInventoryWrapper} + {@code FluidItemContext} pair
 * (204 + 59 lines). On Fabric, reading a fluid out of an item needs a {@code ContainerItemContext}
 * to describe where the item lives, because the operation may have to replace the item with its
 * emptied form inside a transaction. NeoForge folds that into the capability itself:
 * {@link IFluidHandlerItem} owns a copy of the stack and exposes the updated one through
 * {@code getContainer()}, so there is no context to carry and nothing to close.
 *
 * <p>{@link #close()} is therefore a no-op, kept because Create's call sites use
 * try-with-resources on {@link FluidItemInventory}.
 */
public class FluidHandlerItemInventory extends FluidHandlerInventory implements FluidItemInventory {
    protected FluidHandlerItemInventory(IFluidHandlerItem handler) {
        super(handler);
    }

    public static FluidHandlerItemInventory of(IFluidHandlerItem handler) {
        return new FluidHandlerItemInventory(handler);
    }

    @Override
    public ItemStack getContainer() {
        return ((IFluidHandlerItem) handler).getContainer();
    }

    @Override
    public void close() {
        // Nothing to unwind: NeoForge has no transaction to commit or roll back.
    }
}
