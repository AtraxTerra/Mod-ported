package com.zurrtum.create.api.entity;

import com.zurrtum.create.platform.Platform;
import com.zurrtum.create.infrastructure.player.FakePlayerEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.world.entity.Entity;

public interface FakePlayerHandler {
    boolean FABRIC = Platform.isModLoaded("fabric-events-interaction-v0");

    static boolean has(Entity player) {
        if (FABRIC && player instanceof FakePlayer) {
            return true;
        }
        return player instanceof FakePlayerEntity;
    }
}
