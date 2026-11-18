package com.zurrtum.create.client.compat;

import com.zurrtum.create.platform.Platform;
import com.zurrtum.create.client.compat.fabric.PotionRenderHandler;
import com.zurrtum.create.client.compat.trinkets.GoggleTrinketRenderer;
import com.zurrtum.create.compat.Mods;

public class CompatMod {
    public static void register() {
        if (Platform.isModLoaded("fabric-transfer-api-v1")) {
            PotionRenderHandler.register();
        }
        if (Mods.TRINKETS_UPDATED.isLoaded()) {
            GoggleTrinketRenderer.register();
        }
    }
}
