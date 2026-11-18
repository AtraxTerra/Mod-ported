package com.zurrtum.create.platform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Port-only: the NeoForge stand-in for Fabric's {@code main} and {@code client} entrypoints, for
 * Create addons.
 *
 * <p>On Fabric, an addon's {@code ModInitializer} registers its items, block entities and so on
 * straight into the vanilla registries, and its {@code ClientModInitializer} runs from inside
 * {@code Minecraft}'s constructor. Neither timing exists for a NeoForge {@code @Mod} constructor:
 * vanilla registries only accept entries while NeoForge has them open, and on the client the
 * constructor runs before {@code Minecraft} exists. This port registers Create itself at
 * {@code BuiltInRegistries.freeze()} and starts Create's client side from {@code Minecraft}'s
 * constructor, exactly where Fabric would; an addon implementing this interface is called right
 * after each of those, so its Fabric-era code runs at the moment it was written for.
 *
 * <p>Declare an implementation in
 * {@code META-INF/services/com.zurrtum.create.platform.AddonEntrypoint}. Implementations are called
 * in class-name order, after Create's own work. {@link #onInitializeClient()} is only called on the
 * physical client.
 */
public interface AddonEntrypoint {
    /** Right after Create's registration, while vanilla registries still accept entries. */
    default void onInitialize() {
    }

    /** Right after Create's client initialisation, inside {@code Minecraft}'s constructor. */
    default void onInitializeClient() {
    }

    static void initialize() {
        Holder.ADDONS.forEach(AddonEntrypoint::onInitialize);
    }

    static void initializeClient() {
        Holder.ADDONS.forEach(AddonEntrypoint::onInitializeClient);
    }

    final class Holder {
        private static final List<AddonEntrypoint> ADDONS = load();

        private Holder() {
        }

        private static List<AddonEntrypoint> load() {
            List<AddonEntrypoint> addons = new ArrayList<>();
            ServiceLoader.load(AddonEntrypoint.class, AddonEntrypoint.class.getClassLoader()).forEach(addons::add);
            addons.sort(Comparator.comparing(addon -> addon.getClass().getName()));
            return List.copyOf(addons);
        }
    }
}
