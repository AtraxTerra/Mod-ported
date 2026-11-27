package dev.chaevsfe.trackwright.client;

import com.zurrtum.create.AllBlockEntityTypes;
import com.zurrtum.create.client.AllBlockEntityBehaviours;
import dev.chaevsfe.trackwright.Trackwright;
import dev.chaevsfe.trackwright.client.actors.RollerTerrainScroll;
import dev.chaevsfe.trackwright.client.config.ClientConfig;
import dev.chaevsfe.trackwright.client.controls.TrainControlKeys;
import dev.chaevsfe.trackwright.client.guide.ActionBarHold;
import dev.chaevsfe.trackwright.client.guide.BoreShaping;
import dev.chaevsfe.trackwright.client.guide.ClearBedPreview;
import dev.chaevsfe.trackwright.client.guide.RadiusLock;
import dev.chaevsfe.trackwright.client.guide.TrackGuide;
import dev.chaevsfe.trackwright.client.guide.UndoKey;
import dev.chaevsfe.trackwright.client.guide.WidestTurnLock;
import dev.chaevsfe.trackwright.client.markers.ClientMarkers;
import dev.chaevsfe.trackwright.client.schedule.ActorStepsClient;
import dev.chaevsfe.trackwright.client.track.FarEnd;
import dev.chaevsfe.trackwright.config.RemoteSettings;
import dev.chaevsfe.trackwright.markers.Markers;
import dev.chaevsfe.trackwright.neoforge.PlatformEvents;
import dev.chaevsfe.trackwright.network.SettingsPayload;
import dev.chaevsfe.trackwright.network.TrackwrightNetwork;
import dev.chaevsfe.trackwright.track.BoreProfiles;
import dev.chaevsfe.trackwright.track.Messages;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Port: upstream's Fabric {@code client} entrypoint. Create Fly's addon entrypoint calls
 * {@link #onInitializeClient} at the same moment, in Minecraft's constructor (see
 * {@code neoforge.TrackwrightEntrypoint}). Fabric's client events become NeoForge's; the key mappings
 * and the guide's HUD layer go to the mod bus events in {@code neoforge.TrackwrightClientEvents}, and
 * the block entity load and unload events come from {@code neoforge.mixin.LevelChunkMixin} and
 * {@code ClientChunkCacheMixin}, as Fabric API's own implementation does.
 */
public final class TrackwrightClient {
    public void onInitializeClient() {
        Trackwright.loadHookedClasses(
            "com.zurrtum.create.client.content.trains.schedule.ScheduleScreen",
            "com.zurrtum.create.client.foundation.blockEntity.behaviour.ValueSettingsInputHandler",
            "com.zurrtum.create.client.foundation.blockEntity.behaviour.scrollValue.ScrollValueRenderer",
            "com.zurrtum.create.client.content.trains.track.TrackPlacementClient",
            "com.zurrtum.create.client.content.trains.track.TrackPlacementOverlay",
            "com.zurrtum.create.client.content.trains.track.TrackRenderer",
            "com.zurrtum.create.client.content.trains.track.TrackVisual",
            "com.zurrtum.create.client.flywheel.lib.visual.AbstractVisual",
            "com.zurrtum.create.client.foundation.gui.widget.ScrollInput",
            "com.zurrtum.create.content.trains.graph.TrackGraph",
            "net.minecraft.client.renderer.extract.LevelExtractor",
            "net.minecraft.client.MouseHandler",
            "net.minecraft.client.renderer.ScreenEffectRenderer",
            "net.minecraft.client.Minecraft",
            "com.zurrtum.create.client.content.contraptions.ContraptionHandlerClient",
            // Port: the vanilla classes of the block entity load and unload hooks (neoforge.mixin).
            "net.minecraft.client.multiplayer.ClientChunkCache",
            "net.minecraft.world.level.chunk.LevelChunk"
        );
        ClientConfig.load();
        AllBlockEntityBehaviours.add(AllBlockEntityTypes.MECHANICAL_ROLLER, RollerTerrainScroll::new);
        ActorStepsClient.register();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Trackwright.MOD_ID, "trackwright"));
        WidestTurnLock.register(category);
        RadiusLock.register(category);
        UndoKey.register(category);
        BoreShaping.register(category);
        TrainControlKeys.register(category);
        BoreProfiles.setClientSource(BoreShaping::request);
        Markers.setClientSource(ClientMarkers::contains);
        Markers.setClientCoverage(ClientMarkers::covers);
        TrackwrightNetwork.receiveOnClient(TrackwrightClient::receiveSettings, UndoKey::receive, ClientMarkers::receive);
        var game = NeoForge.EVENT_BUS;
        // Fabric's INIT (a new play connection) and DISCONNECT.
        game.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> reset());
        game.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> reset());
        // Fabric's GAME message event sees the messages that are shown: the lowest priority, not cancelled.
        game.addListener(EventPriority.LOWEST, false, ClientChatReceivedEvent.System.class, event -> {
            if (event.isOverlay() && Messages.isOwnOverlay(event.getMessage())) {
                ActionBarHold.start();
            }
        });
        game.addListener(ClientTickEvent.Post.class, event -> FarEnd.refresh(Minecraft.getInstance()));
        PlatformEvents.onUseBlock(WidestTurnLock::beforeBlockUse);
        PlatformEvents.onUseBlock(BoreShaping::beforeBlockUse);
        game.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft mc = Minecraft.getInstance();
            WidestTurnLock.tick(mc);
            RadiusLock.tick(mc);
            UndoKey.tick(mc);
            BoreShaping.tick(mc);
            ClearBedPreview.tick(mc);
            TrainControlKeys.tick(mc);
            ClientMarkers.tick(mc);
        });
        game.addListener(ItemTooltipEvent.class, event -> ClientMarkers.tooltip(event.getItemStack(), event.getContext(), event.getFlags(), event.getToolTip()));
    }

    /** The guide's HUD layer, below the chat as upstream attaches it: registered by TrackwrightClientEvents. */
    public static final Identifier GUIDE_LAYER = Identifier.fromNamespaceAndPath(Trackwright.MOD_ID, "track_guide");

    private static void reset() {
        RemoteSettings.clear();
        UndoKey.reset();
        ActionBarHold.reset();
        TrackGuide.reset();
        FarEnd.reset();
        BoreShaping.reset();
        RadiusLock.reset();
        ClearBedPreview.reset();
        TrainControlKeys.reset();
        ClientMarkers.reset();
    }

    private static void receiveSettings(SettingsPayload payload) {
        if (!payload.sameVersion()) {
            Trackwright.LOGGER.info("This server's Trackwright sends settings format {} and this client reads format {}; only the settings both know are used",
                payload.version(), SettingsPayload.VERSION);
        }
        RemoteSettings.set(payload.settings());
        TrackGuide.forgetPlan();
    }
}
