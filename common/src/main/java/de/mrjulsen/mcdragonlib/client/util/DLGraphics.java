package de.mrjulsen.mcdragonlib.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * What DragonLib's in-world renderers draw with.
 *
 * <p>NeoForge 26.2 port: block entities are submitted to a {@link SubmitNodeCollector} now. Renderer
 * code keeps writing vertices through {@link #multiBufferSource()} (a {@link DLBufferSource}), and
 * whoever created this object calls {@link #flush()} once the renderer is done, which hands the
 * recorded geometry to the collector.
 */
public class DLGraphics { 

    protected final PoseStack poseStack;
    protected final SubmitNodeCollector collector;
    protected final DLBufferSource multiBufferSource = new DLBufferSource();
    protected final int packedLight;
    protected final int packedOverlay;
    protected final float partialTick;

    public DLGraphics(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, int packedOverlay, float partialTick) {
        this.poseStack = poseStack;
        this.collector = collector;
        this.packedLight = packedLight;
        this.packedOverlay = packedOverlay;
        this.partialTick = partialTick;
    }

    public PoseStack poseStack() {
        return poseStack;
    }

    /** The collector this frame's geometry is submitted to. */
    public SubmitNodeCollector collector() {
        return collector;
    }

    public DLBufferSource multiBufferSource() {
        return multiBufferSource;
    }

    public int packedLight() {
        return packedLight;
    }

    public int packedOverlay() {
        return packedOverlay;
    }

    public float partialTick() {
        return partialTick;
    }

    public VertexConsumer vertexConsumer(Identifier textureLocation) {
        return multiBufferSource.getBuffer(RenderTypes.text(textureLocation));
    }

    /** Submits everything written to {@link #multiBufferSource()} so far. */
    public void flush() {
        multiBufferSource.submit(collector);
    }
}
