package com.zurrtum.create;

import com.google.common.collect.MapMaker;
import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.content.kinetics.belt.BeltBlock;
import com.zurrtum.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.foundation.blockEntity.behaviour.CachedDirectionInventoryBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.CachedFluidInventoryBehaviour;
import com.zurrtum.create.foundation.blockEntity.behaviour.CachedInventoryBehaviour;
import com.zurrtum.create.foundation.item.ItemHelper;
import com.zurrtum.create.infrastructure.fluids.FluidInventory;
import com.zurrtum.create.infrastructure.fluids.FluidItemInventory;
import com.zurrtum.create.infrastructure.transfer.FluidInventoryWrapper;
import com.zurrtum.create.infrastructure.transfer.FluidItemInventoryWrapper;
import com.zurrtum.create.infrastructure.transfer.InventoryWrapper;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class AllTransfer {
    /**
     * Upstream this reports whether fabric-transfer-api-v1 is present. Capabilities are part of
     * NeoForge itself, so it is always false. Kept so the call sites that guard on it still compile,
     * and because flipping it to true remains a one-line way to cut Create off from every foreign
     * inventory when bisecting an interop bug.
     */
    public static final boolean DISABLE = false;

    /**
     * Recorded by {@link #register()}, replayed by {@link #registerCapabilities}. Each entry closes
     * over its own block-entity type and accessor, which keeps the generics honest and lets the two
     * item variants dispatch to their own behaviour type.
     */
    private static final List<Consumer<RegisterCapabilitiesEvent>> PENDING = new ArrayList<>();
    private static final Map<ResourceHandler<ItemResource>, Container> WRAPPERS_ITEM = new MapMaker().weakValues().makeMap();
    private static final Map<ResourceHandler<FluidResource>, FluidInventory> WRAPPERS_FLUID = new MapMaker().weakValues()
        .makeMap();

    @Nullable
    public static Supplier<@Nullable Container> getCacheInventory(
        ServerLevel world,
        BlockPos pos,
        Direction direction,
        @Nullable BiPredicate<@Nullable BlockEntity, Direction> filter
    ) {
        if (DISABLE) {
            return null;
        }
        BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> cache = BlockCapabilityCache.create(
            Capabilities.Item.BLOCK,
            world,
            pos,
            direction
        );
        return () -> {
            // NeoForge's cache bakes the context in, so getCapability() takes no side; and it does
            // not expose the block entity the way Fabric's BlockApiCache did, hence the lookup.
            ResourceHandler<ItemResource> inventory = cache.getCapability();
            if (inventory == null || filter != null && !filter.test(world.getBlockEntity(pos), direction)) {
                return null;
            }
            return WRAPPERS_ITEM.computeIfAbsent(inventory, InventoryWrapper::of);
        };
    }

    @Nullable
    public static Container getInventory(
        Level world,
        BlockPos pos,
        @Nullable BlockState state,
        @Nullable BlockEntity blockEntity,
        @Nullable Direction direction
    ) {
        if (DISABLE) {
            return null;
        }
        ResourceHandler<ItemResource> inventory = world.getCapability(
            Capabilities.Item.BLOCK,
            pos,
            state,
            blockEntity,
            direction
        );
        if (inventory == null) {
            return null;
        }
        return WRAPPERS_ITEM.computeIfAbsent(inventory, InventoryWrapper::of);
    }

    public static boolean hasFluidInventory(
        Level world,
        BlockPos pos,
        @Nullable BlockState state,
        @Nullable BlockEntity blockEntity,
        @Nullable Direction direction
    ) {
        if (DISABLE) {
            return false;
        }
        return world.getCapability(Capabilities.Fluid.BLOCK, pos, state, blockEntity, direction) != null;
    }

    @Nullable
    public static Supplier<@Nullable FluidInventory> getCacheFluidInventory(
        ServerLevel world,
        BlockPos pos,
        Direction direction
    ) {
        if (DISABLE) {
            return null;
        }
        BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> cache = BlockCapabilityCache.create(
            Capabilities.Fluid.BLOCK,
            world,
            pos,
            direction
        );
        return () -> {
            ResourceHandler<FluidResource> inventory = cache.getCapability();
            if (inventory == null) {
                return null;
            }
            return WRAPPERS_FLUID.computeIfAbsent(inventory, FluidInventoryWrapper::of);
        };
    }

    @Nullable
    public static FluidInventory getFluidInventory(
        Level world,
        BlockPos pos,
        @Nullable BlockState state,
        @Nullable BlockEntity blockEntity,
        @Nullable Direction direction
    ) {
        if (DISABLE) {
            return null;
        }
        ResourceHandler<FluidResource> inventory = world.getCapability(
            Capabilities.Fluid.BLOCK,
            pos,
            state,
            blockEntity,
            direction
        );
        if (inventory == null) {
            return null;
        }
        return WRAPPERS_FLUID.computeIfAbsent(inventory, FluidInventoryWrapper::of);
    }

    public static boolean hasFluidInventory(ItemStack stack) {
        if (DISABLE) {
            return false;
        }
        // An ItemAccess replaces Fabric's ContainerItemContext: it describes the stack being
        // operated on and carries the updated one back. Nothing to close. The capability is
        // queried through the access, which is the route NeoForge's own FluidUtil documents.
        return ItemAccess.forStack(stack).getCapability(Capabilities.Fluid.ITEM) != null;
    }

    @Nullable
    public static FluidItemInventory getFluidInventory(ItemStack stack) {
        if (DISABLE) {
            return null;
        }
        ItemAccess access = ItemAccess.forStack(stack);
        ResourceHandler<FluidResource> inventory = access.getCapability(Capabilities.Fluid.ITEM);
        if (inventory == null) {
            return null;
        }
        return FluidItemInventoryWrapper.of(inventory, access);
    }

    private static <T extends SmartBlockEntity> void registerItemSide(
        BlockEntityType<T> type,
        Function<T, @Nullable Container> factory
    ) {
        BlockEntityBehaviour.add(type, (T be) -> new CachedInventoryBehaviour<>(be, factory));
        PENDING.add(event -> event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            type,
            CachedInventoryBehaviour::get
        ));
    }

    private static <T extends SmartBlockEntity> void registerItemSide(
        BlockEntityType<T> type,
        BiFunction<T, @Nullable Direction, @Nullable Container> factory
    ) {
        BlockEntityBehaviour.add(type, (T be) -> new CachedDirectionInventoryBehaviour<>(be, factory));
        PENDING.add(event -> event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            type,
            CachedDirectionInventoryBehaviour::get
        ));
    }

    private static <T extends SmartBlockEntity> void registerFluidSide(
        BlockEntityType<T> type,
        Function<T, @Nullable FluidInventory> factory
    ) {
        BlockEntityBehaviour.add(type, (T be) -> new CachedFluidInventoryBehaviour<>(be, factory));
        PENDING.add(event -> event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            type,
            CachedFluidInventoryBehaviour::get
        ));
    }

    /**
     * Replays everything {@link #register()} recorded. Wired to {@link RegisterCapabilitiesEvent}
     * on the mod event bus in {@code Create}'s constructor.
     *
     * <p>Two phases are needed because {@code register()} runs from {@code BuiltInRegistriesMixin}
     * during vanilla registry bootstrap, far earlier than NeoForge fires the capability event.
     * Moving the call instead would change Create's initialisation order, which other registrations
     * depend on.
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (DISABLE) {
            return;
        }
        for (Consumer<RegisterCapabilitiesEvent> pending : PENDING) {
            pending.accept(event);
        }
    }

    public static void register() {
        if (DISABLE) {
            return;
        }
        PENDING.clear();
        registerItemSide(AllBlockEntityTypes.DEPOT, be -> be.depotBehaviour.itemHandler);
        registerItemSide(AllBlockEntityTypes.WEIGHTED_EJECTOR, be -> be.depotBehaviour.itemHandler);
        registerItemSide(
            AllBlockEntityTypes.BELT, be -> {
                if (!BeltBlock.canTransportObjects(be.getBlockState())) {
                    return null;
                }
                if (!be.isRemoved() && be.itemHandler == null) {
                    be.initializeItemHandler();
                }
                return be.itemHandler;
            }
        );
        registerItemSide(AllBlockEntityTypes.MILLSTONE, be -> be.capability);
        registerItemSide(AllBlockEntityTypes.SAW, be -> be.inventory);
        registerItemSide(AllBlockEntityTypes.BASIN, be -> be.itemCapability);
        registerItemSide(
            AllBlockEntityTypes.ANDESITE_TUNNEL, be -> {
                if (be.cap == null) {
                    Level world = be.getLevel();
                    BlockPos pos = be.getBlockPos();
                    BlockState state = world.getBlockState(pos.below());
                    if (state.is(AllBlocks.BELT)) {
                        BlockEntity beBelow = world.getBlockEntity(pos.below());
                        if (beBelow != null) {
                            Container capBelow = ItemHelper.getInventory(
                                world,
                                pos.below(),
                                state,
                                beBelow,
                                Direction.UP
                            );
                            if (capBelow != null) {
                                be.cap = capBelow;
                            }
                        }
                    }
                }
                return be.cap;
            }
        );
        registerItemSide(AllBlockEntityTypes.BRASS_TUNNEL, be -> be.tunnelCapability);
        registerItemSide(AllBlockEntityTypes.CHUTE, be -> be.itemHandler);
        registerItemSide(AllBlockEntityTypes.SMART_CHUTE, be -> be.itemHandler);
        registerItemSide(AllBlockEntityTypes.PORTABLE_STORAGE_INTERFACE, be -> be.capability);
        registerItemSide(
            AllBlockEntityTypes.ITEM_DRAIN, (be, context) -> {
                if (context != null && context.getAxis().isHorizontal()) {
                    return be.itemHandlers.get(context);
                }
                return null;
            }
        );
        registerItemSide(
            AllBlockEntityTypes.DEPLOYER, be -> {
                if (be.invHandler == null) {
                    be.initHandler();
                }
                return be.invHandler;
            }
        );
        registerItemSide(AllBlockEntityTypes.CRUSHING_WHEEL_CONTROLLER, be -> be.inventory);
        registerItemSide(AllBlockEntityTypes.MECHANICAL_CRAFTER, MechanicalCrafterBlockEntity::getInvCapability);
        registerItemSide(AllBlockEntityTypes.CREATIVE_CRATE, be -> be.inv);
        registerItemSide(AllBlockEntityTypes.PACKAGER, be -> be.inventory);
        registerItemSide(AllBlockEntityTypes.REPACKAGER, be -> be.inventory);
        registerItemSide(AllBlockEntityTypes.PACKAGE_POSTBOX, be -> be.inventory);
        registerItemSide(AllBlockEntityTypes.PACKAGE_FROGPORT, be -> be.inventory);
        registerItemSide(AllBlockEntityTypes.TOOLBOX, be -> be.inventory);
        registerItemSide(AllBlockEntityTypes.TRACK_STATION, be -> be.depotBehaviour.itemHandler);
        registerFluidSide(
            AllBlockEntityTypes.FLUID_TANK, be -> {
                if (be.fluidCapability == null) {
                    be.refreshCapability();
                }
                return be.fluidCapability;
            }
        );
        registerFluidSide(AllBlockEntityTypes.BASIN, be -> be.fluidCapability);
        registerFluidSide(AllBlockEntityTypes.PORTABLE_FLUID_INTERFACE, be -> be.capability);
        registerFluidSide(AllBlockEntityTypes.HOSE_PULLEY, be -> be.handler);
        registerFluidSide(AllBlockEntityTypes.SPOUT, be -> be.tank.getCapability());
    }
}
