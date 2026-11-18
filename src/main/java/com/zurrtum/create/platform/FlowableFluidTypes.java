package com.zurrtum.create.platform;

import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.fluids.FlowableFluid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Port-only: a NeoForge {@link FluidType} for every {@link FlowableFluid}, addons' fluids included.
 *
 * <p>NeoForge asks a fluid for its type wherever the fluid meets the game: every entity touching it,
 * each tick ({@code EntityFluidInteraction}), a bucket being emptied ({@code BucketItem}), a fluid
 * stack's name ({@code FluidStack.getHoverName}, which is what JEI shows). The default
 * {@code Fluid.getFluidType()} only knows vanilla's fluids and throws "Mod fluids must override
 * getFluidType" for any other. Fabric has no fluid types, so upstream's fluids have none: here an
 * entity walking into placed honey or chocolate stopped the server, their buckets could not be
 * emptied, and JEI dropped Create's fluids, and Diesel Generators' (whose fluids are
 * {@code FlowableFluid}s too), from its list as broken ingredients.
 *
 * <p>Each family, still and flowing, gets one type, registered under the still fluid's id and named
 * by the lang key the fluid already has ({@code fluid.<namespace>.<path>}), with NeoForge's default
 * properties and the water bucket sounds. They are made when NeoForge registers fluid types, which is
 * after Create and its addons have registered their fluids (from {@code Fluids}' static initializer,
 * {@code FluidsMixin}), and {@link FlowableFluid#getFluidType()} returns them. See PORTING.md 1i.
 */
public final class FlowableFluidTypes {
    private static final Map<Fluid, FluidType> TYPES = new IdentityHashMap<>();
    private static boolean warned;

    private FlowableFluidTypes() {
    }

    public static void register(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.FLUID_TYPES, helper -> {
            for (Fluid fluid : BuiltInRegistries.FLUID) {
                if (!(fluid instanceof FlowableFluid flowable) || TYPES.containsKey(fluid)) {
                    continue;
                }
                Fluid source = flowable.getSource();
                FluidType type = TYPES.get(source);
                if (type == null) {
                    // Never a type named after a fluid of vanilla's (a FlowableFluid is a WaterFluid,
                    // whose source is water unless overridden) or after one left unregistered.
                    Optional<ResourceKey<Fluid>> key = source instanceof FlowableFluid
                        ? BuiltInRegistries.FLUID.getResourceKey(source) : Optional.empty();
                    if (key.isEmpty()) {
                        continue;
                    }
                    Identifier id = key.get().identifier();
                    type = new FluidType(FluidType.Properties.create()
                        .descriptionId("fluid." + id.getNamespace() + "." + id.getPath())
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
                    helper.register(id, type);
                    TYPES.put(source, type);
                }
                TYPES.put(fluid, type);
            }
        });
    }

    /** The type made for this fluid's family; water's, with one warning, for a fluid made too late. */
    public static FluidType get(Fluid fluid) {
        FluidType type = TYPES.get(fluid);
        if (type != null) {
            return type;
        }
        if (!warned) {
            warned = true;
            Create.LOGGER.warn("{} has no fluid type: it was not in the fluid registry when fluid types were registered; using water's",
                BuiltInRegistries.FLUID.getKey(fluid));
        }
        return NeoForgeMod.WATER_TYPE.value();
    }
}
