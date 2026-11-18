package com.zurrtum.create.infrastructure.transfer;

import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import com.zurrtum.create.platform.FluidUnits;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Presents a foreign NeoForge {@link IFluidHandler} to Create as a {@link FluidInventory}.
 *
 * <p>Replaces the upstream {@code FluidInventoryWrapper} (1244 lines) over a Fabric
 * {@code Storage<FluidVariant>}. As with the item side, most of the original was transaction
 * machinery that NeoForge's {@code FluidAction.SIMULATE} makes unnecessary.
 *
 * <p>{@link FluidInventory} declares only three abstract members -- {@code getStack},
 * {@code setStack} and {@code size} -- and builds its whole insert/extract/count API on defaults
 * over them. Those defaults assume a slot they may freely rewrite, which an {@code IFluidHandler}
 * tank is not, so {@link #insert} and {@link #extract} are overridden to go through
 * {@code fill}/{@code drain}.
 *
 * <h2>Units</h2>
 * Create counts droplets, NeoForge counts millibuckets. Every amount crossing this class goes
 * through {@link FluidUnits}; see that class for the rounding rule. Note in particular that
 * {@link #extract} removes the droplet equivalent of the millibuckets the tank actually gave up,
 * never the amount that was asked for.
 */
public class FluidHandlerInventory implements FluidInventory {
    protected final IFluidHandler handler;

    protected FluidHandlerInventory(IFluidHandler handler) {
        this.handler = handler;
    }

    public static FluidHandlerInventory of(IFluidHandler handler) {
        return new FluidHandlerInventory(handler);
    }

    public IFluidHandler getHandler() {
        return handler;
    }

    // --- FluidInventory core ------------------------------------------------

    @Override
    public int size() {
        return handler.getTanks();
    }

    @Override
    public FluidStack getStack(int slot) {
        if (slot < 0 || slot >= handler.getTanks()) {
            return FluidStack.EMPTY;
        }
        net.neoforged.neoforge.fluids.FluidStack present = handler.getFluidInTank(slot);
        return toCreate(present);
    }

    /**
     * {@code IFluidHandler} has no slot-wise write. This drains the tank and fills it with the new
     * contents, which is the closest honest approximation and can legitimately fail on a tank that
     * refuses the fluid. It never fabricates fluid.
     */
    @Override
    public void setStack(int slot, FluidStack stack) {
        if (slot < 0 || slot >= handler.getTanks()) {
            return;
        }
        net.neoforged.neoforge.fluids.FluidStack present = handler.getFluidInTank(slot);
        if (!present.isEmpty()) {
            handler.drain(present, IFluidHandler.FluidAction.EXECUTE);
        }
        if (!stack.isEmpty()) {
            handler.fill(toNeoForge(stack), IFluidHandler.FluidAction.EXECUTE);
        }
        markDirty();
    }

    @Override
    public int getMaxAmountPerStack() {
        int tanks = handler.getTanks();
        return tanks == 0 ? 0 : FluidUnits.toDroplets(handler.getTankCapacity(0));
    }

    @Override
    public int getMaxAmount(FluidStack stack) {
        return getMaxAmountPerStack();
    }

    // --- transfer, in droplets on the Create side ---------------------------

    @Override
    public int insert(FluidStack stack, int maxAmount) {
        return transferIn(stack, maxAmount, IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public int countSpace(FluidStack stack, int maxAmount) {
        return transferIn(stack, maxAmount, IFluidHandler.FluidAction.SIMULATE);
    }

    private int transferIn(FluidStack stack, int maxDroplets, IFluidHandler.FluidAction action) {
        if (stack.isEmpty() || maxDroplets <= 0) {
            return 0;
        }
        int offerMb = FluidUnits.toMillibuckets(Math.min(maxDroplets, stack.getAmount()));
        if (offerMb <= 0) {
            // Less than one millibucket: not representable to a NeoForge tank. Refusing is correct;
            // accepting would round the amount up and create fluid out of nothing.
            return 0;
        }
        int acceptedMb = handler.fill(toNeoForge(stack.copyWithAmount(FluidUnits.toDroplets(offerMb))), action);
        if (acceptedMb <= 0) {
            return 0;
        }
        if (action == IFluidHandler.FluidAction.EXECUTE) {
            markDirty();
        }
        return FluidUnits.toDroplets(acceptedMb);
    }

    @Override
    public int extract(FluidStack stack, int maxAmount) {
        return transferOut(stack, maxAmount, IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public int count(FluidStack stack, int maxAmount) {
        return transferOut(stack, maxAmount, IFluidHandler.FluidAction.SIMULATE);
    }

    private int transferOut(FluidStack stack, int maxDroplets, IFluidHandler.FluidAction action) {
        if (stack.isEmpty() || maxDroplets <= 0) {
            return 0;
        }
        int wantMb = FluidUnits.toMillibuckets(maxDroplets);
        if (wantMb <= 0) {
            return 0;
        }
        net.neoforged.neoforge.fluids.FluidStack request = toNeoForge(stack.copyWithAmount(FluidUnits.toDroplets(wantMb)));
        net.neoforged.neoforge.fluids.FluidStack drained = handler.drain(request, action);
        if (drained.isEmpty()) {
            return 0;
        }
        if (action == IFluidHandler.FluidAction.EXECUTE) {
            markDirty();
        }
        // Convert back what was actually given up, not what was requested.
        return FluidUnits.toDroplets(drained.getAmount());
    }

    // --- conversion ---------------------------------------------------------

    /**
     * NeoForge tank contents to a Create stack. Sub-millibucket precision does not exist on the
     * NeoForge side, so this is exact.
     */
    protected static FluidStack toCreate(net.neoforged.neoforge.fluids.FluidStack stack) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(stack.getFluid(), FluidUnits.toDroplets(stack.getAmount()));
    }

    /**
     * Create stack to a NeoForge stack, rounding the amount <b>down</b> to whole millibuckets.
     * <p>
     * TODO(port): data components are dropped here. Create's {@code FluidStack} carries a
     * {@code DataComponentPatch} and so does NeoForge's; the two use different component type
     * registries for fluid-specific components, so a faithful mapping needs a per-component table.
     * Plain fluids are unaffected; a potion or other component-bearing fluid loses its components
     * when it crosses this boundary. See PORTING.md, "known gaps".
     */
    protected static net.neoforged.neoforge.fluids.FluidStack toNeoForge(FluidStack stack) {
        if (stack.isEmpty()) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }
        return new net.neoforged.neoforge.fluids.FluidStack(
            stack.getFluid(),
            FluidUnits.toMillibuckets(stack.getAmount())
        );
    }
}
