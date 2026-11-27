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

package com.railwayteam.railways.registry.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import com.railwayteam.railways.neoforge.RailwaysNeoForgeClientEvents;
import net.minecraft.client.KeyMapping;

// Port: NeoForge registers key mappings in RegisterKeyMappingsEvent (RailwaysNeoForgeClientEvents),
// and KeyMapping.getKey() (a NeoForge extension) is Fabric's KeyMappingHelper.getBoundKeyOf.
public class CRKeysImpl {
    public static void registerKeyBinding(KeyMapping keyMapping) {
        RailwaysNeoForgeClientEvents.KEYS.add(keyMapping);
    }

    public static int getBoundCode(KeyMapping keyMapping) {
        InputConstants.Key key = keyMapping.getKey();
        return key.getType() == InputConstants.Type.KEYSYM ? key.getValue() : InputConstants.UNKNOWN.getValue();
    }

    public static boolean isMouseBound(KeyMapping keyMapping) {
        return keyMapping.getKey().getType() == InputConstants.Type.MOUSE;
    }
}
