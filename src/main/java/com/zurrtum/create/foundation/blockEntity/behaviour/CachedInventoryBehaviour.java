package com.zurrtum.create.foundation.blockEntity.behaviour;

import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.infrastructure.transfer.ContainerItemHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * Caches the {@link IItemHandler} view of a block entity's inventory, one per face.
 *
 * <p>Ported from the Fabric original, which cached a {@code Storage<ItemVariant>} built by
 * {@code ContainerStorage.of}. The caching shape is unchanged -- build on first use, keep a
 * per-face array for a {@link WorldlyContainer}, a single instance otherwise -- only the handler
 * type differs. Keeping the cache matters: {@code getCapability} is called every tick by every
 * neighbouring machine, and rebuilding the wrapper each time would allocate heavily.
 */
public class CachedInventoryBehaviour<T extends SmartBlockEntity> extends BlockEntityBehaviour<T> {
    public static final BehaviourType<CachedInventoryBehaviour<?>> TYPE = new BehaviourType<>();
    private final Function<T, @Nullable Container> factory;
    private Function<@Nullable Direction, @Nullable IItemHandler> getter;

    public CachedInventoryBehaviour(T be, Function<T, @Nullable Container> factory) {
        super(be);
        this.factory = factory;
        reset();
    }

    public static @Nullable <T extends SmartBlockEntity> IItemHandler get(T be, @Nullable Direction side) {
        return be.getBehaviour(TYPE).get(side);
    }

    @Nullable
    public IItemHandler get(@Nullable Direction side) {
        return getter.apply(side);
    }

    @Nullable
    private IItemHandler firstGet(@Nullable Direction direction) {
        Container inventory = factory.apply(blockEntity);
        if (inventory == null) {
            return null;
        }
        IItemHandler handler = ContainerItemHandler.of(inventory, null);
        if (inventory instanceof WorldlyContainer) {
            @Nullable IItemHandler[] sides = new IItemHandler[6];
            getter = side -> {
                if (side == null) {
                    return handler;
                }
                int i = side.get3DDataValue();
                IItemHandler sideHandler = sides[i];
                if (sideHandler == null) {
                    sideHandler = sides[i] = ContainerItemHandler.of(inventory, side);
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
