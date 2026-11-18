package com.zurrtum.create.client.compat.jei;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.infrastructure.component.BottleType;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class PotionFluidSubtypeInterpreter implements ISubtypeInterpreter<net.neoforged.neoforge.fluids.FluidStack> {
    public static final PotionFluidSubtypeInterpreter INSTANCE = new PotionFluidSubtypeInterpreter();

    @Override
    public @Nullable Object getSubtypeData(net.neoforged.neoforge.fluids.FluidStack ingredient, UidContext context) {
        DataComponentMap components = ingredient.getComponents();
        if (components.isEmpty()) {
            return null;
        }
        return List.of(
            components.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY),
            components.getOrDefault(AllDataComponents.POTION_FLUID_BOTTLE_TYPE, BottleType.REGULAR)
        );
    }
}
