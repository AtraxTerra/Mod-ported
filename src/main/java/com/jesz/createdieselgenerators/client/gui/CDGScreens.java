// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.client.gui;

import com.jesz.createdieselgenerators.CDGMenuTypes;
import com.jesz.createdieselgenerators.content.entity_filter.EntityFilterMenu;
import com.jesz.createdieselgenerators.content.entity_filter.EntityFilterScreen;
import com.zurrtum.create.client.AllMenuScreens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.world.item.ItemStack;

public final class CDGScreens {
    private CDGScreens() {
    }

    public static void register() {
        AllMenuScreens.register(CDGMenuTypes.ENTITY_FILTER,
                (client, type, syncId, inventory, title, buf) ->
                        new EntityFilterScreen(new EntityFilterMenu(syncId, inventory, ItemStack.STREAM_CODEC.decode(buf)), inventory, title));
    }
}
