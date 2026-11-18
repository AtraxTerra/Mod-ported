package com.zurrtum.create.platform;

import com.zurrtum.create.Create;
import com.zurrtum.create.content.trains.entity.CarriageEntityHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;

/**
 * Port-only: train carriages crossing chunk sections, on NeoForge's section event.
 *
 * <p>Upstream's {@code PersistentEntitySectionManagerCallbackMixin} injects into the server's
 * {@code Callback.onMove()} and captures a {@code @Local long}. NeoForge's patch adds a second
 * {@code long} there ({@code oldSectionKey}), so the implicit local became ambiguous and the
 * injection failed the first time an entity changed section -- a server crash in the in-world smoke
 * test. NeoForge posts {@link EntityEvent.EnteringSection} on that same line with both keys, which is
 * how Create itself does this on NeoForge.
 *
 * <p>Behaviour note: in vanilla the only {@code long} local there is the NEW section key, so upstream
 * passed (new, new) and {@code onEntityEnterSection} always returned early. With the event the old
 * key is the real old key, so carriages that move out of ticking chunks are flagged as Create intends
 * -- the behaviour of the original Create, which existing worlds were played with.
 */
@EventBusSubscriber(modid = Create.MOD_ID)
public final class CarriageSectionHooks {
    private CarriageSectionHooks() {
    }

    @SubscribeEvent
    public static void onEnteringSection(EntityEvent.EnteringSection event) {
        // Upstream hooked the server's entity manager only; the handler casts to ServerLevel.
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        CarriageEntityHandler.onEntityEnterSection(event.getEntity(), event.getPackedOldPos(), event.getPackedNewPos());
    }
}
