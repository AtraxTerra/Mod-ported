package com.zurrtum.create.foundation.blockEntity.behaviour;

import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;

public class CachedDirectionInventoryBehaviour<T extends SmartBlockEntity> extends BlockEntityBehaviour<T> {
    public static final BehaviourType<CachedDirectionInventoryBehaviour<?>> TYPE = new BehaviourType<>();
    private final BiFunction<T, @Nullable Direction, @Nullable Container> factory;
    @SuppressWarnings("unchecked")
    @Nullable ResourceHandler<ItemResource>[] sides = new ResourceHandler[7];

    public CachedDirectionInventoryBehaviour(T be, BiFunction<T, Direction, @Nullable Container> factory) {
        super(be);
        this.factory = factory;
    }

    public static @Nullable <T extends SmartBlockEntity> ResourceHandler<ItemResource> get(T be, @Nullable Direction side) {
        return be.getBehaviour(TYPE).get(side);
    }

    @Nullable
    public ResourceHandler<ItemResource> get(@Nullable Direction side) {
        int i = side == null ? 6 : side.get3DDataValue();
        ResourceHandler<ItemResource> sideStorage = sides[i];
        if (sideStorage == null) {
            Container inventory = factory.apply(blockEntity, side);
            if (inventory != null) {
                sideStorage = sides[i] = VanillaContainerWrapper.of(inventory);
            }
        }
        return sideStorage;
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }
}
