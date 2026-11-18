package com.zurrtum.create.foundation.blockEntity.behaviour;

import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.infrastructure.transfer.ContainerItemHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * As {@link CachedInventoryBehaviour}, but for block entities whose inventory depends on the face
 * being asked about -- the factory itself takes the {@link Direction}. Index 6 of the array is the
 * null (side-agnostic) slot.
 */
public class CachedDirectionInventoryBehaviour<T extends SmartBlockEntity> extends BlockEntityBehaviour<T> {
    public static final BehaviourType<CachedDirectionInventoryBehaviour<?>> TYPE = new BehaviourType<>();
    private final BiFunction<T, @Nullable Direction, @Nullable Container> factory;
    @Nullable IItemHandler[] sides = new IItemHandler[7];

    public CachedDirectionInventoryBehaviour(T be, BiFunction<T, Direction, @Nullable Container> factory) {
        super(be);
        this.factory = factory;
    }

    public static @Nullable <T extends SmartBlockEntity> IItemHandler get(T be, @Nullable Direction side) {
        return be.getBehaviour(TYPE).get(side);
    }

    @Nullable
    public IItemHandler get(@Nullable Direction side) {
        int i = side == null ? 6 : side.get3DDataValue();
        IItemHandler sideHandler = sides[i];
        if (sideHandler == null) {
            Container inventory = factory.apply(blockEntity, side);
            if (inventory != null) {
                sideHandler = sides[i] = ContainerItemHandler.of(inventory, null);
            }
        }
        return sideHandler;
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }
}
