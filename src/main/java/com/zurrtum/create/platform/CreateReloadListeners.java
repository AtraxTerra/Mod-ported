package com.zurrtum.create.platform;

import com.zurrtum.create.Create;
import com.zurrtum.create.content.kinetics.belt.BeltHelper;
import com.zurrtum.create.foundation.recipe.RecipeFinder;
import com.zurrtum.create.foundation.recipe.trie.RecipeTrieFinder;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Port-only: Create's server (data pack) reload listeners, registered through NeoForge's event.
 *
 * <p>Upstream appends them to {@code ReloadableServerResources.listeners()} with a
 * {@code @ModifyReturnValue}. NeoForge builds its sorted listener graph from that same list and
 * rejects -- fatally, at server start -- any non-vanilla listener that did not come through
 * {@link AddServerReloadListenersEvent}. Same listeners, same order; the event runs them after all
 * vanilla listeners, which is where upstream's append put them too.
 */
@EventBusSubscriber(modid = Create.MOD_ID)
public final class CreateReloadListeners {
    private CreateReloadListeners() {
    }

    @SubscribeEvent
    public static void addServerListeners(AddServerReloadListenersEvent event) {
        event.addListener(id("recipe_finder"), RecipeFinder.LISTENER);
        event.addListener(id("recipe_trie_finder"), RecipeTrieFinder.LISTENER);
        event.addListener(id("belt_helper"), BeltHelper.LISTENER);
        event.addListener(id("config"), AllConfigs.LISTENER);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Create.MOD_ID, path);
    }
}
