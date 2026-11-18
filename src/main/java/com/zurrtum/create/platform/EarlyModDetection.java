package com.zurrtum.create.platform;

/**
 * Asks whether a mod is present at a point in startup where {@link Platform} must not be used.
 *
 * <h2>Why this is separate from {@link Platform}</h2>
 * A mixin config plugin's {@code onLoad} and {@code shouldApplyMixin} run during class
 * transformation, before mod construction and before {@code ModList} is populated. Calling
 * {@code ModList.get()} there throws. NeoForge's {@code FMLLoader.getLoadingModList()} is the API
 * that is valid that early, and it is all this class uses.
 *
 * <p>It is reached reflectively and every failure answers {@code false}. That is on purpose: this
 * runs before any error handling exists, so a wrong answer must degrade to "optional integration
 * off" rather than take the game down during transformation. A hard dependency must never be
 * detected through this class.
 */
public final class EarlyModDetection {
    private EarlyModDetection() {
    }

    /**
     * @return {@code true} only if the mod is definitely in the loading list. {@code false} when it
     * is absent <i>or</i> when the question could not be answered safely.
     */
    public static boolean isPresent(String modId) {
        try {
            // FML 11 (NeoForge 26.x) made the loader an instance: getLoadingModList() hangs off
            // FMLLoader.getCurrent(), it is no longer static. The static call this first used would
            // have thrown, been caught, and answered false for every mod, silently.
            Class<?> fmlLoader = Class.forName("net.neoforged.fml.loading.FMLLoader");
            Object loader = fmlLoader.getMethod("getCurrent").invoke(null);
            if (loader == null) {
                return false;
            }
            Object loadingModList = loader.getClass().getMethod("getLoadingModList").invoke(loader);
            if (loadingModList == null) {
                return false;
            }
            Object modFileInfo = loadingModList.getClass()
                .getMethod("getModFileById", String.class)
                .invoke(loadingModList, modId);
            return modFileInfo != null;
        } catch (Throwable ignored) {
            // Pre-mod-list, API moved, or anything else: treat the integration as absent.
            return false;
        }
    }
}
