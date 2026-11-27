package de.mrjulsen.mcdragonlib.client.util;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * NeoForge 26.2 port: the GUI no longer hands out vertex buffers to draw into. Code that built its own
 * GUI geometry (polygons, gradients, highlight quads) writes it here instead, with the same
 * {@code addVertex(matrix, x, y, z).setColor(...)} calls it made on a {@code VertexConsumer}; the recorded
 * quads are then submitted to the frame's GUI render state as one element, in draw order.
 *
 * <p>Positions are recorded as given, so callers keep transforming them with
 * {@code graphics.poseStack().last().pose()} like they did for the immediate-mode buffer. Vertices are
 * grouped in fours (quads); a trailing incomplete quad is completed by repeating its last vertex.
 */
public final class GuiVertexRecorder implements VertexConsumer {

    private static final int STRIDE = 4; // x, y, u, v (colors live in their own int array)

    private final GuiGraphicsExtractor graphics;
    private final RenderPipeline pipeline;
    private final TextureSetup textureSetup;
    private final boolean textured;
    private float[] data = new float[STRIDE * 16];
    private int[] colors = new int[16];
    private int vertices = 0;

    private GuiVertexRecorder(GuiGraphicsExtractor graphics, RenderPipeline pipeline, TextureSetup textureSetup, boolean textured) {
        this.graphics = graphics;
        this.pipeline = pipeline;
        this.textureSetup = textureSetup;
        this.textured = textured;
    }

    /** Untextured, colored quads ({@code RenderType.gui()} in 1.21). */
    public static GuiVertexRecorder colored(DLGuiGraphics graphics) {
        return colored(graphics.graphics());
    }

    public static GuiVertexRecorder colored(GuiGraphicsExtractor graphics) {
        return new GuiVertexRecorder(graphics, RenderPipelines.GUI, TextureSetup.noTexture(), false);
    }

    /** Textured, colored quads. */
    public static GuiVertexRecorder textured(DLGuiGraphics graphics, Identifier texture) {
        return textured(graphics.graphics(), Minecraft.getInstance().getTextureManager().getTexture(texture));
    }

    public static GuiVertexRecorder textured(GuiGraphicsExtractor graphics, AbstractTexture texture) {
        return new GuiVertexRecorder(graphics, RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler()), true);
    }

    private int base() {
        return (vertices - 1) * STRIDE;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (vertices * STRIDE + STRIDE > data.length) {
            data = Arrays.copyOf(data, data.length * 2);
            colors = Arrays.copyOf(colors, colors.length * 2);
        }
        vertices++;
        int i = base();
        data[i] = x;
        data[i + 1] = y;
        data[i + 2] = 0;
        data[i + 3] = 0;
        colors[vertices - 1] = 0xFFFFFFFF;
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return setColor((a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255));
    }

    @Override
    public VertexConsumer setColor(int color) {
        if (vertices > 0) {
            colors[vertices - 1] = color;
        }
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        if (vertices > 0) {
            data[base() + 2] = u;
            data[base() + 3] = v;
        }
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }

    public boolean isEmpty() {
        return vertices == 0;
    }

    /** Submits the recorded quads to the GUI render state, clipped by the current scissor. */
    public void submit() {
        if (vertices == 0) {
            return;
        }
        while (vertices % 4 != 0) {
            float[] last = Arrays.copyOfRange(data, base(), base() + STRIDE);
            int lastColor = colors[vertices - 1];
            addVertex(last[0], last[1], 0);
            System.arraycopy(last, 0, data, base(), STRIDE);
            colors[vertices - 1] = lastColor;
        }
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int v = 0; v < vertices; v++) {
            float x = data[v * STRIDE], y = data[v * STRIDE + 1];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        ScreenRectangle scissor = graphics.peekScissorStack();
        int x0 = (int) Math.floor(minX), y0 = (int) Math.floor(minY);
        ScreenRectangle bounds = new ScreenRectangle(x0, y0, Math.max(1, (int) Math.ceil(maxX) - x0), Math.max(1, (int) Math.ceil(maxY) - y0));
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
            if (bounds == null) {
                vertices = 0;
                return;
            }
        }
        graphics.submitGuiElementRenderState(new Quads(pipeline, textureSetup, Arrays.copyOf(data, vertices * STRIDE), Arrays.copyOf(colors, vertices), vertices, textured, scissor, bounds));
        vertices = 0;
    }

    private record Quads(RenderPipeline pipeline, TextureSetup textureSetup, float[] data, int[] colors, int count, boolean textured,
                         @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int v = 0; v < count; v++) {
                int i = v * STRIDE;
                consumer.addVertex(data[i], data[i + 1], 0);
                if (textured) {
                    consumer.setUv(data[i + 2], data[i + 3]);
                }
                consumer.setColor(colors[v]);
            }
        }
    }
}
