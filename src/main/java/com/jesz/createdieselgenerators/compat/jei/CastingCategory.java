// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.compat.jei;

import com.jesz.createdieselgenerators.CDGItems;
import com.jesz.createdieselgenerators.client.gui.render.CastingSpoutRenderState;
import com.jesz.createdieselgenerators.content.molds.CastingRecipe;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.client.compat.jei.renderer.TwoIconRenderer;
import com.zurrtum.create.client.foundation.gui.AllGuiTextures;
import com.zurrtum.create.foundation.fluid.FluidIngredient;
import com.zurrtum.create.foundation.fluid.FluidStackIngredient;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import org.joml.Matrix3x2f;

import java.util.List;

/** A spout pouring into a basin that holds a mold: the fluid under the mold, the cast on the right. */
public class CastingCategory extends CDGCategory<CastingRecipe> {
    @Override
    public IRecipeType<RecipeHolder<CastingRecipe>> getRecipeType() {
        return CDGJeiPlugin.CASTING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("createdieselgenerators.recipe.casting");
    }

    @Override
    public IDrawable getIcon() {
        return new TwoIconRenderer(AllItems.SPOUT, CDGItems.MOLD.asItem());
    }

    @Override
    public int getHeight() {
        return 103;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<CastingRecipe> entry, IFocusGroup focuses) {
        CastingRecipe recipe = entry.value();
        builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 36, 11).setBackground(SLOT, -1, -1)
            .add(CDGJeiPlugin.mold(recipe.mold()));
        List<FluidIngredient> fluids = recipe.getFluidIngredients();
        if (!fluids.isEmpty()) {
            addFluidSlot(builder, 36, 51, fluids.getFirst()).setBackground(SLOT, -1, -1);
        }
        addOutputs(builder, recipe, 51);
    }

    @Override
    public void draw(
        RecipeHolder<CastingRecipe> entry,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphicsExtractor graphics,
        double mouseX,
        double mouseY
    ) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 81, 68);
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 136, 32);
        List<FluidIngredient> fluids = entry.value().getFluidIngredients();
        if (fluids.isEmpty()) {
            return;
        }
        FluidIngredient input = fluids.getFirst();
        List<Fluid> matching = input.getMatchingFluids();
        if (matching.isEmpty()) {
            return;
        }
        DataComponentPatch components = input instanceof FluidStackIngredient stack ? stack.components()
            : DataComponentPatch.EMPTY;
        graphics.guiRenderState.addPicturesInPictureState(new CastingSpoutRenderState(
            new Matrix3x2f(graphics.pose()),
            matching.getFirst(),
            components,
            91,
            -5
        ));
    }
}
