/*
 * Steam 'n' Rails
 * Copyright (c) 2022-2026 The Railways Team
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

package com.railwayteam.railways.content.buffer.headstock.fabric;

import com.railwayteam.railways.content.buffer.TrackBufferBlock;
import com.railwayteam.railways.content.buffer.fabric.BufferModel;
import com.railwayteam.railways.content.buffer.headstock.CopycatHeadstockBarsBlock;
import com.railwayteam.railways.content.buffer.headstock.CopycatHeadstockBlock;
import com.railwayteam.railways.content.buffer.headstock.HeadstockBlock;
import com.railwayteam.railways.content.buffer.single_deco.GenericDyeableSingleBufferBlock;
import com.railwayteam.railways.content.buffer.single_deco.LinkPinBlock;
import com.railwayteam.railways.content.custom_tracks.generic_crossing.GenericCrossingBlock;
import com.railwayteam.railways.content.custom_tracks.generic_crossing.fabric.GenericCrossingModel;
import com.zurrtum.create.client.AllModels;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Port: upstream wraps these blocks' models through Fabric API's model loading plugin, before baking.
 * Create Fly does the same for its own blocks through {@link AllModels#register}, a block -> wrapper
 * map applied at the same point by its loader-independent model loading, so the wrappers go there.
 *
 * <p>The matching item model wrappers (upstream's {@code modifyItemModelBeforeBake}, which show the
 * copied material and colour of a headstock or buffer item) are applied by {@link #wrapItemModels},
 * called by the NeoForge {@code ClientItemInfoLoaderMixin} on every model reload.
 */
public class CopycatHeadstockModelRegistration {
	private static boolean registered;

	public static void register() {
		if (registered)
			return;
		registered = true;

		for (Block block : BuiltInRegistries.BLOCK) {
			if (block instanceof CopycatHeadstockBlock)
				AllModels.register(block, CopycatHeadstockBlockStateModel::new);
			else if (block instanceof CopycatHeadstockBarsBlock)
				AllModels.register(block, CopycatHeadstockBarsModel::new);
			else if (block instanceof GenericCrossingBlock)
				AllModels.register(block, GenericCrossingModel::new);
			else if (isBufferBlock(block))
				AllModels.register(block, BufferModel::new);
		}
	}

	/**
	 * Port: upstream's {@code modifyItemModelBeforeBake} callback, run over the loaded item definitions.
	 */
	public static ClientItemInfoLoader.LoadedClientInfos wrapItemModels(ClientItemInfoLoader.LoadedClientInfos infos) {
		Map<Identifier, ClientItem> contents = new HashMap<>(infos.contents());
		boolean changed = false;
		for (Map.Entry<Identifier, ClientItem> entry : contents.entrySet()) {
			ClientItem item = entry.getValue();
			ItemModel.Unbaked model = wrapItemModel(entry.getKey(), item.model());
			if (model != item.model()) {
				entry.setValue(new ClientItem(model, item.properties(), item.registrySwapper()));
				changed = true;
			}
		}
		return changed ? new ClientItemInfoLoader.LoadedClientInfos(contents) : infos;
	}

	private static ItemModel.Unbaked wrapItemModel(Identifier itemId, ItemModel.Unbaked model) {
		if (!(model instanceof CuboidItemModelWrapper.Unbaked cuboid))
			return model;
		if (!(BuiltInRegistries.ITEM.getValue(itemId) instanceof BlockItem blockItem))
			return model;
		if (blockItem.getBlock() instanceof CopycatHeadstockBlock)
			return new CopycatHeadstockBlockStateModel.UnbakedItemModel(cuboid);
		return isBufferBlock(blockItem.getBlock()) ? new BufferModel.UnbakedItemModel(cuboid) : model;
	}

	private static boolean isBufferBlock(Block block) {
		return block instanceof TrackBufferBlock<?>
			|| block instanceof GenericDyeableSingleBufferBlock
			|| block instanceof LinkPinBlock
			|| block instanceof HeadstockBlock;
	}
}
