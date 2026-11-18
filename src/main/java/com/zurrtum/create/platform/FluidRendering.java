package com.zurrtum.create.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/**
 * Client-side fluid tint colour.
 *
 * <h2>Unresolved: 26.2 moved this, and the replacement has not been located</h2>
 * Upstream asks Fabric's {@code FluidVariantRendering.getColor(FluidVariant.of(fluid, components),
 * level, pos)} in three places in {@code AllFluidConfigs}.
 *
 * <p>The obvious NeoForge counterpart would be the fluid type's client extension, but
 * {@code IClientFluidTypeExtensions} in NeoForge 26.2 has <b>no tint or colour method at all</b> --
 * it covers the overlay texture, fog colour and fog rendering, and nothing else. Fluid colour moved
 * somewhere in the 26.x render rework and the new home has not been found.
 *
 * <p>So this returns opaque white, which is the identity for a multiplicative tint: fluids render
 * in their texture's own colours rather than crashing or rendering black. For Create's own fluids
 * that is close to correct, since their textures carry their colour. For a fluid that relies on a
 * tint -- water per biome, a modded fluid coloured by code -- it is visibly wrong.
 *
 * <p>TODO(port): find where 26.2 exposes a fluid's render colour and implement this. Three places
 * in {@code AllFluidConfigs} consume it; nothing else does. Starting points: how vanilla tints
 * water per biome in 26.2, and whether {@code FluidResource} or the fluid's client config carries
 * a colour now that the transfer API deals in resources rather than stacks.
 */
public final class FluidRendering {
    /** Opaque white: the identity value for a multiplicative tint. */
    private static final int NO_TINT = 0xFFFFFFFF;

    private FluidRendering() {
    }

    public static int getColor(
        Fluid fluid,
        @Nullable DataComponentPatch components,
        @Nullable BlockGetter level,
        @Nullable BlockPos pos
    ) {
        return NO_TINT;
    }
}
