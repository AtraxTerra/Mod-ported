// Adapted from chaevsfe / Create Fly: Steam 'n' Rails (https://github.com/chaevsfe/Create-Steam-n-Rails-Fly)
// Based on original work from Create: Steam 'n' Rails by The Railways Team (https://github.com/Layers-of-Railways/Railway)
// Licensed under LGPL-3.0-only — extracted/adapted with AI assistance.
package com.railwayteam.railways.neoforge.mixin;

import com.railwayteam.railways.content.buffer.headstock.fabric.CopycatHeadstockModelRegistration;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Port: the item side of upstream's model wrappers. Upstream wraps the headstock and buffer item
 * models through Fabric API's {@code modifyItemModelBeforeBake}; NeoForge has no event for unbaked
 * item models, so the item definitions are rewritten as they leave the loader. The model manager
 * hands this same result to model discovery and to baking, so both see the wrapped models.
 */
@Mixin(ClientItemInfoLoader.class)
public abstract class ClientItemInfoLoaderMixin {
    @Inject(method = "scheduleLoad", at = @At("RETURN"), cancellable = true)
    private static void railways$wrapItemModels(
        ResourceManager manager,
        Executor executor,
        CallbackInfoReturnable<CompletableFuture<ClientItemInfoLoader.LoadedClientInfos>> cir
    ) {
        cir.setReturnValue(cir.getReturnValue().thenApply(CopycatHeadstockModelRegistration::wrapItemModels));
    }
}
