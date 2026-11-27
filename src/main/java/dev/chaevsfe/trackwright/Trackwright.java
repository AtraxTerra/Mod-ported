package dev.chaevsfe.trackwright;

import dev.chaevsfe.trackwright.actors.TrainControls;
import dev.chaevsfe.trackwright.config.ServerConfig;
import dev.chaevsfe.trackwright.markers.Markers;
import dev.chaevsfe.trackwright.network.TrackwrightNetwork;
import dev.chaevsfe.trackwright.schedule.ActorSteps;
import dev.chaevsfe.trackwright.track.BoreProfiles;
import dev.chaevsfe.trackwright.track.ClearBed;
import dev.chaevsfe.trackwright.track.PassengerResync;
import dev.chaevsfe.trackwright.undo.UndoCommand;
import dev.chaevsfe.trackwright.undo.UndoJournal;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Port: upstream's Fabric {@code main} entrypoint. Create Fly's addon entrypoint calls
 * {@link #onInitialize} at the same moment (see {@code neoforge.TrackwrightEntrypoint}); each Fabric
 * event becomes its NeoForge counterpart on the game event bus.
 */
public final class Trackwright {
    public static final String MOD_ID = "trackwright";
    public static final Logger LOGGER = LoggerFactory.getLogger("Trackwright");

    public void onInitialize() {
        loadHookedClasses(
            "com.zurrtum.create.AllHandle",
            "com.zurrtum.create.catnip.config.ConfigBase$CValue",
            "com.zurrtum.create.content.contraptions.Contraption",
            "com.zurrtum.create.content.contraptions.ContraptionCollider",
            "com.zurrtum.create.content.contraptions.actors.roller.RollerBlockEntity",
            "com.zurrtum.create.content.contraptions.actors.roller.RollerMovementBehaviour",
            "com.zurrtum.create.content.contraptions.actors.seat.SeatMovementBehaviour",
            "com.zurrtum.create.content.kinetics.base.BlockBreakingKineticBlockEntity",
            "com.zurrtum.create.content.kinetics.base.BlockBreakingMovementBehaviour",
            "com.zurrtum.create.content.trains.entity.Carriage",
            "com.zurrtum.create.content.trains.entity.CarriageContraptionEntity",
            "com.zurrtum.create.content.trains.entity.Navigation",
            "com.zurrtum.create.content.trains.station.StationBlockEntity",
            "com.zurrtum.create.content.trains.track.TrackBlock",
            "com.zurrtum.create.content.trains.track.TrackBlockEntity",
            "com.zurrtum.create.content.trains.track.TrackBlockItem",
            "com.zurrtum.create.content.trains.track.TrackPaver",
            "com.zurrtum.create.content.trains.track.TrackPlacement",
            "com.zurrtum.create.content.trains.track.TrackPlacement$PlacementInfo",
            "com.zurrtum.create.content.trains.track.TrackTargetingBlockItem",
            "net.minecraft.server.network.ServerGamePacketListenerImpl",
            "net.minecraft.world.entity.LivingEntity"
        );
        TrackwrightNetwork.register();
        ClearBed.register();
        ActorSteps.register();
        Markers.register();
        var game = NeoForge.EVENT_BUS;
        // Fabric's SERVER_STARTING comes before the worlds load, like ServerAboutToStartEvent.
        game.addListener(ServerAboutToStartEvent.class, event -> ServerConfig.reload());
        game.addListener(ServerStoppedEvent.class, event -> PassengerResync.clear());
        game.addListener(ServerTickEvent.Post.class, event -> PassengerResync.tick(event.getServer()));
        game.addListener(ServerTickEvent.Post.class, event -> UndoJournal.tick(event.getServer()));
        game.addListener(ServerStoppedEvent.class, event -> UndoJournal.clearAll());
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> UndoJournal.forget(event.getEntity().getUUID()));
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> BoreProfiles.forget(event.getEntity().getUUID()));
        game.addListener(ServerStoppedEvent.class, event -> BoreProfiles.clearAll());
        game.addListener(ServerStoppedEvent.class, event -> TrainControls.clearAll());
        game.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                UndoJournal.clear(player);
            }
        });
        game.addListener(RegisterCommandsEvent.class, event -> UndoCommand.register(event.getDispatcher()));
        // Fabric's END_DATA_PACK_RELOAD: after a /reload, when the new data goes out to every player.
        game.addListener(OnDatapackSyncEvent.class, event -> {
            if (event.getPlayer() == null) {
                ServerConfig.reload();
                TrackwrightNetwork.sendToAll(event.getPlayerList().getServer());
            }
        });
    }

    public static void loadHookedClasses(String... names) {
        ClassLoader loader = Trackwright.class.getClassLoader();
        for (String name : names) {
            try {
                Class.forName(name, false, loader);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Trackwright cannot find " + name + " in this Create Fly build", e);
            }
        }
    }
}
