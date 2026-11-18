package com.zurrtum.create.client.platform;

import com.zurrtum.create.Create;
import com.zurrtum.create.client.catnip.gui.render.*;
import com.zurrtum.create.client.foundation.gui.render.*;
import com.zurrtum.create.client.ponder.foundation.render.SceneRenderState;
import com.zurrtum.create.client.ponder.foundation.render.SceneRenderer;
import com.zurrtum.create.client.ponder.foundation.render.TitleTextRenderState;
import com.zurrtum.create.client.ponder.foundation.render.TitleTextRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

/**
 * Port-only: Create's picture-in-picture GUI renderers, registered the NeoForge way.
 *
 * <p>Upstream adds them from {@code GuiRendererMixin}, wrapping the {@code ImmutableMap.builder()}
 * call in {@code GuiRenderer}'s constructor. NeoForge rewrites that constructor -- it takes a list of
 * {@code PictureInPictureRendererRegistration}s and builds per-state renderer pools -- so the
 * builder call is gone and the wrap had no target. This is the same list, in the same order, on
 * {@link RegisterPictureInPictureRenderersEvent} (mod bus, client only).
 *
 * <p>NeoForge takes a factory rather than an instance: it may create several renderers per state
 * class (one per state drawn in a frame), which also fixes vanilla's "last state wins" reuse bug.
 * When upstream adds a renderer to its mixin, add it here; the upstream-sync PR will show the change
 * in {@code GuiRendererMixin}, which is kept in the tree for exactly that reason but not applied.
 */
@EventBusSubscriber(modid = Create.MOD_ID, value = Dist.CLIENT)
public final class CreatePictureInPictureRenderers {
    private CreatePictureInPictureRenderers() {
    }

    @SubscribeEvent
    public static void register(RegisterPictureInPictureRenderersEvent event) {
        event.register(ItemTransformRenderState.class, ItemTransformElementRenderer::new);
        event.register(BlockTransformRenderState.class, BlockTransformElementRenderer::new);
        event.register(EntityBlockRenderState.class, EntityBlockRenderer::new);
        event.register(PartialRenderState.class, PartialElementRenderer::new);
        event.register(BlazeBurnerRenderState.class, BlazeBurnerElementRenderer::new);
        event.register(PressBasinRenderState.class, PressBasinRenderer::new);
        event.register(PressRenderState.class, PressRenderer::new);
        event.register(MixingBasinRenderState.class, MixingBasinRenderer::new);
        event.register(BasinBlazeBurnerRenderState.class, BasinBlazeBurnerRenderer::new);
        event.register(MillstoneRenderState.class, MillstoneRenderer::new);
        event.register(SawRenderState.class, SawRenderer::new);
        event.register(CrushWheelRenderState.class, CrushWheelRenderer::new);
        event.register(DeployerRenderState.class, DeployerRenderer::new);
        event.register(ManualBlockRenderState.class, ManualBlockRenderer::new);
        event.register(SpoutRenderState.class, SpoutRenderer::new);
        event.register(CrafterRenderState.class, CrafterRenderer::new);
        event.register(DrainRenderState.class, DrainRenderer::new);
        event.register(SandPaperRenderState.class, SandPaperRenderer::new);
        event.register(TitleTextRenderState.class, TitleTextRenderer::new);
        event.register(SceneRenderState.class, SceneRenderer::new);
        event.register(FanRenderState.class, FanRenderer::new);
    }
}
