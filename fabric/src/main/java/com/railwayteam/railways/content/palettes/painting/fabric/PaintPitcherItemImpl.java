/*
 * Steam 'n' Rails
 * Copyright (c) 2025 The Railways Team
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

package com.railwayteam.railways.content.palettes.painting.fabric;

import com.railwayteam.railways.content.palettes.PalettesColor;
import com.railwayteam.railways.content.palettes.painting.PaintPitcherItem;
import com.railwayteam.railways.neoforge.RailwaysNeoForgeEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

public class PaintPitcherItemImpl extends PaintPitcherItem {
    public PaintPitcherItemImpl(Properties properties, @Nullable PalettesColor color) {
        super(properties, color);
        // Port: NeoForge's item fluid capability, registered when NeoForge asks for capabilities.
        RailwaysNeoForgeEvents.CAPABILITIES.add(event ->
            event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new PaintPitcherFluidStorage(access), this));
    }

    public static PaintPitcherItem create(Item.Properties properties, @Nullable PalettesColor color) {
        return new PaintPitcherItemImpl(properties, color);
    }

    // Port: NeoForge item hook, as in the original Forge build (Fabric has no such call): a pitcher whose
    // paint level changes stays in hand without the re-equip animation.
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return railways$shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }
}
