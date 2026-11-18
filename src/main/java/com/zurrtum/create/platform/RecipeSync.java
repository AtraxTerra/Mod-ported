package com.zurrtum.create.platform;

import com.zurrtum.create.AllRecipeTypes;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.util.List;

/**
 * Port-only: Create's recipe types are sent to clients, for the recipe viewers.
 *
 * <p>Since 1.21.2 a client no longer receives the server's recipes: the loader sends only the
 * recipe types something asks for. Upstream asks through Fabric API's
 * {@code RecipeSynchronization} ({@code compat/fabric/RecipeCommonPlugin}, excluded from this
 * build). Without the NeoForge request below, JEI's Create categories read an
 * {@code Internal.getClientSyncedRecipes()} that holds none of these types: crushing, milling,
 * mixing, pressing, deploying, sequenced assembly and the rest showed no recipe at all, single
 * player included; only the categories built from vanilla recipes (automatic packing and
 * shapeless, bulk blasting and smoking) and mystery conversion had any. The same sixteen types as
 * upstream, sent on every datapack sync (joining, {@code /reload}).
 */
public final class RecipeSync {
    private RecipeSync() {
    }

    public static List<RecipeType<?>> types() {
        return List.of(
            AllRecipeTypes.COMPACTING,
            AllRecipeTypes.PRESSING,
            AllRecipeTypes.MIXING,
            AllRecipeTypes.MILLING,
            AllRecipeTypes.CUTTING,
            AllRecipeTypes.CRUSHING,
            AllRecipeTypes.ITEM_APPLICATION,
            AllRecipeTypes.DEPLOYING,
            AllRecipeTypes.SANDPAPER_POLISHING,
            AllRecipeTypes.EMPTYING,
            AllRecipeTypes.MECHANICAL_CRAFTING,
            AllRecipeTypes.FILLING,
            AllRecipeTypes.SEQUENCED_ASSEMBLY,
            AllRecipeTypes.HAUNTING,
            AllRecipeTypes.SPLASHING,
            AllRecipeTypes.POTION
        );
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.sendRecipes(types().toArray(RecipeType[]::new));
    }
}
