package com.zurrtum.create.foundation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import net.neoforged.neoforge.common.extensions.IBlockExtension;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;
public interface ResistanceControlBlock extends IBlockExtension {
    float getResistance(BlockGetter world, BlockPos pos);

    /** Replaces upstream's wrap on Block.getExplosionResistance(); NeoForge patches that call to this hook. */
    @Override
    default float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return getResistance(level, pos);
    }
}
