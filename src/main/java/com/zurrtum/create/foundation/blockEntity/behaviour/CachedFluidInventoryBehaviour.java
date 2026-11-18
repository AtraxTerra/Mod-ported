package com.zurrtum.create.foundation.blockEntity.behaviour;

import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.SidedFluidInventory;
import com.zurrtum.create.infrastructure.transfer.FluidInventoryHandler;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * The fluid counterpart of {@link CachedInventoryBehaviour}: caches the {@link IFluidHandler} view
 * of a block entity's tank, per face when the inventory is a {@link SidedFluidInventory}.
 *
 * <p>Ported from the Fabric original, which cached a {@code Storage<FluidVariant>} built by
 * {@code FluidInventoryStorage.of}. Same caching shape, NeoForge handler type.
 */
public class CachedFluidInventoryBehaviour<T extends SmartBlockEntity> extends BlockEntityBehaviour<T> {
    public static final BehaviourType<CachedFluidInventoryBehaviour<?>> TYPE = new BehaviourType<>();
    private final Function<T, @Nullable FluidInventory> factory;
    private Function<@Nullable Direction, @Nullable IFluidHandler> getter;

    public CachedFluidInventoryBehaviour(T be, Function<T, @Nullable FluidInventory> factory) {
        super(be);
        this.factory = factory;
        reset();
    }

    public static @Nullable <T extends SmartBlockEntity> IFluidHandler get(T be, @Nullable Direction side) {
        return be.getBehaviour(TYPE).get(side);
    }

    @Nullable
    public IFluidHandler get(@Nullable Direction side) {
        return getter.apply(side);
    }

    @Nullable
    private IFluidHandler firstGet(@Nullable Direction direction) {
        FluidInventory inventory = factory.apply(blockEntity);
        if (inventory == null) {
            return null;
        }
        IFluidHandler handler = FluidInventoryHandler.of(inventory, null);
        if (inventory instanceof SidedFluidInventory) {
            @Nullable IFluidHandler[] sides = new IFluidHandler[6];
            getter = side -> {
                if (side == null) {
                    return handler;
                }
                int i = side.get3DDataValue();
                IFluidHandler sideHandler = sides[i];
                if (sideHandler == null) {
                    sideHandler = sides[i] = FluidInventoryHandler.of(inventory, side);
                }
                return sideHandler;
            };
        } else {
            getter = _ -> handler;
        }
        return getter.apply(direction);
    }

    public void reset() {
        getter = this::firstGet;
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }
}
