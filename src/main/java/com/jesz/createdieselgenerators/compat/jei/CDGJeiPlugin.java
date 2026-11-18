// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.compat.jei;

import com.jesz.createdieselgenerators.CDGBlocks;
import com.jesz.createdieselgenerators.CDGDataComponents;
import com.jesz.createdieselgenerators.CDGItems;
import com.jesz.createdieselgenerators.CDGRecipes;
import com.jesz.createdieselgenerators.CreateDieselGenerators;
import com.jesz.createdieselgenerators.content.basin_lid.BasinFermentingRecipe;
import com.jesz.createdieselgenerators.content.bulk_fermenter.BulkFermentingRecipe;
import com.jesz.createdieselgenerators.content.distillation.DistillationRecipe;
import com.jesz.createdieselgenerators.content.molds.CastingRecipe;
import com.jesz.createdieselgenerators.content.molds.CompressionMoldingRecipe;
import com.jesz.createdieselgenerators.content.tools.hammer.HammerRecipe;
import com.jesz.createdieselgenerators.content.tools.wire_cutters.WireCuttingRecipe;
import com.zurrtum.create.AllItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.common.Internal;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;

/**
 * Port: Diesel Generators' seven recipe types in JEI. The Fabric build shows them through "Create
 * Fly: Recipe Viewer" ({@code compat/viewer}, excluded here: that library has no NeoForge build);
 * this plugin shows the same categories with Create Fly's JEI helpers. The recipes come from the
 * client's synced recipe map, which {@code CDGRecipeSync} fills when JEI is on the server.
 */
@JeiPlugin
public class CDGJeiPlugin implements IModPlugin {
    private static final Identifier ID = CreateDieselGenerators.rl("jei_plugin");

    public static final IRecipeType<RecipeHolder<BasinFermentingRecipe>> BASIN_FERMENTING = type("basin_fermenting");
    public static final IRecipeType<RecipeHolder<BulkFermentingRecipe>> BULK_FERMENTING = type("bulk_fermenting");
    public static final IRecipeType<RecipeHolder<CompressionMoldingRecipe>> COMPRESSION_MOLDING = type("compression_molding");
    public static final IRecipeType<RecipeHolder<CastingRecipe>> CASTING = type("casting");
    public static final IRecipeType<RecipeHolder<DistillationRecipe>> DISTILLATION = type("distillation");
    public static final IRecipeType<RecipeHolder<HammerRecipe>> HAMMERING = type("hammering");
    public static final IRecipeType<RecipeHolder<WireCuttingRecipe>> WIRE_CUTTING = type("wire_cutting");

    @SuppressWarnings("unchecked")
    private static <T> IRecipeType<T> type(String path) {
        return (IRecipeType<T>) IRecipeType.create(CreateDieselGenerators.rl(path), RecipeHolder.class);
    }

    /** A mold of the given kind, as the recipe asks for it. */
    static ItemStack mold(Identifier moldType) {
        ItemStack stack = CDGItems.MOLD.asStack();
        stack.set(CDGDataComponents.MOLD_TYPE, moldType);
        return stack;
    }

    @Override
    public Identifier getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
            new BasinFermentingCategory(),
            new BulkFermentingCategory(),
            new CompressionMoldingCategory(),
            new CastingCategory(),
            new DistillationCategory(),
            ToolCategory.hammering(),
            ToolCategory.wireCutting()
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(BASIN_FERMENTING, CDGBlocks.BASIN_LID.asItem(), AllItems.BASIN);
        registration.addCraftingStation(BULK_FERMENTING, CDGBlocks.BULK_FERMENTER.asItem());
        registration.addCraftingStation(COMPRESSION_MOLDING, AllItems.MECHANICAL_PRESS, CDGItems.MOLD.asItem(), AllItems.BASIN);
        registration.addCraftingStation(CASTING, AllItems.SPOUT, CDGItems.MOLD.asItem(), AllItems.BASIN);
        registration.addCraftingStation(DISTILLATION, AllItems.FLUID_TANK, CDGItems.DISTILLATION_CONTROLLER.asItem());
        registration.addCraftingStation(HAMMERING, CDGItems.HAMMER.asItem());
        registration.addCraftingStation(WIRE_CUTTING, CDGItems.WIRE_CUTTERS.asItem());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RecipeMap map = Internal.getClientSyncedRecipes();
        registration.addRecipes(BASIN_FERMENTING, map.byType(CDGRecipes.BASIN_FERMENTING.getType()).stream().toList());
        registration.addRecipes(BULK_FERMENTING, map.byType(CDGRecipes.BULK_FERMENTING.getType()).stream().toList());
        registration.addRecipes(COMPRESSION_MOLDING, map.byType(CDGRecipes.COMPRESSION_MOLDING.getType()).stream().toList());
        registration.addRecipes(CASTING, map.byType(CDGRecipes.CASTING.getType()).stream().toList());
        registration.addRecipes(DISTILLATION, map.byType(CDGRecipes.DISTILLATION.getType()).stream().toList());
        registration.addRecipes(HAMMERING, map.byType(CDGRecipes.HAMMERING.getType()).stream().toList());
        registration.addRecipes(WIRE_CUTTING, map.byType(CDGRecipes.WIRE_CUTTING.getType()).stream().toList());
    }
}
