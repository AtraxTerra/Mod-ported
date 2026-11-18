package com.zurrtum.create.mixin;

import com.zurrtum.create.Create;
import com.zurrtum.create.content.kinetics.fan.processing.FanProcessingTypeRegistry;
import com.zurrtum.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltInRegistries.class)
public class BuiltInRegistriesMixin {
    /**
     * Create's content is registered here, piggybacking on vanilla's own registry bootstrap rather
     * than on a loader hook -- which is a large part of why this mod ports between loaders at all.
     *
     * <p>Upstream this call is guarded by {@code !isModLoaded("fabric-api")}, because with Fabric API
     * present registration is deferred instead ({@code Create.Lazy}). On NeoForge that condition is
     * always true and the guard is removed deliberately: asking {@code ModList} anything this early
     * is unsafe, since vanilla's bootstrap can run before the mod list is built, and a lookup there
     * would fail during registry freeze.
     *
     * <p>TODO(port, needs a run to confirm): verify {@code freeze()} is reached after Create's
     * classes are loadable on NeoForge. If registration turns out to run too early, move this call
     * to {@code FMLConstructModEvent} in {@code Create}'s constructor and leave only the TAIL
     * injection below.
     */
    @Inject(method = "freeze()V", at = @At("HEAD"))
    private static void onInitialize(CallbackInfo ci) {
        Create.register();
        // Port: ids of the original Create that Create Fly renamed (worlds from Create: Adapted).
        com.zurrtum.create.platform.LegacyCreateIds.register();
        // Port: addons' Fabric `main` entrypoints, at the same moment (see AddonEntrypoint).
        com.zurrtum.create.platform.AddonEntrypoint.initialize();
    }

    @Inject(method = "freeze()V", at = @At("TAIL"))
    private static void afterFreeze(CallbackInfo ci) {
        ArmInteractionPointType.register();
        FanProcessingTypeRegistry.register();
    }
}
