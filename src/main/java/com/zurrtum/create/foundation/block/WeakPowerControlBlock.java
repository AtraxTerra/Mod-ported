package com.zurrtum.create.foundation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.extensions.IBlockExtension;
/**
 * Same name and same signature as NeoForge's IBlockExtension.shouldCheckWeakPower, so extending it
 * turns Create's method into an override of the native hook with no bridging code. Upstream needed a
 * wrap on SignalGetter.getSignal's isRedstoneConductor call for this; NeoForge already routes that
 * call through this method, and its default is exactly isRedstoneConductor.
 */
public interface WeakPowerControlBlock extends IBlockExtension {
    @Override
    boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction side);
}
