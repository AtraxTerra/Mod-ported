// Adapted from chaevsfe / Create Fly: Steam 'n' Rails (https://github.com/chaevsfe/Create-Steam-n-Rails-Fly)
// Based on original work from Create: Steam 'n' Rails by The Railways Team (https://github.com/Layers-of-Railways/Railway)
// Licensed under LGPL-3.0-only — extracted/adapted with AI assistance.
package com.railwayteam.railways.neoforge;

import com.railwayteam.railways.Railways;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Port check, CI only ({@code -Drailways.port.probeClasses=<compiled classes directory>} on a
 * dedicated server): links every class of this mod and reports those that cannot be verified without
 * client classes.
 *
 * <p>Why it exists: upstream marks client members of common classes with Fabric's
 * {@code @Environment(EnvType.CLIENT)}, and Fabric Loader strips them on a dedicated server.
 * NeoForge 26 strips nothing, so such a member stays, and if its bytecode makes the verifier check a
 * client type (a client object passed where its supertype is expected, say), loading the common class
 * on a server fails. One server boot shows only the first such class; this lists them all at once.
 * Client-only classes (renderers, screens...) are expected in the list: they are never loaded on a
 * server. Common classes in it are bugs.
 */
public final class ServerClassProbe {
    private ServerClassProbe() {
    }

    public static void runIfRequested() {
        String classes = System.getProperty("railways.port.probeClasses");
        if (classes == null || classes.isEmpty())
            return;
        try {
            Path root = Path.of(classes);
            List<String> names = new ArrayList<>();
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(f -> f.toString().endsWith(".class")).forEach(f -> {
                    String name = root.relativize(f).toString().replace('/', '.').replace('\\', '.');
                    names.add(name.substring(0, name.length() - ".class".length()));
                });
            }
            int[] failed = new int[3];
            for (String name : names) {
                if (name.contains(".mixin.") || name.contains(".fabric_mixin.") || name.endsWith("package-info"))
                    continue;
                Class<?> type;
                try {
                    type = Class.forName(name, false, ServerClassProbe.class.getClassLoader());
                } catch (Throwable t) {
                    // A superclass or an interface is missing: the class cannot exist on a server.
                    report(failed, 0, "LOAD", name, t);
                    continue;
                }
                try {
                    // HotSpot links (and so verifies) a class before listing its members; constructors
                    // rarely name client types, so a failure here is the verifier itself.
                    type.getDeclaredConstructors();
                } catch (Throwable t) {
                    report(failed, 1, "LINK", name, t);
                    continue;
                }
                try {
                    // Only reflection resolves member signatures; a client type there is harmless at
                    // run time unless something reflects on the class. Listed for information.
                    type.getDeclaredMethods();
                    type.getDeclaredFields();
                } catch (Throwable t) {
                    report(failed, 2, "SIG", name, t);
                }
            }
            Railways.LOGGER.error("PROBE done: {} classes, LOAD {}, LINK {}, SIG {}", names.size(), failed[0], failed[1], failed[2]);
        } catch (Throwable t) {
            Railways.LOGGER.error("PROBE could not run", t);
        }
        Runtime.getRuntime().halt(0);
    }

    private static void report(int[] failed, int kind, String label, String name, Throwable t) {
        failed[kind]++;
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root)
            root = root.getCause();
        Railways.LOGGER.error("PROBE {} {} missing={}", label, name, String.valueOf(root.getMessage()).replace('/', '.'));
    }
}
