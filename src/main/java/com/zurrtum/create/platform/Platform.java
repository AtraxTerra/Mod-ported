package com.zurrtum.create.platform;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
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
        return ModList.get().isLoaded(modId);
    }

    public static Optional<ModContainer> getModContainer(String modId) {
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

    public static Collection<ModContainer> getAllMods() {
        return ModList.get().getSortedMods();
    }

    public static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public static boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }
}
