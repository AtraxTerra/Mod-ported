package de.mrjulsen.mcdragonlib.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.architectury.injectables.annotations.PlatformOnly;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

/**
 * Fixes fabric.
 *
 * <p>NeoForge 26.2 port: the injector below is {@code @PlatformOnly(FABRIC)}, which Architectury strips
 * on NeoForge. The NeoForge-only build has no such transformer, so dragonlib.mixins.json no longer
 * lists this mixin; the class stays as upstream's reference.
 */
@Mixin(TextureManager.class)
public class TextureManagerMixin {

    @Final
    @Shadow
    private Map<Identifier, AbstractTexture> byPath;

    @PlatformOnly(value = PlatformOnly.FABRIC)
    @Inject(method = "release", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureManager;safeClose(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V", shift = Shift.BEFORE))
    public void dragonlib$release(Identifier path, CallbackInfo ci) {
        this.byPath.remove(path);
    }
}
