package de.mrjulsen.mcdragonlib.client.util;

import java.util.Optional;

import net.minecraft.resources.Identifier;

public class DLTexture {
    private final Identifier texture;
    private final int textureId;
    private final boolean useTextureId;
    private final int textureWidth;
    private final int textureHeight;

    public DLTexture(Identifier texture, int textureId, boolean useTextureId, int textureWidth, int textureHeight) {
        this.texture = texture;
        this.textureId = textureId;
        this.useTextureId = useTextureId;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    public DLTexture(Identifier texture, int width, int height) {
        this(texture, 0, false, width, height);
    }

    public DLTexture(int textureId, int width, int height) {
        this(null, textureId, true, width, height);
    }

    public Optional<Identifier> getTexture() {
        return Optional.ofNullable(texture);
    }

    public int getTextureId() {
        return textureId;
    }

    public boolean usesTextureId() {
        return useTextureId;
    }

    public int width() {
        return textureWidth;
    }

    public int height() {
        return textureHeight;
    }
}
