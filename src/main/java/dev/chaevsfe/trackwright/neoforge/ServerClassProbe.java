// Adapted from chaevsfe / Create Fly: Trackwright (https://github.com/chaevsfe/Trackwright)
// Licensed under GPL-3.0-or-later — extracted/adapted with AI assistance.
package dev.chaevsfe.trackwright.neoforge;

import dev.chaevsfe.trackwright.Trackwright;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Port check, CI only ({@code -Dtrackwright.port.probeClasses=<compiled classes directory>} on a
 * dedicated server): links every common class of this mod and reports those that cannot be verified
 * without client classes. Upstream keeps client code in its own source set, so this list should hold
 * no common class; the check makes sure the NeoForge port keeps it that way.
 */
public final class ServerClassProbe {
    private ServerClassProbe() {
    }

    public static void runIfRequested() {
        String classes = System.getProperty("trackwright.port.probeClasses");
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
            Trackwright.LOGGER.error("PROBE done: {} classes, LOAD {}, LINK {}, SIG {}", names.size(), failed[0], failed[1], failed[2]);
        } catch (Throwable t) {
            Trackwright.LOGGER.error("PROBE could not run", t);
        }
        Runtime.getRuntime().halt(0);
    }

    private static void report(int[] failed, int kind, String label, String name, Throwable t) {
        failed[kind]++;
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root)
            root = root.getCause();
        Trackwright.LOGGER.error("PROBE {} {} missing={}", label, name, String.valueOf(root.getMessage()).replace('/', '.'));
    }
}
