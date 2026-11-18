package com.zurrtum.create.client.mixin;

import com.zurrtum.create.client.flywheel.lib.model.baked.VanillinMeshEmitterManager;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port-only replacement for upstream's {@code ModelBlockRendererMixin} (excluded in build.gradle).
 *
 * <p>Upstream, on Fabric without the renderer API, {@code @Overwrite}s {@code tesselateBlock} and
 * {@code tesselateFlat} to get four things: its own enhanced AO ({@code ModelLighter}), per-position
 * parts for {@code WrapperBlockStateModel}s, copycat light emission, and a call telling Vanillin's
 * mesh emitter whether the block is drawn with AO. NeoForge patches both methods heavily -- level-aware
 * {@code collectParts}, per-part AO, contextual face culling, dynamic tints -- so an overwrite would
 * remove those for every block in the game, other mods' included. Three of the four exist natively:
 * <ul>
 *   <li>enhanced AO: NeoForge's {@code EnhancedBlockModelLighter} (the same idea as Indigo's);</li>
 *   <li>parts: {@code WrapperBlockStateModel} overrides NeoForge's level-aware {@code collectParts};</li>
 *   <li>copycat emission: {@code CopycatBlock} overrides {@code getLightEmission(state, level, pos)},
 *       which NeoForge's AO decision calls.</li>
 * </ul>
 * This mixin supplies the fourth, without replacing anything: just before NeoForge's tesselateBlock
 * dispatches to the AO or flat path, Vanillin's emitter is told which one.
 *
 * <p>Not carried over: upstream's flat path computed light from the copycat's MATERIAL state; here it
 * uses the copycat block's own state, as vanilla does. Emission is unaffected (see above); only a
 * material with emissive rendering (magma, for instance) would look different.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class VanillinModelLayerMixin {
    @Inject(method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateAmbientOcclusion(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLjava/util/List;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V"))
    private void create$beforeAmbientOcclusion(
        BlockQuadOutput output,
        float x,
        float y,
        float z,
        BlockAndTintGetter level,
        BlockPos pos,
        BlockState blockState,
        BlockStateModel model,
        long seed,
        CallbackInfo ci
    ) {
        if (output instanceof VanillinMeshEmitterManager meshEmitter) {
            meshEmitter.prepareForModelLayer(true);
        }
    }

    @Inject(method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateFlat(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLjava/util/List;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V"))
    private void create$beforeFlat(
        BlockQuadOutput output,
        float x,
        float y,
        float z,
        BlockAndTintGetter level,
        BlockPos pos,
        BlockState blockState,
        BlockStateModel model,
        long seed,
        CallbackInfo ci
    ) {
        if (output instanceof VanillinMeshEmitterManager meshEmitter) {
            meshEmitter.prepareForModelLayer(false);
        }
    }
}
