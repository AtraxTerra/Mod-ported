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

package com.railwayteam.railways.fabric.events;

/**
 * Port: upstream subscribes to Fabric API's server tick, player join and data-pack reload events here
 * and registers the liquid fuel reload listener. On NeoForge those are
 * {@code com.railwayteam.railways.neoforge.RailwaysNeoForgeEvents}, an {@code @EventBusSubscriber}
 * (level tick, player login, tags updated, server reload listeners), so there is nothing left to do
 * when the mod initialises. Kept so {@code RailwaysImpl} reads as upstream's.
 */
public class CommonEventsFabric {
	public static void init() {
	}
}
