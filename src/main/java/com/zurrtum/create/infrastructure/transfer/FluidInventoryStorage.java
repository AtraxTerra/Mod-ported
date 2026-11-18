package com.zurrtum.create.infrastructure.transfer;

import com.google.common.collect.MapMaker;
import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.UnmodifiableView;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public interface FluidInventoryStorage extends ResourceHandler<FluidResource> {
    Map<FluidResource, FluidStack> CACHE = new MapMaker().weakKeys().makeMap();

    static FluidStack getCachedStack(FluidResource variant) {
        return CACHE.computeIfAbsent(variant, FluidInventoryStorage::toStack);
    }

    static FluidStack toStack(FluidResource variant) {
        return new FluidStack(variant.getFluid(), 1, variant.getComponentsPatch());
    }

    static FluidInventoryStorage of(FluidInventory inventory) {
        Objects.requireNonNull(inventory, "Null fluid inventory is not supported.");
        return FluidInventoryStorageImpl.of(inventory);
    }

    static FluidInventoryStorage of(FluidInventory inventory, @Nullable Direction direction) {
        Objects.requireNonNull(inventory, "Null fluid inventory is not supported.");
        return FluidInventoryStorageImpl.of(inventory, direction);
    }

    static boolean matches(FluidResource variant, FluidStack stack) {
        if (variant.getFluid() != stack.getFluid()) {
            return false;
        }
        PatchedDataComponentMap stackComponents = stack.directComponents();
        DataComponentMap otherStackComponents = variant.getComponents();
        if (stackComponents == otherStackComponents) {
            return true;
        }
        if (otherStackComponents == DataComponentMap.EMPTY) {
            Reference2ObjectMap<DataComponentType<?>, Optional<?>> stackComponentMap = stackComponents.patch;
            int stackComponentCount = stackComponentMap.size();
            if (stackComponentCount == 0) {
                return true;
            }
            return stackComponentCount == 1 && stackComponentMap.containsKey(AllDataComponents.FLUID_MAX_CAPACITY);
        }
        return FluidStack.areFluidsAndComponentsEqualIgnoreCapacity(
            stackComponents,
            (PatchedDataComponentMap) otherStackComponents
        );
    }

    /**
     * The one-slot handlers this storage is built from, one per inventory slot.
     *
     * <p>Upstream models this as Fabric's {@code SlottedStorage}: a storage that hands out a view
     * per slot, through {@code getSlots}/{@code getSlotCount}/{@code getSlot}. NeoForge has no such
     * contract -- a {@link ResourceHandler} is simply indexed, with {@code size()} and index-taking
     * operations -- so the list stays, as Create's own concept, and the handler methods below
     * delegate to it. Each part has exactly one index, so the mapping is one-to-one and index 0 is
     * always the right one to forward.
     */
    @UnmodifiableView
    List<? extends ResourceHandler<FluidResource>> getParts();

    @Override
    default int size() {
        return getParts().size();
    }

    @Override
    default FluidResource getResource(int index) {
        return getParts().get(index).getResource(0);
    }

    @Override
    default long getAmountAsLong(int index) {
        return getParts().get(index).getAmountAsLong(0);
    }

    @Override
    default long getCapacityAsLong(int index, FluidResource resource) {
        return getParts().get(index).getCapacityAsLong(0, resource);
    }

    @Override
    default boolean isValid(int index, FluidResource resource) {
        return getParts().get(index).isValid(0, resource);
    }

    @Override
    default int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return getParts().get(index).insert(0, resource, amount, transaction);
    }

    @Override
    default int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return getParts().get(index).extract(0, resource, amount, transaction);
    }

    @SuppressWarnings("UnstableApiUsage")
    static String toString(@Nullable FluidInventory inventory) {
        if (inventory == null) {
            return "~~NULL~~";
        }
        String result = inventory.toString();

        if (inventory instanceof BlockEntity blockEntity) {
            // Upstream formats the position through Fabric's internal DebugMessages helper, which
            // has no NeoForge counterpart. This is a debug string, so the position is printed
            // directly instead.
            result += " (%s, %s)".formatted(blockEntity.getBlockState(), blockEntity.getBlockPos());
        }

        return result;
    }
}
