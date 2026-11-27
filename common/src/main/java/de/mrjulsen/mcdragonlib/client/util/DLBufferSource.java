package de.mrjulsen.mcdragonlib.client.util;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * NeoForge 26.2 port: the stand-in for 1.21's {@code MultiBufferSource} in DragonLib's block entity
 * renderers. 26.2 renders block entities in two steps (extract, then submit to a
 * {@link SubmitNodeCollector}) and hands out vertex buffers only inside a submitted callback, so
 * renderer code written for immediate buffers records its vertices here, per render type, exactly as
 * it wrote them ({@code addVertex(pose, x, y, z).setColor(...)...}); {@link #submit} then hands each
 * render type's vertices to the collector as custom geometry.
 *
 * <p>Vertices are recorded already transformed by the caller's pose (that is how the immediate API
 * was used), so they are submitted with an identity pose.
 */
public final class DLBufferSource {

    private static final PoseStack IDENTITY = new PoseStack();

    private final Map<RenderType, Recorder> buffers = new LinkedHashMap<>();

    public VertexConsumer getBuffer(RenderType renderType) {
        return buffers.computeIfAbsent(renderType, rt -> new Recorder());
    }

    public boolean isEmpty() {
        return buffers.values().stream().allMatch(Recorder::isEmpty);
    }

    /** Hands every recorded vertex to {@code collector} and starts over. */
    public void submit(SubmitNodeCollector collector) {
        for (Map.Entry<RenderType, Recorder> entry : buffers.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                collector.submitCustomGeometry(IDENTITY, entry.getKey(), entry.getValue().copy());
            }
        }
        buffers.clear();
    }

    /** Records vertices with the elements that were set on each, and replays them into a real buffer. */
    private static final class Recorder implements VertexConsumer, SubmitNodeCollector.CustomGeometryRenderer {
        private static final int COLOR = 1, UV0 = 2, UV1 = 4, UV2 = 8, NORMAL = 16, LINE_WIDTH = 32;
        private static final int FLOATS = 9; // x y z u v nx ny nz lineWidth
        private static final int INTS = 6;   // set-mask color uv1u uv1v uv2u uv2v

        private float[] floats = new float[FLOATS * 32];
        private int[] ints = new int[INTS * 32];
        private int count = 0;

        boolean isEmpty() {
            return count == 0;
        }

        Recorder copy() {
            Recorder copy = new Recorder();
            copy.floats = Arrays.copyOf(floats, count * FLOATS);
            copy.ints = Arrays.copyOf(ints, count * INTS);
            copy.count = count;
            return copy;
        }

        private int f() {
            return (count - 1) * FLOATS;
        }

        private int i() {
            return (count - 1) * INTS;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if ((count + 1) * FLOATS > floats.length) {
                floats = Arrays.copyOf(floats, floats.length * 2);
                ints = Arrays.copyOf(ints, ints.length * 2);
            }
            count++;
            Arrays.fill(floats, f(), f() + FLOATS, 0);
            Arrays.fill(ints, i(), i() + INTS, 0);
            floats[f()] = x;
            floats[f() + 1] = y;
            floats[f() + 2] = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor((a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255));
        }

        @Override
        public VertexConsumer setColor(int color) {
            if (count > 0) {
                ints[i()] |= COLOR;
                ints[i() + 1] = color;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (count > 0) {
                ints[i()] |= UV0;
                floats[f() + 3] = u;
                floats[f() + 4] = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            if (count > 0) {
                ints[i()] |= UV1;
                ints[i() + 2] = u;
                ints[i() + 3] = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (count > 0) {
                ints[i()] |= UV2;
                ints[i() + 4] = u;
                ints[i() + 5] = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (count > 0) {
                ints[i()] |= NORMAL;
                floats[f() + 5] = x;
                floats[f() + 6] = y;
                floats[f() + 7] = z;
            }
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            if (count > 0) {
                ints[i()] |= LINE_WIDTH;
                floats[f() + 8] = width;
            }
            return this;
        }

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer consumer) {
            for (int v = 0; v < count; v++) {
                int fo = v * FLOATS, io = v * INTS, mask = ints[io];
                consumer.addVertex(floats[fo], floats[fo + 1], floats[fo + 2]);
                if ((mask & COLOR) != 0) consumer.setColor(ints[io + 1]);
                if ((mask & UV0) != 0) consumer.setUv(floats[fo + 3], floats[fo + 4]);
                if ((mask & UV1) != 0) consumer.setUv1(ints[io + 2], ints[io + 3]);
                if ((mask & UV2) != 0) consumer.setUv2(ints[io + 4], ints[io + 5]);
                if ((mask & NORMAL) != 0) consumer.setNormal(floats[fo + 5], floats[fo + 6], floats[fo + 7]);
                if ((mask & LINE_WIDTH) != 0) consumer.setLineWidth(floats[fo + 8]);
            }
        }
    }
}
