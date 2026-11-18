package com.zurrtum.create.foundation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;

import net.neoforged.neoforge.common.extensions.IBlockExtension;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
public interface SlipperinessControlBlock extends IBlockExtension {
    float getSlipperiness(LevelReader world, BlockPos pos);

    /**
     * Upstream feeds getSlipperiness through @WrapOperations on Block.getFriction() in four entity
     * classes. NeoForge patches all four to ask the block through this level-sensitive hook
     * instead, so the bridge lives here and those wraps are gone. Leashable is the exception: it
     * still calls the vanilla method, so its upstream wrap is kept.
     */
    @Override
    default float getFriction(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return getSlipperiness(level, pos);
    }
}
