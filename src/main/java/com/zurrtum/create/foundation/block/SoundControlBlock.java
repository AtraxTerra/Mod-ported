package com.zurrtum.create.foundation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import org.jspecify.annotations.Nullable;

/**
 * A block whose sound depends on where it is, rather than on its state alone.
 *
 * <h2>How this is wired on NeoForge, and why it needs no mixin</h2>
 * Fabric has no position-aware sound hook, so upstream implements this with three
 * {@code @WrapOperation}s on {@code BlockState.getSoundType()} call sites: the step sound in
 * {@code Entity}, the fall sound in {@code LivingEntity}, and the place sound in {@code BlockItem}.
 *
 * <p>NeoForge already has the hook. {@link IBlockExtension#getSoundType(BlockState, LevelReader,
 * BlockPos, Entity)} is a level-sensitive sound type that every block can override, and NeoForge
 * patches every one of those call sites -- steps, falls, placing, breaking, hitting -- to go through
 * it. That patch is also why the upstream mixins cannot work here: the {@code getSoundType()} calls
 * they wrap are gone, and the first server boot died on exactly that, with
 * {@code EntityMixin.getStepSound} finding zero targets.
 *
 * <p>So this interface extends {@link IBlockExtension} and overrides that default. Java resolves
 * the most specific default, and this interface is more specific than {@code IBlockExtension}, so
 * any block implementing it gets the position-aware sound through NeoForge's own hooks. It would
 * only lose to a block that declares the method itself, which would be deliberate.
 */
public interface SoundControlBlock extends IBlockExtension {
    SoundType getSoundGroup(LevelReader level, BlockPos pos);

    @Override
    default SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return getSoundGroup(level, pos);
    }
}
