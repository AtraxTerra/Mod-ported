package com.zurrtum.create.client.mixin;

import java.util.function.Predicate;
import net.neoforged.neoforge.fluids.FluidType;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.infrastructure.fluids.FlowableFluid;
import com.zurrtum.create.infrastructure.fluids.FluidInteractionPredicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Port note: NeoForge turns update(Entity, boolean) into a delegate to update(Entity,
// Predicate<FluidType>), which now holds the loop; both injectors target that overload. Its HEAD
// is reached from either entry point, so the reset still happens once per update.
@Mixin(EntityFluidInteraction.class)
public class EntityFluidInteractionMixin implements FluidInteractionPredicate {
    @Unique
    private boolean inModFluid;

    @Inject(method = "update(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)V", at = @At("HEAD"))
    private void clear(Entity entity, Predicate<FluidType> typePushPredicate, CallbackInfo ci) {
        inModFluid = false;
    }

    @Inject(method = "update(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;getY()I"))
    private void update(Entity entity, Predicate<FluidType> typePushPredicate, CallbackInfo ci, @Local FluidState fluidState) {
        if (!inModFluid) {
            inModFluid = fluidState.getType() instanceof FlowableFluid;
        }
    }

    @Override
    public boolean create$inModFluid() {
        return inModFluid;
    }
}
