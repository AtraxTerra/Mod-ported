package de.mrjulsen.mcdragonlib.block;

import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public abstract class DLSyncedBlockEntity extends BlockEntity {

	public DLSyncedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveWithFullMetadata(registries);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	// Special handling for client update packets
	// NeoForge 26.2 port: block entities load from a ValueInput and save to a ValueOutput; these
	// CompoundTag entry points wrap the tag in the tag-backed implementations.
	public void readClient(CompoundTag tag) {
		loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, registries(), tag));
	}

	// Special handling for client update packets
	public CompoundTag writeClient(CompoundTag tag) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries());
		saveAdditional(output);
		return tag.merge(output.buildResult());
	}

	private HolderLookup.Provider registries() {
		return level != null ? level.registryAccess() : RegistryAccess.EMPTY;
	}

	public void sendData() {
		if (level instanceof ServerLevel serverLevel) {			
			serverLevel.getChunkSource().blockChanged(getBlockPos());
		}
	}

	public void notifyUpdate() {
		setChanged();
		sendData();
	}

	public LevelChunk containedChunk() {
		return level.getChunkAt(worldPosition);
	}
}
