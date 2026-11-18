package com.zurrtum.create.infrastructure.transfer;

import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.platform.FluidUnits;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jspecify.annotations.Nullable;

/**
 * Exposes one of Create's {@link FluidInventory}s to other mods as an {@link IFluidHandler}.
 * The outbound half of the fluid bridge; {@link FluidHandlerInventory} is the inbound half.
 *
 * <p>All amounts crossing out of Create are rounded <b>down</b> to whole millibuckets by
 * {@link FluidUnits}, so a tank holding 1040 droplets advertises 12 mB and keeps the 68-droplet
 * remainder invisible to the outside world. That is deliberate: rounding up would let a neighbour
 * drain fluid that does not exist.
 */
public class FluidInventoryHandler implements IFluidHandler {
    protected final FluidInventory inventory;
    protected final @Nullable Direction side;

    protected FluidInventoryHandler(FluidInventory inventory, @Nullable Direction side) {
        this.inventory = inventory;
        this.side = side;
    }

    public static FluidInventoryHandler of(FluidInventory inventory) {
        return new FluidInventoryHandler(inventory, null);
    }

    public static FluidInventoryHandler of(FluidInventory inventory, @Nullable Direction side) {
        return new FluidInventoryHandler(inventory, side);
    }

    public FluidInventory getInventory() {
        return inventory;
    }

    @Override
    public int getTanks() {
        return inventory.size();
    }

    @Override
    public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) {
        if (tank < 0 || tank >= inventory.size()) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }
        return FluidHandlerInventory.toNeoForge(inventory.getStack(tank));
    }

    @Override
    public int getTankCapacity(int tank) {
        return FluidUnits.toMillibuckets(inventory.getMaxAmountPerStack());
    }

    @Override
    public boolean isFluidValid(int tank, net.neoforged.neoforge.fluids.FluidStack stack) {
        if (tank < 0 || tank >= inventory.size() || stack.isEmpty()) {
            return false;
        }
        // One millibucket is the smallest unit the caller can express; if the inventory has room for
        // that much of this fluid, the fluid is valid for this tank.
        return inventory.countSpace(FluidHandlerInventory.toCreate(stack), FluidUnits.DROPLETS_PER_MILLIBUCKET, side) > 0;
    }

    @Override
    public int fill(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        com.zurrtum.create.infrastructure.fluids.FluidStack offer = FluidHandlerInventory.toCreate(resource);
        int droplets = offer.getAmount();
        int accepted = action.execute()
            ? inventory.insert(offer, droplets, side)
            : inventory.countSpace(offer, droplets, side);
        // Report only whole millibuckets, so the caller is never told it moved more than it did.
        return FluidUnits.toMillibuckets(accepted);
    }

    @Override
    public net.neoforged.neoforge.fluids.FluidStack drain(
        net.neoforged.neoforge.fluids.FluidStack resource,
        FluidAction action
    ) {
        if (resource.isEmpty()) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }
        com.zurrtum.create.infrastructure.fluids.FluidStack request = FluidHandlerInventory.toCreate(resource);
        int droplets = request.getAmount();
        int taken = action.execute()
            ? inventory.extract(request, droplets, side)
            : inventory.count(request, droplets, side);
        int takenMb = FluidUnits.toMillibuckets(taken);
        if (takenMb <= 0) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }
        return resource.copyWithAmount(takenMb);
    }

    /**
     * Drain-by-amount, fluid unspecified: takes from the first non-empty tank, matching the
     * behaviour other mods expect of a multi-tank handler.
     */
    @Override
    public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }
        for (int tank = 0, tanks = inventory.size(); tank < tanks; tank++) {
            com.zurrtum.create.infrastructure.fluids.FluidStack present = inventory.getStack(tank);
            if (present.isEmpty()) {
                continue;
            }
            net.neoforged.neoforge.fluids.FluidStack request =
                FluidHandlerInventory.toNeoForge(present).copyWithAmount(maxDrain);
            net.neoforged.neoforge.fluids.FluidStack drained = drain(request, action);
            if (!drained.isEmpty()) {
                return drained;
            }
        }
        return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    }
}
