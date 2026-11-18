// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.compat.jei;

import com.jesz.createdieselgenerators.CDGItems;
import com.jesz.createdieselgenerators.content.molds.CompressionMoldingRecipe;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.client.compat.jei.category.BasinCategory;
import com.zurrtum.create.client.compat.jei.renderer.TwoIconRenderer;
import com.zurrtum.create.client.foundation.gui.render.PressBasinRenderState;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.joml.Matrix3x2f;

/** A mechanical press over a basin holding a mold: the mold shown in its own slot, top left. */
public class CompressionMoldingCategory extends CDGCategory<CompressionMoldingRecipe> {
    @Override
    public IRecipeType<RecipeHolder<CompressionMoldingRecipe>> getRecipeType() {
        return CDGJeiPlugin.COMPRESSION_MOLDING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("createdieselgenerators.recipe.compression_molding");
    }

    @Override
    public IDrawable getIcon() {
        return new TwoIconRenderer(AllItems.MECHANICAL_PRESS, CDGItems.MOLD.asItem());
    }

    @Override
    public int getHeight() {
        return 103;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<CompressionMoldingRecipe> entry, IFocusGroup focuses) {
        CompressionMoldingRecipe recipe = entry.value();
        BasinCategory.addIngredientSlots(builder, recipe);
        int size = recipe.getRollableResults().size() + recipe.getFluidResults().size();
        addOutputs(builder, recipe, size <= 4 ? 51 : 60);
        builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 36, 11).setBackground(SLOT, -1, -1)
            .add(CDGJeiPlugin.mold(recipe.mold()));
        BasinCategory.addHeatSlots(builder, recipe);
    }

    @Override
    public void draw(
        RecipeHolder<CompressionMoldingRecipe> entry,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphicsExtractor graphics,
        double mouseX,
        double mouseY
    ) {
        CompressionMoldingRecipe recipe = entry.value();
        BasinCategory.drawBackground(recipe, graphics, recipe.getRollableResults().size() + recipe.getFluidResults().size());
        graphics.guiRenderState.addPicturesInPictureState(new PressBasinRenderState(
            new Matrix3x2f(graphics.pose()),
            91,
            -5
        ));
    }
}
