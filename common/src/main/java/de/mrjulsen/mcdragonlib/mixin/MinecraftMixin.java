package de.mrjulsen.mcdragonlib.mixin;

import de.mrjulsen.mcdragonlib.client.DLOverlayManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    /*
    @Inject(method = "setInitialScreen", at = @At(value = "HEAD"), cancellable = true)
    public void dragonlib$showScreen(RealmsClient realmsClient, ReloadInstance reloadInstance, GameConfig.QuickPlayData quickPlayData, CallbackInfo ci) {
        //DLWindow.openWindow(mgr -> new DLTestWindow(mgr));
        //ci.cancel();
    }
     */

    // 26.2: resizeDisplay became resizeGui.
    @Inject(method = "resizeGui", at = @At(value = "TAIL"))
    public void dragonlib$resizeDisplay(CallbackInfo ci) {
        DLOverlayManager.resizeDisplay();
    }
}
