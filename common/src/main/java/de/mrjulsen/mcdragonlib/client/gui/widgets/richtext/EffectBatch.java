package de.mrjulsen.mcdragonlib.client.gui.widgets.richtext;

import com.google.common.collect.Lists;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.List;

public class EffectBatch {
    /**
     * An underline or strikethrough rectangle (1.21: {@code BakedGlyph.Effect}, removed in 26.2), same
     * fields in the same order.
     */
    public record Effect(float x0, float y0, float x1, float y1, float depth, float r, float g, float b, float a) {
        public void addTo(VertexConsumer consumer, Matrix4f pose) {
            consumer.addVertex(pose, x0, y0, depth).setColor(r, g, b, a);
            consumer.addVertex(pose, x0, y1, depth).setColor(r, g, b, a);
            consumer.addVertex(pose, x1, y1, depth).setColor(r, g, b, a);
            consumer.addVertex(pose, x1, y0, depth).setColor(r, g, b, a);
        }
    }

    private final List<Effect> effects;
    private final float x;
    private final float y;
    private final float scale;

    public EffectBatch(float x, float y, float scale) {
        this.effects = Lists.newLinkedList();
        this.x = x;
        this.y = y;
        this.scale = scale;
    }

    public void addEffectToBatch(Effect effect) {
        this.effects.add(effect);
    }

    public List<Effect> getEffects() {
        return effects;
    }

    public boolean isEmpty() {
        return effects.isEmpty();
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float scale() {
        return scale;
    }
}
