// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.compat.jei;

import com.google.common.base.Suppliers;
import com.jesz.createdieselgenerators.recipe.CDGProcessingRecipe;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.client.compat.jei.CreateCategory;
import com.zurrtum.create.client.foundation.gui.AllGuiTextures;
import com.zurrtum.create.client.foundation.gui.render.BasinBlazeBurnerRenderState;
import com.zurrtum.create.client.foundation.utility.CreateLang;
import com.zurrtum.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.zurrtum.create.content.processing.recipe.HeatCondition;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import com.zurrtum.create.content.processing.recipe.SizedIngredient;
import com.zurrtum.create.foundation.fluid.FluidIngredient;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.joml.Matrix3x2f;

import java.util.List;
import java.util.function.Supplier;

/**
 * Port: the layouts of the Fabric build's recipe-viewer categories ({@code compat/viewer}, which
 * needs "Create Fly: Recipe Viewer", a Fabric-only library), redone on JEI with Create Fly's own
 * JEI helpers, so they look like Create's categories next to them.
 */
public abstract class CDGCategory<T extends Recipe<?>> extends CreateCategory<RecipeHolder<T>> {
    /** Item and fluid inputs, three per row from the bottom row up, as in Create's basin categories. */
    public static void addInputs(IRecipeLayoutBuilder builder, CDGProcessingRecipe<?> recipe, int x, int y) {
        List<SizedIngredient> ingredients = recipe.getIngredients();
        List<FluidIngredient> fluids = recipe.getFluidIngredients();
        int size = ingredients.size() + fluids.size();
        int xOffset = size < 3 ? x + (3 - size) * 19 / 2 : x;
        int i = 0;
        Supplier<ContextMap> context = Suppliers.memoize(CreateCategory::createIngredientContext);
        for (SizedIngredient ingredient : ingredients) {
            builder.addInputSlot(xOffset + i % 3 * 19, y - i / 3 * 19).setBackground(SLOT, -1, -1)
                .addItemStacks(getStacks(ingredient, context));
            i++;
        }
        for (FluidIngredient fluid : fluids) {
            addFluidSlot(builder, xOffset + i % 3 * 19, y - i / 3 * 19, fluid).setBackground(SLOT, -1, -1);
            i++;
        }
    }

    /** Item then fluid results, two per row from the given row up, the odd last one centred. */
    public static int addOutputs(IRecipeLayoutBuilder builder, CDGProcessingRecipe<?> recipe, int y) {
        List<ProcessingOutput> results = recipe.getRollableResults();
        List<FluidStack> fluids = recipe.getFluidResults();
        int size = results.size() + fluids.size();
        int end = size - 1;
        boolean odd = size % 2 != 0;
        int i = 0;
        for (ProcessingOutput result : results) {
            addChanceSlot(builder, outputX(i, end, odd), y - 19 * (i / 2), result);
            i++;
        }
        for (FluidStack fluid : fluids) {
            addFluidSlot(builder, outputX(i, end, odd), y - 19 * (i / 2), fluid).setBackground(SLOT, -1, -1);
            i++;
        }
        return size;
    }

    private static int outputX(int i, int end, boolean odd) {
        if (odd && i == end) {
            return 142;
        }
        return i % 2 == 0 ? 132 : 151;
    }

    /** The blaze burner and blaze cake a heated recipe asks for. */
    public static void addHeatSlots(IRecipeLayoutBuilder builder, HeatCondition heat, int x, int y) {
        if (!heat.testBlazeBurner(HeatLevel.NONE)) {
            builder.addSlot(RecipeIngredientRole.RENDER_ONLY, x, y).add(AllItems.BLAZE_BURNER);
        }
        if (!heat.testBlazeBurner(HeatLevel.KINDLED)) {
            builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, x + 19, y).add(AllItems.BLAZE_CAKE);
        }
    }

    /** Heat bar and label, and the burner under the machine (or its shadow when unheated). */
    public static void drawHeat(GuiGraphicsExtractor graphics, HeatCondition heat, int barY, int burnerX, int burnerY) {
        if (heat == HeatCondition.NONE) {
            AllGuiTextures.JEI_NO_HEAT_BAR.render(graphics, 4, barY);
            AllGuiTextures.JEI_SHADOW.render(graphics, burnerX - 10, burnerY - 1);
        } else {
            AllGuiTextures.JEI_HEAT_BAR.render(graphics, 4, barY);
            AllGuiTextures.JEI_LIGHT.render(graphics, burnerX - 10, burnerY + 19);
            graphics.guiRenderState.addPicturesInPictureState(new BasinBlazeBurnerRenderState(
                new Matrix3x2f(graphics.pose()),
                burnerX,
                burnerY,
                heat.visualizeAsBlazeBurner()
            ));
        }
        graphics.text(
            Minecraft.getInstance().font,
            CreateLang.translateDirect(heat.getTranslationKey()),
            9,
            barY + 6,
            heat.getColor(),
            false
        );
    }
}
