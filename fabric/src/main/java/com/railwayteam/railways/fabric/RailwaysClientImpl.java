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

package com.railwayteam.railways.fabric;

import com.zurrtum.create.platform.Platform;
import com.mojang.brigadier.CommandDispatcher;
import com.railwayteam.railways.Railways;
import com.railwayteam.railways.RailwaysClient;
import com.railwayteam.railways.content.buffer.headstock.fabric.CopycatHeadstockModelRegistration;
import com.railwayteam.railways.content.conductor.fabric.ConductorCapItemRenderer;
import com.railwayteam.railways.neoforge.RailwaysNeoForgeClientEvents;
import com.railwayteam.railways.registry.CRParticleTypes;
import com.railwayteam.railways.registry.fabric.CRBlockEntitiesClientImpl;
import com.railwayteam.railways.registry.fabric.client.CRTooltipBehaviours;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.function.Consumer;
import java.util.function.Supplier;

// Port: the Fabric ClientModInitializer, called by Create Fly through RailwaysEntrypoints inside
// Minecraft's constructor, where Fabric would have called it. Fabric API's registries became queues
// that RailwaysNeoForgeClientEvents hands to the matching NeoForge events, all of which fire later
// (key mappings, particles, model layers, entity renderers, packs, reload listeners); its client
// events are subscribed there too.
public class RailwaysClientImpl {
	public void onInitializeClient() {
		CopycatHeadstockModelRegistration.register();
		CRBlockEntitiesClientImpl.register();
		CRTooltipBehaviours.register();
		RailwaysClient.init();
		if (Platform.isModLoaded("jei")) {
			registerJeiCompat();
		}
		ConductorCapItemRenderer.register();
		CRParticleTypes.registerFactories();
	}

	private static void registerJeiCompat() {
		try {
			Class.forName("com.railwayteam.railways.compat.jei.RailwaysJeiClient")
				.getMethod("register")
				.invoke(null);
		} catch (ReflectiveOperationException e) {
			Railways.LOGGER.error("Failed to register Railways JEI client compatibility", e);
		}
	}

	public static void registerClientCommands(Consumer<CommandDispatcher<SharedSuggestionProvider>> consumer) {
		RailwaysNeoForgeClientEvents.CLIENT_COMMANDS.add(consumer);
	}

	public static void registerModelLayer(ModelLayerLocation layer, Supplier<LayerDefinition> definition) {
		RailwaysNeoForgeClientEvents.LAYERS.put(layer, definition);
	}

	public static <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
		RailwaysNeoForgeClientEvents.ENTITY_RENDERERS.add(event -> event.registerEntityRenderer(type, provider));
	}

	public static void registerBuiltinPack(String id, String name) {
		RailwaysNeoForgeClientEvents.PACKS.put(id, name);
	}
}
