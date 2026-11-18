package com.zurrtum.create.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.jspecify.annotations.Nullable;

/**
 * Client-side fluid appearance, behind one call so the loader-specific part stays in one file.
 *
 * <p>Upstream asks Fabric's {@code FluidVariantRendering.getColor(FluidVariant.of(fluid,
 * components), level, pos)} in three places. NeoForge's equivalent is the fluid type's client
 * extension.
 *
 * <p><b>Behavioural difference worth knowing:</b> a Fabric {@code FluidVariant} carries the
 * fluid's data components, so a Fabric fluid-colour provider can tint per-component -- a potion
 * fluid coloured by its effects, for instance. NeoForge's {@code getTintColor} takes a
 * {@code FluidState} and no components, so the tint is per-fluid only. Create's own fluids do not
 * tint by component, so this is lossless for Create itself; a <i>foreign</i> component-tinted fluid
 * flowing through a Create pipe will render in its base colour rather than its component colour.
 * The {@code components} parameter is kept in the signature so the call sites do not change and so
 * the information is still here if a future NeoForge API can use it.
 */
public final class FluidRendering {
    private FluidRendering() {
    }

    public static int getColor(
        Fluid fluid,
        @Nullable DataComponentPatch components,
        @Nullable BlockAndTintGetter level,
        @Nullable BlockPos pos
    ) {
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        return extensions.getTintColor(fluid.defaultFluidState(), level, pos);
    }
}
