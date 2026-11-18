package com.zurrtum.create.platform;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

/**
 * The loader seam.
 * <p>
 * The upstream Fabric source reaches for {@code FabricLoader.getInstance()} in 20 places across 22
 * files, for exactly six things. Rather than scatter NeoForge calls through those files, every one
 * of them goes through here; this is the only class in the mod that imports {@code net.neoforged.fml}.
 * <p>
 * Keeping the seam this narrow is what makes the mod buildable on either loader: swap this one file
 * and the other 3.5k compile unchanged.
 *
 * <h2>Never call this from a static initialiser</h2>
 * {@code ModList} is null until FML has finished building the mod list, which is <i>after</i> it
 * constructs the mod class. On Fabric the equivalent {@code FabricLoader.getInstance()} is available
 * at class-load time, so upstream freely computes things like the mod version in a static field.
 * Carried over directly, that threw {@code ExceptionInInitializerError} and killed the server 40ms
 * after the classloader was built -- with no message in any log, because nothing had flushed yet.
 *
 * <p>So anything that asks a question here must do it lazily; {@link
 * com.zurrtum.create.Create#getVersion()} is the pattern. For the one place that genuinely must ask
 * earlier than this class can answer -- a mixin config plugin -- see {@link EarlyModDetection}.
 */
public final class Platform {
    private Platform() {
    }

    /**
     * Mind the semantics when porting a condition: the Fabric build asks
     * {@code isModLoaded("fabric-transfer-api-v1")} and similar to decide whether an optional code
     * path is available. On NeoForge every such id is absent, so those questions answer {@code false}
     * and the mod selects its self-contained vanilla path -- which is the intended behaviour, not a
     * degradation. Do not "fix" these by making them return true.
     */
    public static boolean isModLoaded(String modId) {
        ModList modList = ModList.get();
        if (modList == null) {
            // Too early for ModList: BuiltInRegistries.freeze() -- where Create and its addons
            // register (see AddonEntrypoint) -- runs before FML builds it. FML's loading mod list
            // already knows which mods will load, so answer from that rather than throwing. (The
            // Diesel Generators port asked about JEI from there and the server died in Mods' static
            // initialiser.)
            return EarlyModDetection.isPresent(modId);
        }
        return modList.isLoaded(modId);
    }

    // ModList hands back a wildcard; keeping it in the signature avoids an unchecked cast here
    // and still assigns cleanly to a ModContainer at the call sites.
    public static Optional<? extends ModContainer> getModContainer(String modId) {
        return ModList.get().getModContainerById(modId);
    }

    /**
     * The mod's own version string, with any build metadata suffix stripped.
     * Replaces {@code FabricLoader.getInstance().getModContainer(id).getMetadata().getVersion()}.
     */
    public static String getModVersion(String modId) {
        return getModContainer(modId)
            .map(container -> container.getModInfo().getVersion().toString())
            .map(version -> version.split("\\+")[0])
            .orElse("unknown");
    }

    public static @Nullable String getModName(String modId) {
        return getModContainer(modId)
            .map(container -> container.getModInfo().getDisplayName())
            .orElse(null);
    }

    public static Collection<? extends ModContainer> getAllMods() {
        return ModList.get().getSortedMods();
    }

    public static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public static boolean isDevelopmentEnvironment() {
        // FMLEnvironment, not FMLLoader: the latter's isProduction() is an instance method.
        return !FMLEnvironment.isProduction();
    }
}
