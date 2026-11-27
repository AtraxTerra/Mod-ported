// Adapted from chaevsfe / Create Fly: Steam 'n' Rails (https://github.com/chaevsfe/Create-Steam-n-Rails-Fly)
// Based on original work from Create: Steam 'n' Rails by The Railways Team (https://github.com/Layers-of-Railways/Railway)
// Licensed under LGPL-3.0-only — extracted/adapted with AI assistance.
package com.railwayteam.railways.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.railwayteam.railways.Railways;
import com.railwayteam.railways.base.reload.ClientResourceReloadListener;
import com.railwayteam.railways.content.conductor.fabric.ConductorCapItemRenderer;
import com.railwayteam.railways.events.ClientEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The client half of what upstream does with Fabric API: its client events
 * ({@code RailwaysClientImpl.registerClientEvents}) and the client registries Fabric API fills on the
 * spot. Steam 'n' Rails starts its client side inside {@code Minecraft}'s constructor, before NeoForge
 * fires any of the registration events below; each registry became a queue, filled from the code that
 * registered upstream and handed over when its event fires.
 */
@EventBusSubscriber(modid = Railways.MOD_ID, value = Dist.CLIENT)
public final class RailwaysNeoForgeClientEvents {
    public static final List<KeyMapping> KEYS = new CopyOnWriteArrayList<>();
    public static final List<Consumer<RegisterParticleProvidersEvent>> PARTICLES = new CopyOnWriteArrayList<>();
    public static final Map<ModelLayerLocation, Supplier<LayerDefinition>> LAYERS = new LinkedHashMap<>();
    public static final List<Consumer<EntityRenderersEvent.RegisterRenderers>> ENTITY_RENDERERS = new CopyOnWriteArrayList<>();
    public static final List<Consumer<CommandDispatcher<SharedSuggestionProvider>>> CLIENT_COMMANDS = new CopyOnWriteArrayList<>();
    public static final List<Consumer<RegisterMenuScreensEvent>> MENU_SCREENS = new CopyOnWriteArrayList<>();
    public static final List<Consumer<RegisterFluidModelsEvent>> FLUID_MODELS = new CopyOnWriteArrayList<>();
    /** Built-in resource packs: folder under resourcepacks/ -> title. */
    public static final Map<String, String> PACKS = new LinkedHashMap<>();

    private RailwaysNeoForgeClientEvents() {
    }

    // --- Fabric API client events --------------------------------------------------------------

    @SubscribeEvent
    public static void clientTickStart(ClientTickEvent.Pre event) {
        ClientEvents.onClientTickStart(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void clientTickEnd(ClientTickEvent.Post event) {
        ClientEvents.onClientTickEnd(Minecraft.getInstance());
    }

    // Fabric's ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.
    @SubscribeEvent
    public static void clientLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level)
            ClientEvents.onClientWorldLoad(level);
    }

    // Fabric's ClientPlayConnectionEvents.DISCONNECT.
    @SubscribeEvent
    public static void clientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientEvents.onClientDisconnect();
    }

    // Upstream refreshes its client tag caches from a server-data reload listener registered on the
    // client; NeoForge tells the client directly whenever tags arrive.
    @SubscribeEvent
    public static void clientTagsUpdated(TagsUpdatedEvent.ClientPacketReceived event) {
        ClientEvents.onTagsUpdated();
    }

    @SubscribeEvent
    public static void clientReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Railways.asResource("client_resource_reload"), ClientResourceReloadListener.INSTANCE);
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void clientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<SharedSuggestionProvider> dispatcher = (CommandDispatcher) event.getDispatcher();
        for (Consumer<CommandDispatcher<SharedSuggestionProvider>> commands : CLIENT_COMMANDS)
            commands.accept(dispatcher);
    }

    // --- Fabric API client registries ----------------------------------------------------------

    @SubscribeEvent
    public static void keyMappings(RegisterKeyMappingsEvent event) {
        // The category itself is registered by vanilla's KeyMapping.Category.register (CRKeys).
        KEYS.forEach(event::register);
    }

    @SubscribeEvent
    public static void particleProviders(RegisterParticleProvidersEvent event) {
        PARTICLES.forEach(provider -> provider.accept(event));
    }

    @SubscribeEvent
    public static void layerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        LAYERS.forEach(event::registerLayerDefinition);
    }

    @SubscribeEvent
    public static void entityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ENTITY_RENDERERS.forEach(renderer -> renderer.accept(event));
    }

    // The conductor cap worn by players (upstream: Fabric API's ArmorRenderer).
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerModelType skin : event.getSkins()) {
            AvatarRenderer<AbstractClientPlayer> renderer = event.getPlayerRenderer(skin);
            if (renderer != null)
                renderer.addLayer(new ConductorCapItemRenderer<>(renderer, event.getEntityModels()));
        }
    }

    @SubscribeEvent
    public static void menuScreens(RegisterMenuScreensEvent event) {
        MENU_SCREENS.forEach(screen -> screen.accept(event));
    }

    @SubscribeEvent
    public static void fluidModels(RegisterFluidModelsEvent event) {
        FLUID_MODELS.forEach(model -> model.accept(event));
    }

    // Fabric's ResourceManagerHelper.registerBuiltinResourcePack(..., NORMAL): optional client packs,
    // off until the player turns them on.
    @SubscribeEvent
    public static void packFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES)
            return;
        PACKS.forEach((id, name) -> event.addPackFinders(
            Railways.asResource("resourcepacks/" + id),
            PackType.CLIENT_RESOURCES,
            Component.literal(name),
            PackSource.BUILT_IN,
            false,
            Pack.Position.TOP
        ));
    }
}
