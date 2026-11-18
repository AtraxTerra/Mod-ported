package com.zurrtum.create.foundation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

/**
 * A block that can switch off entity bounce for some of its states.
 *
 * <p>Same situation as {@link SoundControlBlock}. Upstream wraps
 * {@code Entity.getBlockBounciness(Block)} inside {@code restituteMovementAfterCollisions}. NeoForge
 * patches both: the method gains a {@code BlockPos} parameter, and the call becomes
 * {@code getBlockBounciness(BlockPos, BlockState)}, which asks the block through
 * {@link IBlockExtension#getBounceRestitution(Level, BlockPos, BlockState, Entity)}. The upstream wrap
 * found zero targets and was the second boot-killing mixin.
 *
 * <p>So this overrides that default directly. Returning 0 is exactly upstream's effect, since NeoForge
 * only scales the value afterwards.
 */
public interface BouncinessControlBlock extends IBlockExtension {
    boolean skipBounciness(BlockState state);

    @Override
    default float getBounceRestitution(Level level, BlockPos pos, BlockState state, Entity entity) {
        return skipBounciness(state) ? 0F : IBlockExtension.super.getBounceRestitution(level, pos, state, entity);
    }
}
