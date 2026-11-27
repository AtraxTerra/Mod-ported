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

package com.railwayteam.railways.util.fabric;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.railwayteam.railways.util.RegistrationListening.Listener;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

// Port: Fabric API's RegistryEntryAddedCallback has no NeoForge counterpart. An entry that already
// exists is reported at once, as upstream does; the rest are reported once NeoForge has run every
// mod's registrations for that registry (RegisterEvent, at lowest priority), which is when modded
// entries appear on NeoForge.
@EventBusSubscriber(modid = com.railwayteam.railways.Railways.MOD_ID)
public class RegistrationListeningImpl {
	private static final Multimap<Registry<?>, Listener<?>> pending = HashMultimap.create();

	public static synchronized <T> void addListener(Listener<T> listener) {
		Registry<T> registry = listener.registry();
		if (registry.containsKey(listener.id()))
			listener.onRegister(registry.getValue(listener.id()));
		else pending.put(registry, listener);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static synchronized void onRegister(RegisterEvent event) {
		for (Registry<?> registry : pending.keySet().toArray(Registry<?>[]::new)) {
			if (!registry.key().equals(event.getRegistryKey()))
				continue;
			pending.get(registry).removeIf(listener -> report(listener));
		}
	}

	private static <T> boolean report(Listener<T> listener) {
		Identifier id = listener.id();
		if (!listener.registry().containsKey(id))
			return false;
		listener.onRegister(listener.registry().getValue(id));
		return true;
	}
}
