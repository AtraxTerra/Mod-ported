package com.zurrtum.create.compat;

/**
 * Optional-integration dispatch.
 *
 * <p>Both of upstream's integrations are off on this loader, so this is currently empty:
 * <ul>
 *   <li><b>CC: Tweaked</b> -- {@code ComputerIntegration}, {@code AllComputerPeripherals} and
 *       {@code AllComputerDisplaySource} are excluded from compilation, because they import
 *       CC:T's own API and no NeoForge-side artifact for it is wired into the build. Create's
 *       loader-neutral half of that feature ({@code AbstractComputerBehaviour} and the event
 *       types) is still compiled, and 17 block entities still reference it -- so restoring the
 *       feature is a matter of adding the dependency and dropping three exclusions, not of
 *       rewriting anything.</li>
 *   <li><b>Recipe viewers</b> -- {@code compat.fabric.RecipeCommonPlugin} is Fabric-specific, and
 *       the JEI categories still call Fabric's custom-ingredient API.</li>
 * </ul>
 *
 * <p>See PORTING.md for what each one needs.
 */
public class CompatMod {
    public static void register() {
    }
}
