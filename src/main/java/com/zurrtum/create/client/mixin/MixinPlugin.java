package com.zurrtum.create.client.mixin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Selects which client mixins to apply.
 *
 * <h2>The important consequence of the port</h2>
 * Upstream branches on {@code fabric-renderer-api-v1}. With FRAPI present it applies a set of
 * mixins that render Create's geometry through Fabric's renderer API; with FRAPI absent it applies a
 * <i>different, already-written</i> set that renders through vanilla. On NeoForge only the second
 * set can apply -- which is the whole reason the client side of this port is tractable: Create Fly
 * already ships a vanilla rendering path, so there is no Fabric renderer API to reimplement.
 *
 * <p>The FRAPI-only mixins and their supporting classes are therefore dead on NeoForge. They are
 * excluded from compilation in {@code build.gradle} rather than deleted, so that a future
 * multiloader setup can switch them back on. See PORTING.md.
 *
 * <p>Applied here, all from upstream's no-FRAPI branch:
 * {@code CuboidModelElementDeserializerMixin}, {@code CuboidModelElementMixin},
 * {@code UnbakedCuboidGeometryMixin}, {@code BakedQuadMixin}, {@code VertexConsumerMixin},
 * {@code ModelBlockRendererMixin}, {@code TerrainParticleMixin}, plus
 * {@code CreativeModeInventoryScreenMixin} (no {@code fabric-creative-tab-api-v1}) and
 * {@code LoadBlockModelMixin} (no {@code fabric-model-loading-api-v1}).
 *
 * <p>Dropped: {@code ConnectedTextureBehaviourMixin} (patches {@code fabric-block-api-v1}) and the
 * {@code rrv} recipe-viewer mixins (Fabric-only mod).
 */
public class MixinPlugin implements IMixinConfigPlugin {
    private List<String> mixins;

    @Override
    public void onLoad(String mixinPackage) {
        // A config plugin's getMixins() list is applied on EVERY side -- unlike the config's own
        // "client" array, which Mixin only applies on the client. On Fabric that never mattered:
        // fabric.mod.json loads the whole client config on the client only. NeoForge has no such
        // per-config environment, so this plugin runs on the dedicated server too, and the first
        // boot logged ten "Attempted to load class ... which is not present on the dedicated server"
        // errors -- one per mixin below, each targeting a client-only renderer class.
        //
        // FMLEnvironment's dist is fixed before any mixin config loads (it is the first thing FML
        // logs), so it is safe to read here, unlike ModList.
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            mixins = List.of();
            return;
        }
        mixins = new ArrayList<>(List.of(
            // Upstream's "no Fabric renderer API" branch -- the vanilla rendering path.
            "CuboidModelElementDeserializerMixin",
            "CuboidModelElementMixin",
            "UnbakedCuboidGeometryMixin",
            "BakedQuadMixin",
            "VertexConsumerMixin",
            // Upstream's ModelBlockRendererMixin @Overwrites tesselateBlock, which would erase
            // NeoForge's renderer patches; this port-only mixin keeps only the Vanillin call it needs.
            "VanillinModelLayerMixin",
            "TerrainParticleMixin",
            // No fabric-creative-tab-api-v1 / fabric-model-loading-api-v1 on this loader.
            "CreativeModeInventoryScreenMixin",
            "LoadBlockModelMixin"
        ));

        // Upstream also adds QuadRenderHelperMixin / IrisPipelinesMixin when Fabric Sodium or Iris
        // is present. Both target those mods' Fabric internals and are excluded from compilation
        // here, so they are not referenced: naming a class that is not in the jar would fail at
        // mixin PREPARE with a ClassNotFoundException, which is fatal.
        //
        // TODO(port): to support a shader mod on NeoForge, rewrite those two mixins against
        // whichever one is actually targeted, drop the exclusion in build.gradle, and gate them
        // with EarlyModDetection.isPresent(<its id>) here -- never with ModList, which does not
        // exist yet at this point in startup.
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
