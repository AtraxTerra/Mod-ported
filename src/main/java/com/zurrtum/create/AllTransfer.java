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
import com.zurrtum.create.infrastructure.transfer.ContainerItemHandler;
import com.zurrtum.create.infrastructure.transfer.FluidHandlerInventory;
import com.zurrtum.create.infrastructure.transfer.FluidHandlerItemInventory;
import com.zurrtum.create.infrastructure.transfer.FluidInventoryHandler;
import com.zurrtum.create.infrastructure.transfer.ItemHandlerContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The single bridge between Create's item/fluid logistics and the rest of the mod ecosystem.
 *
 * <p>Upstream this file talks to the Fabric Transfer API. Here it talks to NeoForge capabilities.
 * The <b>public method signatures are unchanged</b>, which is what keeps the several hundred call
 * sites across the mod compiling untouched -- this file and the {@code infrastructure.transfer}
 * adapters are the whole of the loader-specific transfer code.
 *
 * <h2>Two-phase registration</h2>
 * Upstream, {@code register()} is called from {@code Create.register()}, which runs from
 * {@code BuiltInRegistriesMixin} during vanilla registry bootstrap -- far earlier than NeoForge
 * fires {@link RegisterCapabilitiesEvent}. Rather than move the call (and change Create's
 * initialisation order, which other registrations depend on), {@code register()} keeps its original
 * position and only <i>records</i> what should be exposed. {@link #registerCapabilities} then
 * replays those records when NeoForge is ready.
 *
 * <p>The {@code DISABLE} flag is kept, and is now always {@code false}: on Fabric it reported
 * whether {@code fabric-transfer-api-v1} was present, and capabilities are part of NeoForge itself.
 * It stays so that call sites guarding on it still compile, and so that flipping it to {@code true}
 * remains a one-line way to bisect an interop bug by cutting Create off from foreign inventories.
 */
public class AllTransfer {
    public static final boolean DISABLE = false;

    private static final Map<IItemHandler, Container> WRAPPERS_ITEM = new MapMaker().weakValues().makeMap();
    private static final Map<IFluidHandler, FluidInventory> WRAPPERS_FLUID = new MapMaker().weakValues().makeMap();

    /**
     * Recorded by {@link #register()}, replayed by {@link #registerCapabilities}. Each entry is a
     * closure over its own block-entity type and accessor, which keeps the generics honest and lets
     * the two item variants (side-agnostic and per-face) dispatch to their own behaviour type.
     */
    private static final List<Consumer<RegisterCapabilitiesEvent>> PENDING = new ArrayList<>();

    // --- lookups -----------------------------------------------------------

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
        BlockCapabilityCache<IItemHandler, @Nullable Direction> cache = BlockCapabilityCache.create(
            Capabilities.ItemHandler.BLOCK,
            world,
            pos,
            direction
        );
        return () -> {
            IItemHandler handler = cache.getCapability();
            if (handler == null) {
                return null;
            }
            if (filter != null && !filter.test(world.getBlockEntity(pos), direction)) {
                return null;
            }
            return WRAPPERS_ITEM.computeIfAbsent(handler, ItemHandlerContainer::of);
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
        IItemHandler handler = world.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, direction);
        if (handler == null) {
            return null;
        }
        return WRAPPERS_ITEM.computeIfAbsent(handler, ItemHandlerContainer::of);
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
        return world.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, blockEntity, direction) != null;
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
        BlockCapabilityCache<IFluidHandler, @Nullable Direction> cache = BlockCapabilityCache.create(
            Capabilities.FluidHandler.BLOCK,
            world,
            pos,
            direction
        );
        return () -> {
            IFluidHandler handler = cache.getCapability();
            if (handler == null) {
                return null;
            }
            return WRAPPERS_FLUID.computeIfAbsent(handler, FluidHandlerInventory::of);
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
        IFluidHandler handler = world.getCapability(
            Capabilities.FluidHandler.BLOCK,
            pos,
            state,
            blockEntity,
            direction
        );
        if (handler == null) {
            return null;
        }
        return WRAPPERS_FLUID.computeIfAbsent(handler, FluidHandlerInventory::of);
    }

    public static boolean hasFluidInventory(ItemStack stack) {
        if (DISABLE) {
            return false;
        }
        return stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
    }

    /**
     * Note the lifecycle difference from upstream: on Fabric the returned inventory held a
     * {@code ContainerItemContext} that had to be closed to commit the change to the item. Here the
     * capability owns its own copy of the stack and the caller reads the result back with
     * {@code getContainer()}; {@code close()} is a no-op. Call sites that used try-with-resources
     * still work, but a call site that <i>relied</i> on close() writing the item back needs checking
     * -- see PORTING.md.
     */
    @Nullable
    public static FluidItemInventory getFluidInventory(ItemStack stack) {
        if (DISABLE) {
            return null;
        }
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return null;
        }
        return FluidHandlerItemInventory.of(handler);
    }

    // --- registration ------------------------------------------------------

    private static <T extends SmartBlockEntity> void registerItemSide(
        BlockEntityType<T> type,
        Function<T, @Nullable Container> factory
    ) {
        BlockEntityBehaviour.add(type, (T be) -> new CachedInventoryBehaviour<>(be, factory));
        PENDING.add(event -> event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
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
            Capabilities.ItemHandler.BLOCK,
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
            Capabilities.FluidHandler.BLOCK,
            type,
            CachedFluidInventoryBehaviour::get
        ));
    }

    /**
     * Replays everything {@link #register()} recorded. Wired to {@link RegisterCapabilitiesEvent}
     * on the mod event bus in {@code Create}'s constructor.
     *
     * <p>The behaviours added by {@code register()} are what actually produce the handler for a
     * given block entity and side; this only tells NeoForge to ask them. Those behaviours return the
     * handler directly, so there is no wrapping to do here.
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
