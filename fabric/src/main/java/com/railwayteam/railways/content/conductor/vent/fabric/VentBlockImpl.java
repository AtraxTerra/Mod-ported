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

package com.railwayteam.railways.content.conductor.vent.fabric;

import com.railwayteam.railways.content.conductor.vent.VentBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public class VentBlockImpl extends VentBlock {
    public VentBlockImpl(Properties properties) {
        super(properties);
    }

    public static VentBlock create(Properties properties) {
        return new VentBlockImpl(properties);
    }

    // Adapted from chaevsfe / Create Fly: Steam 'n' Rails (https://github.com/chaevsfe/Create-Steam-n-Rails-Fly)
    // Based on original work from Create: Steam 'n' Rails by The Railways Team (https://github.com/Layers-of-Railways/Railway)
    // Licensed under LGPL-3.0-only — extracted/adapted with AI assistance.
    //
    // Port: VentBlock declares these two NeoForge block hooks with placeholder bodies (throw new
    // AssertionError), which the Forge build overrides in its VentBlockImpl and the Fabric build never
    // calls. NeoForge calls them while meshing chunks, so a vent next to any block crashed the client.
    // Same logic as the original Forge VentBlockImpl: the vent hides a neighbour's face as its
    // copied material would.

    @Override
    public boolean supportsExternalFaceHiding(BlockState state) {
        return true;
    }

    @Override
    public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighborState,
                                     Direction dir) {
        if (state.is(this) == neighborState.is(this)) {
            if (getMaterial(level, pos).skipRendering(getMaterial(level, pos.relative(dir)), dir.getOpposite()))
                return true;
        }

        return getMaterial(level, pos).skipRendering(neighborState, dir.getOpposite());
    }
}
