// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.compat.jei;

import com.jesz.createdieselgenerators.CDGBlocks;
import com.jesz.createdieselgenerators.content.bulk_fermenter.BulkFermentingRecipe;
import com.zurrtum.create.client.compat.jei.renderer.IconRenderer;
import com.zurrtum.create.client.foundation.gui.AllGuiTextures;
import com.zurrtum.create.client.foundation.gui.render.ManualBlockRenderState;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.joml.Matrix3x2f;

/** The bulk fermenter tank, over a blaze burner when heated: inputs on the left, results on the right. */
public class BulkFermentingCategory extends CDGCategory<BulkFermentingRecipe> {
    @Override
    public IRecipeType<RecipeHolder<BulkFermentingRecipe>> getRecipeType() {
        return CDGJeiPlugin.BULK_FERMENTING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("createdieselgenerators.recipe.bulk_fermenting");
    }

    @Override
    public IDrawable getIcon() {
        return new IconRenderer(CDGBlocks.BULK_FERMENTER.asItem());
    }

    @Override
    public int getHeight() {
        return 103;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<BulkFermentingRecipe> entry, IFocusGroup focuses) {
        BulkFermentingRecipe recipe = entry.value();
        addInputs(builder, recipe, 12, 51);
        addOutputs(builder, recipe, 51);
        addHeatSlots(builder, recipe.getRequiredHeat(), 134, 81);
    }

    @Override
    public void draw(
        RecipeHolder<BulkFermentingRecipe> entry,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphicsExtractor graphics,
        double mouseX,
        double mouseY
    ) {
        BulkFermentingRecipe recipe = entry.value();
        int size = recipe.getRollableResults().size() + recipe.getFluidResults().size();
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 136, 32 - (size - 1) / 2 * 19);
        drawHeat(graphics, recipe.getRequiredHeat(), 80, 91, 69);
        graphics.guiRenderState.addPicturesInPictureState(new ManualBlockRenderState(
            new Matrix3x2f(graphics.pose()),
            CDGBlocks.BULK_FERMENTER.getDefaultState(),
            80,
            38
        ));
    }
}
