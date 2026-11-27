package de.mrjulsen.mcdragonlib.client.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import de.mrjulsen.mcdragonlib.client.util.DLGraphics;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;

/** NeoForge 26.2 port: built in {@link SafeBlockEntityRenderer#submit} around the frame's collector. */
public class BERGraphics<B extends BlockEntity> extends DLGraphics { 

    protected final B blockEntity;
    protected final BlockEntityRendererProvider.Context berProviderContext;

    public BERGraphics(B blockEntity, PoseStack poseStack, SubmitNodeCollector collector, BlockEntityRendererProvider.Context berProviderContext, int packedLight, int packedOverlay, float partialTick) {
        super(poseStack, collector, packedLight, packedOverlay, partialTick);
        this.blockEntity = blockEntity;
        this.berProviderContext = berProviderContext;
    }

    public B blockEntity() {
        return blockEntity;
    }

    public BlockEntityRendererProvider.Context BERProviderContext() {
        return berProviderContext;
    }

    @Override
    public VertexConsumer vertexConsumer(Identifier textureLocation) {
        return multiBufferSource.getBuffer(RenderTypes.text(textureLocation));
    }
}
