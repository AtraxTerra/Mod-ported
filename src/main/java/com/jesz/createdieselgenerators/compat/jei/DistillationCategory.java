// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.compat.jei;

import com.jesz.createdieselgenerators.CDGItems;
import com.jesz.createdieselgenerators.client.gui.render.DistillationTowerRenderState;
import com.jesz.createdieselgenerators.content.distillation.DistillationRecipe;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.client.compat.jei.renderer.TwoIconRenderer;
import com.zurrtum.create.client.foundation.gui.AllGuiTextures;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import com.zurrtum.create.foundation.fluid.FluidIngredient;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.joml.Matrix3x2f;

import java.util.List;

/**
 * The distillation tower, one tank more than it has outputs, each output beside the level it is
 * drawn from: the Fabric build's layout, with the same constants, on a fixed three-output height.
 */
public class DistillationCategory extends CDGCategory<DistillationRecipe> {
    private static final int TOWER_X = 88;
    private static final int TOWER_BOTTOM_Y = 170;
    private static final int TOWER_BASE_Y = 147;
    private static final int TOWER_STEP = DistillationTowerRenderState.SCALE;
    private static final int TOWER_TOP_MARGIN = 6;
    private static final int OUTPUT_BASE_Y = 127;
    private static final int OUTPUT_STEP = 23;
    private static final int CONTENT_BOTTOM = 190;
    private static final int MAX_TIERS = 3;
    private static final int MARGIN = 4;
    /** Everything is drawn this much higher than in the Fabric layout, which started off-screen. */
    private static final int DY = top(MAX_TIERS) - MARGIN;

    private static int top(int outputs) {
        int topSlot = OUTPUT_BASE_Y - OUTPUT_STEP * Math.max(outputs - 1, 0);
        int topTier = TOWER_BASE_Y - TOWER_STEP * outputs - TOWER_TOP_MARGIN;
        return Math.min(topSlot, topTier);
    }

    @Override
    public IRecipeType<RecipeHolder<DistillationRecipe>> getRecipeType() {
        return CDGJeiPlugin.DISTILLATION;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("createdieselgenerators.recipe.distillation");
    }

    @Override
    public IDrawable getIcon() {
        return new TwoIconRenderer(AllItems.FLUID_TANK, CDGItems.DISTILLATION_CONTROLLER.asItem());
    }

    @Override
    public int getHeight() {
        return CONTENT_BOTTOM - top(MAX_TIERS) + MARGIN * 2;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<DistillationRecipe> entry, IFocusGroup focuses) {
        DistillationRecipe recipe = entry.value();
        List<FluidIngredient> inputs = recipe.getFluidIngredients();
        if (!inputs.isEmpty()) {
            addFluidSlot(builder, 17, 145 - DY, inputs.getFirst()).setBackground(SLOT, -1, -1);
        }
        int i = 0;
        for (FluidStack fluid : recipe.getFluidResults()) {
            addFluidSlot(builder, 130, OUTPUT_BASE_Y - OUTPUT_STEP * i - DY, fluid).setBackground(SLOT, -1, -1);
            i++;
        }
        for (ProcessingOutput output : recipe.getRollableResults()) {
            addChanceSlot(builder, 130, OUTPUT_BASE_Y - OUTPUT_STEP * i - DY, output);
            i++;
        }
        addHeatSlots(builder, recipe.getRequiredHeat(), 134, 171 - DY);
    }

    @Override
    public void draw(
        RecipeHolder<DistillationRecipe> entry,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphicsExtractor graphics,
        double mouseX,
        double mouseY
    ) {
        DistillationRecipe recipe = entry.value();
        AllGuiTextures.JEI_ARROW.render(graphics, 40, 150 - DY);
        drawHeat(graphics, recipe.getRequiredHeat(), 170 - DY, 91, 164 - DY);
        int tiers = recipe.getFluidResults().size() + recipe.getRollableResults().size() + 1;
        graphics.guiRenderState.addPicturesInPictureState(new DistillationTowerRenderState(
            new Matrix3x2f(graphics.pose()),
            tiers,
            TOWER_X,
            TOWER_BOTTOM_Y - DY - DistillationTowerRenderState.height(tiers)
        ));
    }
}
