package com.zurrtum.create.foundation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

/**
 * Port note: upstream is a marker interface that LivingEntityMixin checks where vanilla's
 * handleOnClimbable asks {@code is(Blocks.SCAFFOLDING)}. NeoForge patches that test into
 * {@code state.isScaffolding(entity)}, an {@link IBlockExtension} hook -- its only call site -- so the
 * marker now answers that hook directly, and the mixin is gone.
 */
public interface ScaffoldingControlBlock extends IBlockExtension {
    @Override
    default boolean isScaffolding(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return true;
    }
}
