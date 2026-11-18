// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.compat.jei;

import com.jesz.createdieselgenerators.CDGBlocks;
import com.jesz.createdieselgenerators.client.gui.render.BasinFermentingRenderState;
import com.jesz.createdieselgenerators.content.basin_lid.BasinFermentingRecipe;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.client.compat.jei.category.BasinCategory;
import com.zurrtum.create.client.compat.jei.renderer.TwoIconRenderer;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.joml.Matrix3x2f;

/** A basin under a basin lid: Create's basin layout, with the lid drawn over the basin. */
public class BasinFermentingCategory extends CDGCategory<BasinFermentingRecipe> {
    @Override
    public IRecipeType<RecipeHolder<BasinFermentingRecipe>> getRecipeType() {
        return CDGJeiPlugin.BASIN_FERMENTING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("createdieselgenerators.recipe.basin_fermenting");
    }

    @Override
    public IDrawable getIcon() {
        return new TwoIconRenderer(AllItems.BASIN, CDGBlocks.BASIN_LID.asItem());
    }

    @Override
    public int getHeight() {
        return 103;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<BasinFermentingRecipe> entry, IFocusGroup focuses) {
        BasinFermentingRecipe recipe = entry.value();
        BasinCategory.addIngredientSlots(builder, recipe);
        addOutputs(builder, recipe, outputRow(recipe));
        BasinCategory.addHeatSlots(builder, recipe);
    }

    static int outputRow(BasinFermentingRecipe recipe) {
        return recipe.getRollableResults().size() + recipe.getFluidResults().size() <= 4 ? 51 : 60;
    }

    @Override
    public void draw(
        RecipeHolder<BasinFermentingRecipe> entry,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphicsExtractor graphics,
        double mouseX,
        double mouseY
    ) {
        BasinFermentingRecipe recipe = entry.value();
        BasinCategory.drawBackground(recipe, graphics, recipe.getRollableResults().size() + recipe.getFluidResults().size());
        graphics.guiRenderState.addPicturesInPictureState(new BasinFermentingRenderState(
            new Matrix3x2f(graphics.pose()),
            91,
            -5
        ));
    }
}
