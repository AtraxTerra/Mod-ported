package com.zurrtum.create.infrastructure.transfer;

import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class FluidItemContext extends SingleStackStorage implements ContainerItemContext, AutoCloseable {
    private static final Deque<FluidItemContext> POOL = new ArrayDeque<>();
    private ItemStack stack;

    public static FluidItemContext of(ItemStack stack) {
        FluidItemContext ctx = POOL.pollFirst();
        if (ctx == null) {
            ctx = new FluidItemContext();
        }
        ctx.stack = stack;
        return ctx;
    }

    @Override
    public void close() {
        stack = ItemStack.EMPTY;
        POOL.addLast(this);
    }

    @Override
    protected ItemStack getStack() {
        return stack;
    }

    @Override
    protected void setStack(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public ResourceView<ItemResource> getMainSlot() {
        return this;
    }

    @Override
    public long insertOverflow(ItemResource itemVariant, long maxAmount, TransactionContext transactionContext) {
        return 0;
    }

    @Override
    public @UnmodifiableView List<ResourceView<ItemResource>> getAdditionalSlots() {
        return Collections.emptyList();
    }
}
