package de.mrjulsen.mcdragonlib.client.util;

import java.util.Optional;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * A texture DragonLib can draw: a texture location, or a texture object that has no location (a
 * {@code DynamicTexture}, for instance).
 *
 * <p>NeoForge 26.2 port: 1.21 kept a raw OpenGL texture id for the second case. 26.2 textures are GPU
 * objects without such ids, so the texture object itself is kept instead.
 */
public class DLTexture {
    private final Identifier texture;
    private final AbstractTexture dynamicTexture;
    private final int textureWidth;
    private final int textureHeight;

    public DLTexture(Identifier texture, int width, int height) {
        this.texture = texture;
        this.dynamicTexture = null;
        this.textureWidth = width;
        this.textureHeight = height;
    }

    public DLTexture(AbstractTexture texture, int width, int height) {
        this.texture = null;
        this.dynamicTexture = texture;
        this.textureWidth = width;
        this.textureHeight = height;
    }

    public Optional<Identifier> getTexture() {
        return Optional.ofNullable(texture);
    }

    public Optional<AbstractTexture> getDynamicTexture() {
        return Optional.ofNullable(dynamicTexture);
    }

    /** True when this texture has no location (1.21: drawn through its OpenGL texture id). */
    public boolean usesTextureId() {
        return texture == null;
    }

    public int width() {
        return textureWidth;
    }

    public int height() {
        return textureHeight;
    }
}
