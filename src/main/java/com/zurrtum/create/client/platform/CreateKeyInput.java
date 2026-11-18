package com.zurrtum.create.client.platform;

import com.mojang.blaze3d.platform.InputConstants;
import com.zurrtum.create.client.Create;
import com.zurrtum.create.client.content.contraptions.wrench.RadialWrenchHandler;
import com.zurrtum.create.client.content.equipment.toolbox.ToolboxHandlerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Port-only: Create's in-world key handling, on NeoForge's key event.
 *
 * <p>Upstream's {@code KeyboardHandlerMixin} injects into {@code KeyboardHandler.keyPress} twice: at
 * TAIL (key pressed or repeated) and at the fifth RETURN (key released), then runs the body below
 * with that flag. NeoForge's patch changes the method's returns, so {@code ordinal = 4} matched
 * nothing and crashed the client at startup. NeoForge already posts {@link InputEvent.Key} for press,
 * release and repeat at the end of the same block, after screens have had their turn -- which is how
 * Create itself does this on NeoForge: pressed unless the action is RELEASE.
 */
@EventBusSubscriber(modid = com.zurrtum.create.Create.MOD_ID, value = Dist.CLIENT)
public final class CreateKeyInput {
    private CreateKeyInput() {
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        onKey(Minecraft.getInstance(), event.getKeyEvent(), event.getAction() != InputConstants.RELEASE);
    }

    // Upstream's body, unchanged.
    private static void onKey(Minecraft minecraft, KeyEvent input, boolean pressed) {
        if (minecraft.gui.screen() != null) {
            return;
        }
        if (Create.SCHEMATIC_HANDLER.onKeyInput(input, pressed)) {
            return;
        }
        if (ToolboxHandlerClient.onKeyInput(minecraft, input)) {
            return;
        }
        RadialWrenchHandler.onKeyInput(minecraft, input, pressed);
    }
}
