// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.compat;

import com.jesz.createdieselgenerators.CDGRecipes;
import com.zurrtum.create.compat.Mods;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.util.List;

public final class CDGRecipeSync {
    private CDGRecipeSync() {
    }

    public static List<RecipeSerializer<?>> serializers() {
        return List.of(
            CDGRecipes.BASIN_FERMENTING.getSerializer(),
            CDGRecipes.BULK_FERMENTING.getSerializer(),
            CDGRecipes.DISTILLATION.getSerializer(),
            CDGRecipes.COMPRESSION_MOLDING.getSerializer(),
            CDGRecipes.CASTING.getSerializer(),
            CDGRecipes.WIRE_CUTTING.getSerializer(),
            CDGRecipes.HAMMERING.getSerializer()
        );
    }

    public static List<RecipeType<?>> types() {
        return List.of(
            CDGRecipes.BASIN_FERMENTING.getType(),
            CDGRecipes.BULK_FERMENTING.getType(),
            CDGRecipes.DISTILLATION.getType(),
            CDGRecipes.COMPRESSION_MOLDING.getType(),
            CDGRecipes.CASTING.getType(),
            CDGRecipes.WIRE_CUTTING.getType(),
            CDGRecipes.HAMMERING.getType()
        );
    }

    public static void register() {
        if (!Mods.JEI.isLoaded() && !Mods.RRV.isLoaded())
            return;
        // Port: NeoForge sends chosen recipe types to clients on datapack sync (join and /reload).
        NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, event -> event.sendRecipes(types().toArray(RecipeType[]::new)));
    }
}
