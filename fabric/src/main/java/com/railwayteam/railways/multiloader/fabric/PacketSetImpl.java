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

import com.railwayteam.railways.Railways;
import com.railwayteam.railways.multiloader.C2SPacket;
import com.railwayteam.railways.multiloader.PacketSet;
import com.railwayteam.railways.multiloader.PlayerSelection;
import com.railwayteam.railways.multiloader.S2CPacket;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.ApiStatus.Internal;

import java.util.List;
import java.util.function.Function;

public class PacketSetImpl extends PacketSet {
	protected PacketSetImpl(String id, int version,
							List<Function<FriendlyByteBuf, S2CPacket>> s2cPackets,
							Object2IntMap<Class<? extends S2CPacket>> s2cTypes,
							List<Function<FriendlyByteBuf, C2SPacket>> c2sPackets,
							Object2IntMap<Class<? extends C2SPacket>> c2sTypes) {
		super(id, version, s2cPackets, s2cTypes, c2sPackets, c2sTypes);
		// Port: NeoForge takes payload types and their handlers together, in RegisterPayloadHandlersEvent,
		// which fires after this set exists (it is built while Create Fly registers its addons).
		SETS.add(this);
	}

	/** Port: every packet set made so far; NeoForge registers their payloads in {@link #registerPayloads}. */
	private static final List<PacketSetImpl> SETS = new java.util.concurrent.CopyOnWriteArrayList<>();

	/**
	 * Port: Fabric's {@code PayloadTypeRegistry} and global receivers in one go. The handlers run on
	 * the main thread (NeoForge's default), as Fabric's do; the packet set's own handling then defers
	 * to the client or server executor exactly as upstream.
	 */
	public static void registerPayloads(RegisterPayloadHandlersEvent event) {
		for (PacketSetImpl set : SETS) {
			PayloadRegistrar registrar = event.registrar(Integer.toString(set.version));
			registrar.playToClient(payloadType(set.s2cPacket), RailwaysPayload.STREAM_CODEC,
				(payload, context) -> com.railwayteam.railways.neoforge.RailwaysClientPackets.handle(set, payload.toBuffer()));
			registrar.playToServer(payloadType(set.c2sPacket), RailwaysPayload.STREAM_CODEC,
				(payload, context) -> handleFromClient(set, payload, context));
		}
	}

	private static void handleFromClient(PacketSetImpl set, RailwaysPayload payload, IPayloadContext context) {
		if (context.player() instanceof ServerPlayer player)
			set.handleC2SPacket(player, payload.toBuffer());
	}

	// Port: NeoForge binds the handlers when it registers the payloads (registerPayloads); these
	// two only mark the moments upstream did that.
	public void registerS2CListener() {
	}

	public void registerC2SListener() {
	}

	protected void doSendC2S(FriendlyByteBuf buf) {
		ClientPacketDistributor.sendToServer(new RailwaysPayload(payloadType(c2sPacket), copyBytes(buf)));
	}

	public void send(Packet<? super ServerGamePacketListener> packet) {
		Minecraft.getInstance().getConnection().send(packet);
	}

	public void sendTo(ServerPlayer player, Packet<? super ClientGamePacketListener> packet) {
		player.connection.send(packet);
	}

	public void sendTo(PlayerSelection selection, Packet<? super ClientGamePacketListener> packet) {
		if (selection instanceof PlayerSelectionImpl impl) {
			for (ServerPlayer player : impl.railways$getPlayers())
				player.connection.send(packet);
		}
	}

	private static CustomPacketPayload.Type<RailwaysPayload> payloadType(Identifier id) {
		return new CustomPacketPayload.Type<>(id);
	}

	private static byte[] copyBytes(FriendlyByteBuf buf) {
		byte[] data = new byte[buf.readableBytes()];
		buf.getBytes(buf.readerIndex(), data);
		return data;
	}

	public record RailwaysPayload(CustomPacketPayload.Type<RailwaysPayload> type, byte[] data) implements CustomPacketPayload {
		public static final StreamCodec<RegistryFriendlyByteBuf, RailwaysPayload> STREAM_CODEC = StreamCodec.ofMember(
			RailwaysPayload::write,
			RailwaysPayload::decode
		);

		// TEMPORARY diagnostic: vanilla swallows the real exception behind a generic
		// "Failed to decode packet 'clientbound/minecraft:custom_payload'" disconnect reason.
		// Log it here so we can see what's actually going wrong.
		private static RailwaysPayload decode(RegistryFriendlyByteBuf buf) {
			int readableBefore = buf.readableBytes();
			try {
				Identifier id = buf.readIdentifier();
				byte[] data = buf.readByteArray();
				return new RailwaysPayload(payloadType(id), data);
			} catch (Throwable t) {
				Railways.LOGGER.error("RailwaysPayload decode failed, readableBytes was {}", readableBefore, t);
				throw t;
			}
		}

		private RailwaysPayload(RegistryFriendlyByteBuf buf) {
			this(payloadType(buf.readIdentifier()), buf.readByteArray());
		}

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeIdentifier(type.id());
			buf.writeByteArray(data);
		}

		FriendlyByteBuf toBuffer() {
			return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
		}
	}

	@Internal
	public static PacketSet create(String id, int version,
								   List<Function<FriendlyByteBuf, S2CPacket>> s2cPackets,
								   Object2IntMap<Class<? extends S2CPacket>> s2cTypes,
								   List<Function<FriendlyByteBuf, C2SPacket>> c2sPackets,
								   Object2IntMap<Class<? extends C2SPacket>> c2sTypes) {
		return new PacketSetImpl(id, version, s2cPackets, s2cTypes, c2sPackets, c2sTypes);
	}
}
