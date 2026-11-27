// Adapted from chaevsfe / Create Fly: Steam 'n' Rails (https://github.com/chaevsfe/Create-Steam-n-Rails-Fly)
// Based on original work from Create: Steam 'n' Rails by The Railways Team (https://github.com/Layers-of-Railways/Railway)
// Licensed under LGPL-3.0-only — extracted/adapted with AI assistance.
package com.railwayteam.railways.neoforge;

import com.railwayteam.railways.Railways;
import com.railwayteam.railways.content.fuel.LiquidFuelManager;
import com.railwayteam.railways.content.palettes.PalettesColor;
import com.railwayteam.railways.events.CommonEvents;
import com.railwayteam.railways.multiloader.CommandRegistrar;
import com.railwayteam.railways.multiloader.fabric.PacketSetImpl;
import com.railwayteam.railways.registry.fabric.CRFluidsImpl;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The NeoForge side of what upstream does with Fabric API on both physical sides: its server
 * events ({@code CommonEventsFabric}), its command, payload, capability and attribute registries, and
 * paint's fluid type.
 *
 * <p>Steam 'n' Rails initialises while Create Fly registers its addons (vanilla registry bootstrap),
 * before FML constructs mods and long before NeoForge fires its registration events. What upstream
 * registers on the spot is therefore queued in the lists below, from the code that registered it
 * upstream, and handed over when the matching event fires.
 */
@EventBusSubscriber(modid = Railways.MOD_ID)
public final class RailwaysNeoForgeEvents {
    /** Command trees, from {@code RailwaysImpl.registerCommands}; replayed on every command reload. */
    public static final List<CommandRegistrar> COMMANDS = new CopyOnWriteArrayList<>();
    /** Block entity and item capabilities, from the block entity and pitcher registrations. */
    public static final List<Consumer<RegisterCapabilitiesEvent>> CAPABILITIES = new CopyOnWriteArrayList<>();
    /** Default attributes, from the registrate shim's entity builder. */
    public static final Map<EntityType<? extends LivingEntity>, Supplier<AttributeSupplier.Builder>> ATTRIBUTES = new ConcurrentHashMap<>();

    private static @Nullable FluidType paintType;

    private RailwaysNeoForgeEvents() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PacketSetImpl.registerPayloads(event);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        boolean dedicated = event.getCommandSelection().includeDedicated;
        for (CommandRegistrar registrar : COMMANDS)
            registrar.register(event.getDispatcher(), dedicated, event.getBuildContext());
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (Consumer<RegisterCapabilitiesEvent> capability : CAPABILITIES)
            capability.accept(event);
    }

    @SubscribeEvent
    public static void createAttributes(EntityAttributeCreationEvent event) {
        ATTRIBUTES.forEach((type, attributes) -> event.put(type, attributes.get().build()));
    }

    // Fabric's ServerTickEvents.START_LEVEL_TICK.
    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Pre event) {
        if (!event.getLevel().isClientSide())
            CommonEvents.onWorldTickStart(event.getLevel());
    }

    // Fabric's ServerPlayConnectionEvents.JOIN.
    @SubscribeEvent
    public static void playerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            CommonEvents.onPlayerJoin(player);
    }

    // Fabric's ServerLifecycleEvents.END_DATA_PACK_RELOAD. NeoForge also fires this for the first
    // load of a world's data, which refreshes the same tag caches once more.
    @SubscribeEvent
    public static void tagsUpdated(TagsUpdatedEvent.ServerDataLoad event) {
        CommonEvents.onTagsUpdated();
    }

    @SubscribeEvent
    public static void serverReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Railways.asResource(LiquidFuelManager.ReloadListener.ID), LiquidFuelManager.ReloadListener.INSTANCE);
    }

    @SubscribeEvent
    public static void registerFluidTypes(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.FLUID_TYPES, helper -> {
            paintType = new PaintFluidType();
            helper.register(Railways.asResource("paint"), paintType);
        });
    }

    /** Paint's fluid type; water's until NeoForge has registered fluid types (nothing asks before). */
    public static FluidType paintFluidType() {
        FluidType type = paintType;
        return type != null ? type : NeoForgeMod.WATER_TYPE.value();
    }

    /** Paint, named by its colour as upstream's Fabric {@code FluidVariantAttributeHandler} does. */
    private static final class PaintFluidType extends FluidType {
        private PaintFluidType() {
            super(FluidType.Properties.create().descriptionId("fluid.railways.paint"));
        }

        @Override
        public Component getDescription(FluidStack stack) {
            return Component.translatable(CRFluidsImpl.getPaintColor(stack)
                .map(PalettesColor::getPaintNameId)
                .orElse("fluid.railways.paint"));
        }
    }
}
