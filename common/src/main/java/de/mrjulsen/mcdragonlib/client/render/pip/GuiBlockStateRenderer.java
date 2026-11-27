package de.mrjulsen.mcdragonlib.client.render.pip;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/**
 * NeoForge 26.2 port: draws a {@link GuiBlockStateRenderState} into its own texture. Inside a picture
 * renderer the pose works like the 1.21 GUI pose (y down, the base class scales by {@code scale} with z
 * negated), with the origin moved to the box centre; the 1.21 code then applied
 * {@code scale(-s, -s, -1)} and the Y rotation, which this renderer completes from the base scale.
 */
public class GuiBlockStateRenderer extends PictureInPictureRenderer<GuiBlockStateRenderState> {

    private final RandomSource random = RandomSource.create(42L);
    private final List<BlockStateModelPart> parts = new ArrayList<>();

    @Override
    public Class<GuiBlockStateRenderState> getRenderStateClass() {
        return GuiBlockStateRenderState.class;
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected void renderToTexture(GuiBlockStateRenderState renderState, PoseStack poseStack, SubmitNodeCollector collector) {
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_FLAT);
        // base: scale(s, s, -s); 1.21: scale(-s, -s, -1) then rotY  =>  here: scale(-1, -1, 1/s) then rotY
        poseStack.scale(-1.0F, -1.0F, 1.0F / Math.max(renderState.scale(), 1.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(renderState.yRot()));
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(renderState.state());
        parts.clear();
        random.setSeed(42L);
        model.collectParts(random, parts);
        collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.copyOf(parts), new int[0], LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
    }

    @Override
    protected String getTextureLabel() {
        return "dragonlib block state";
    }
}
