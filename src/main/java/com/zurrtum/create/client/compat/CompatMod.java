package com.zurrtum.create.client.compat;

/**
 * Client-side optional-integration dispatch. Empty on this loader:
 * <ul>
 *   <li>{@code PotionRenderHandler} registers a Fabric fluid-variant render handler, which has no
 *       counterpart here -- 26.2 moved fluid tinting off the client fluid extensions entirely.</li>
 *   <li>{@code GoggleTrinketRenderer} targets Trinkets, a Fabric accessory mod. The NeoForge
 *       equivalent is Curios; wiring it up means a new renderer, not a port of this one.</li>
 * </ul>
 */
public class CompatMod {
    public static void register() {
    }
}
