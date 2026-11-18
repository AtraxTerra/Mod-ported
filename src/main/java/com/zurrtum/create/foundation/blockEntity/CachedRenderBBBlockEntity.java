package com.zurrtum.create.foundation.blockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public abstract class CachedRenderBBBlockEntity extends SyncedBlockEntity {

    private @Nullable AABB renderBoundingBox;

    public CachedRenderBBBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // Port: upstream marks this @Environment(EnvType.CLIENT). NeoForge 26 no longer strips @OnlyIn
    // members and reports each use as a loading warning, which puts a click-through warnings
    // screen in front of every launch. Nothing here is client-only, so it is simply dropped.
    public AABB getRenderBoundingBox() {
        if (renderBoundingBox == null) {
            renderBoundingBox = createRenderBoundingBox();
        }
        return renderBoundingBox;
    }

    protected void invalidateRenderBoundingBox() {
        renderBoundingBox = null;
    }

    protected AABB createRenderBoundingBox() {
        return new AABB(getBlockPos());
    }

}
