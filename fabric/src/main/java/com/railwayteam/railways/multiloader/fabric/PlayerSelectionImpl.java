/*
 * Steam 'n' Rails
 * Copyright (c) 2022-2024 The Railways Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.railwayteam.railways.multiloader.fabric;

import com.railwayteam.railways.multiloader.PlayerSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

// Port: Fabric API's PlayerLookup and ServerPlayNetworking, done with vanilla and NeoForge. The
// tracking sets are the same ones Fabric reads: an entity's ChunkMap.TrackedEntity.seenBy (opened by
// the access transformer), a position's players watching its chunk.
public class PlayerSelectionImpl extends PlayerSelection {
    /**
     * Upstream forces this class's static initializer to register its server lifecycle listeners.
     * NeoForge knows the running server itself ({@link ServerLifecycleHooks}), so this is a no-op kept
     * for {@code RailwaysImpl}.
     */
    public static void init() {
    }

    private final Collection<ServerPlayer> players;

    Collection<ServerPlayer> railways$getPlayers() {
        return players;
    }

    private PlayerSelectionImpl(Collection<ServerPlayer> players) {
        this.players = players;
    }

    @Override
    public void accept(Identifier id, FriendlyByteBuf buffer) {
        byte[] data = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), data);
        CustomPacketPayload.Type<PacketSetImpl.RailwaysPayload> type = new CustomPacketPayload.Type<>(id);
        PacketSetImpl.RailwaysPayload payload = new PacketSetImpl.RailwaysPayload(type, data);
        for (ServerPlayer player : players)
            PacketDistributor.sendToPlayer(player, payload);
    }

    private static List<ServerPlayer> allPlayers() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? Collections.emptyList() : server.getPlayerList().getPlayers();
    }

    public static PlayerSelection all() {
        return new PlayerSelectionImpl(List.copyOf(allPlayers()));
    }

    public static PlayerSelection allWith(Predicate<ServerPlayer> condition) {
        return new PlayerSelectionImpl(allPlayers().stream().filter(condition).toList());
    }

    public static PlayerSelection of(ServerPlayer player) {
        return new PlayerSelectionImpl(Collections.singleton(player));
    }

    public static PlayerSelection tracking(Entity entity) {
        return new PlayerSelectionImpl(trackingPlayers(entity));
    }

    public static PlayerSelection trackingWith(Entity entity, Predicate<ServerPlayer> condition) {
        return new PlayerSelectionImpl(trackingPlayers(entity).stream().filter(condition).toList());
    }

    public static PlayerSelection tracking(BlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel level))
            return new PlayerSelectionImpl(Collections.emptyList());
        return new PlayerSelectionImpl(trackingPlayers(level, be.getBlockPos()));
    }

    public static PlayerSelection tracking(ServerLevel level, BlockPos pos) {
        return new PlayerSelectionImpl(trackingPlayers(level, pos));
    }

    public static PlayerSelection trackingAndSelf(ServerPlayer player) {
        ArrayList<ServerPlayer> players = new ArrayList<>(trackingPlayers(player));
        players.add(player);
        return new PlayerSelectionImpl(players);
    }

    /** Fabric's {@code PlayerLookup.tracking(Entity)}: whoever the entity's tracker sends updates to. */
    private static Collection<ServerPlayer> trackingPlayers(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level))
            return Collections.emptyList();
        ChunkMap.TrackedEntity tracked = level.getChunkSource().chunkMap.entityMap.get(entity.getId());
        if (tracked == null)
            return Collections.emptyList();
        List<ServerPlayer> players = new ArrayList<>(tracked.seenBy.size());
        for (ServerPlayerConnection connection : tracked.seenBy)
            players.add(connection.getPlayer());
        return players;
    }

    /** Fabric's {@code PlayerLookup.tracking(ServerLevel, BlockPos)}: whoever watches the chunk. */
    private static Collection<ServerPlayer> trackingPlayers(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false);
    }
}
