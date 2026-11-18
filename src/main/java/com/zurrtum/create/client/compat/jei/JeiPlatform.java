package com.zurrtum.create.client.compat.jei;

import com.zurrtum.create.foundation.recipe.ComponentsIngredient;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import com.zurrtum.create.platform.FluidUnits;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.jspecify.annotations.Nullable;

/**
 * Port-only: what the JEI integration took from Fabric, on NeoForge.
 *
 * <ul>
 *   <li>JEI's fluid ingredient is NeoForge's {@code FluidStack} ({@code NeoForgeTypes.FLUID_STACK}),
 *   in <b>millibuckets</b>; Create's own fluid stacks are in droplets (81 per mB), so every amount
 *   crossing into or out of JEI goes through {@link #toJei} / {@link #fromJei}.</li>
 *   <li>Fabric's {@code FabricIngredient.getCustomIngredient()} is NeoForge's
 *   {@code Ingredient.getCustomIngredient()}; Create's {@link ComponentsIngredient} is custom too.</li>
 *   <li>Fabric's {@code DefaultCustomIngredients.components(stack)} is NeoForge's
 *   {@link DataComponentIngredient}.</li>
 * </ul>
 */
public final class JeiPlatform {
    private JeiPlatform() {
    }

    /** The stacks a custom ingredient displays, or {@code null} for a plain list of items. */
    public static @Nullable SlotDisplay customDisplay(Ingredient ingredient) {
        if (ingredient.isCustom()) {
            return ingredient.getCustomIngredient().display();
        }
        if (ingredient instanceof ComponentsIngredient) {
            return ingredient.display();
        }
        return null;
    }

    public static boolean isCustom(Ingredient ingredient) {
        return ingredient.isCustom() || ingredient instanceof ComponentsIngredient;
    }

    /** An ingredient matching this stack's item and components (for display recipes). */
    public static Ingredient components(ItemStack stack) {
        return DataComponentIngredient.of(false, stack);
    }

    /** Droplets -> the millibuckets JEI shows; never rounds a non-empty amount to nothing. */
    public static long toJei(long droplets) {
        long mb = FluidUnits.toMillibuckets(droplets);
        return mb == 0 && droplets > 0 ? 1 : mb;
    }

    /** JEI's (NeoForge, millibucket) fluid stack -> Create's. */
    public static FluidStack fromJei(net.neoforged.neoforge.fluids.FluidStack stack) {
        return new FluidStack(stack.getFluid(), FluidUnits.toDroplets(stack.getAmount()), stack.getComponentsPatch());
    }
}
