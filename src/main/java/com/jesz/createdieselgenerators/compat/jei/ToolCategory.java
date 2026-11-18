// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.compat.jei;

import com.jesz.createdieselgenerators.CDGItems;
import com.jesz.createdieselgenerators.content.tools.hammer.HammerRecipe;
import com.jesz.createdieselgenerators.content.tools.wire_cutters.WireCuttingRecipe;
import com.zurrtum.create.client.compat.jei.CreateCategory;
import com.zurrtum.create.client.compat.jei.renderer.IconRenderer;
import com.zurrtum.create.client.foundation.gui.AllGuiTextures;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.function.Function;

/** A hand tool used on one item (hammer, wire cutters): the item, the tool over the arrow, the results. */
public class ToolCategory<T extends Recipe<?>> extends CreateCategory<RecipeHolder<T>> {
    private final IRecipeType<RecipeHolder<T>> type;
    private final String title;
    private final Item tool;
    private final Function<T, Ingredient> ingredient;
    private final Function<T, List<ProcessingOutput>> results;

    private ToolCategory(
        IRecipeType<RecipeHolder<T>> type,
        String title,
        Item tool,
        Function<T, Ingredient> ingredient,
        Function<T, List<ProcessingOutput>> results
    ) {
        this.type = type;
        this.title = title;
        this.tool = tool;
        this.ingredient = ingredient;
        this.results = results;
    }

    public static ToolCategory<HammerRecipe> hammering() {
        return new ToolCategory<>(
            CDGJeiPlugin.HAMMERING,
            "createdieselgenerators.recipe.hammering",
            CDGItems.HAMMER.asItem(),
            HammerRecipe::ingredient,
            HammerRecipe::results
        );
    }

    public static ToolCategory<WireCuttingRecipe> wireCutting() {
        return new ToolCategory<>(
            CDGJeiPlugin.WIRE_CUTTING,
            "createdieselgenerators.recipe.wire_cutting",
            CDGItems.WIRE_CUTTERS.asItem(),
            WireCuttingRecipe::ingredient,
            WireCuttingRecipe::results
        );
    }

    @Override
    public IRecipeType<RecipeHolder<T>> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(title);
    }

    @Override
    public IDrawable getIcon() {
        return new IconRenderer(tool);
    }

    @Override
    public int getHeight() {
        return 55;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<T> entry, IFocusGroup focuses) {
        T recipe = entry.value();
        builder.addInputSlot(27, 29).setBackground(SLOT, -1, -1).add(ingredient.apply(recipe));
        builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 72, 8).add(tool);
        List<ProcessingOutput> outputs = results.apply(recipe);
        for (int i = 0; i < outputs.size(); i++) {
            addChanceSlot(builder, 132 + i % 2 * 19, 29 - i / 2 * 19, outputs.get(i));
        }
    }

    @Override
    public void draw(
        RecipeHolder<T> entry,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphicsExtractor graphics,
        double mouseX,
        double mouseY
    ) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 61, 21);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 52, 32);
    }
}
