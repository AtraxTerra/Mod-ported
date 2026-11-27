// Adapted from chaevsfe / Create Fly: Steam 'n' Rails (https://github.com/chaevsfe/Create-Steam-n-Rails-Fly)
// Based on original work from Create: Steam 'n' Rails by The Railways Team (https://github.com/Layers-of-Railways/Railway)
// Licensed under LGPL-3.0-only — extracted/adapted with AI assistance.
package com.railwayteam.railways.neoforge.mixin;

import net.neoforged.neoforge.client.model.obj.ObjMaterialLibrary;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port: NeoForge's OBJ loader bakes every mesh of a model with its material's diffuse texture
 * ({@code map_Kd}) and throws when a material has none: {@code TextureSlots} gets a null reference
 * and the whole model fails to bake. Twenty-nine of Steam 'n' Rails' OBJ models (bogey frames,
 * buffers...) keep a few faces on Blender's untextured {@code none} material. A mesh without a
 * texture is skipped here, as NeoForge already skips a mesh without a material.
 */
@Mixin(targets = "net.neoforged.neoforge.client.model.obj.ObjGeometry$ModelMesh")
public abstract class ObjModelMeshMixin {
    @Shadow
    public ObjMaterialLibrary.@Nullable Material mat;

    @Inject(method = "addQuads", at = @At("HEAD"), cancellable = true)
    private void railways$skipUntexturedMesh(CallbackInfo ci) {
        if (mat != null && mat.diffuseColorMap == null)
            ci.cancel();
    }
}
