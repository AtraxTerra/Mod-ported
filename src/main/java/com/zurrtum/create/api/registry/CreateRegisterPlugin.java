package com.zurrtum.create.api.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

public interface CreateRegisterPlugin {
    List<CreateRegisterPlugin> PLUGINS = createPlugins();

    default void onBlockRegister() {
    }

    default void onFluidRegister() {
    }

    default void onDataLoaderRegister() {
    }

    default void onEntityAttributeRegister() {
    }

    /**
     * Discovers the registration plugins contributed by Create and by any addon.
     *
     * <h2>Ported from Fabric entrypoints to {@link ServiceLoader}</h2>
     * Upstream this reads the {@code "create_plugin"} entrypoint out of every mod's
     * {@code fabric.mod.json}. NeoForge has no entrypoint concept, so the equivalent extension point
     * here is a service: a plugin declares itself in
     * {@code META-INF/services/com.zurrtum.create.api.registry.CreateRegisterPlugin}.
     * Create's own plugin is declared that way too, in this mod's resources.
     *
     * <p>Addon authors: this is the one piece of Create's extension surface whose <i>declaration</i>
     * differs between the Fabric and NeoForge builds. The interface itself is unchanged.
     *
     * <h2>Ordering</h2>
     * Order matters -- Create's own registrations must run before an addon's. Upstream sorts by mod
     * id with {@code "create"} forced first. A service has no mod id attached, so this sorts
     * Create's own plugins first (by package) and then by class name, which is deterministic and
     * gives the same guarantee that matters.
     */
    static List<CreateRegisterPlugin> createPlugins() {
        List<CreateRegisterPlugin> plugins = new ArrayList<>();
        ServiceLoader.load(CreateRegisterPlugin.class, CreateRegisterPlugin.class.getClassLoader())
            .forEach(plugins::add);
        Comparator<CreateRegisterPlugin> order = Comparator
            .comparing((CreateRegisterPlugin plugin) -> !isCreateOwn(plugin))
            .thenComparing(plugin -> plugin.getClass().getName());
        plugins.sort(order);
        return List.copyOf(plugins);
    }

    private static boolean isCreateOwn(CreateRegisterPlugin plugin) {
        return plugin.getClass().getName().startsWith("com.zurrtum.create.");
    }

    static void registerBlock() {
        PLUGINS.forEach(CreateRegisterPlugin::onBlockRegister);
    }

    static void registerFluid() {
        PLUGINS.forEach(CreateRegisterPlugin::onFluidRegister);
    }

    static void registerDataLoader() {
        PLUGINS.forEach(CreateRegisterPlugin::onDataLoaderRegister);
    }

    static void registerEntityAttributes() {
        PLUGINS.forEach(CreateRegisterPlugin::onEntityAttributeRegister);
    }
}
