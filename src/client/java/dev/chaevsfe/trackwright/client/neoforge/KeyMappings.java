// Adapted from chaevsfe / Create Fly: Trackwright (https://github.com/chaevsfe/Trackwright)
// Licensed under GPL-3.0-or-later — extracted/adapted with AI assistance.
package dev.chaevsfe.trackwright.client.neoforge;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Port: Fabric's {@code KeyMappingHelper.registerKeyMapping}. Upstream creates its key mappings in the
 * client entrypoint, which runs before Minecraft builds its options; NeoForge takes them from
 * {@link RegisterKeyMappingsEvent}, fired while the options are built, so they wait here until then.
 */
public final class KeyMappings {
    private static final List<KeyMapping> PENDING = new ArrayList<>();

    private KeyMappings() {
    }

    public static KeyMapping register(KeyMapping mapping) {
        PENDING.add(mapping);
        return mapping;
    }

    static void registerAll(RegisterKeyMappingsEvent event) {
        PENDING.forEach(event::register);
    }
}
