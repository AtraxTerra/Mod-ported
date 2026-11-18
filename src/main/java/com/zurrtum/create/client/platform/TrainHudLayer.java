package com.zurrtum.create.client.platform;

import com.zurrtum.create.Create;
import com.zurrtum.create.client.content.trains.TrainHUD;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Port-only: the train HUD as a NeoForge GUI layer.
 *
 * <p>Upstream wraps {@code Hud.nextContextualInfoState()} inside {@code extractHotbarAndDecorations}:
 * it draws the train HUD there and, when it did, returns {@code ContextualInfo.EMPTY} so the XP or
 * locator bar is not drawn over it. NeoForge splits that method into separate GUI layers and moves the
 * contextual-bar choice to the start of the frame, where nothing can be drawn yet.
 *
 * <p>So the HUD is drawn by a layer in the same place in the order -- after the hotbar, health,
 * vehicle-health and air layers, just before the contextual bar -- and when it draws, the two layers
 * that make up the contextual bar are cancelled for that frame. The experience level number stays,
 * as it does upstream (vanilla draws it separately from the bar).
 *
 * <p>Layers registered through the event do not inherit vanilla's "HUD hidden" (F1) condition, so
 * the layer checks it itself.
 */
@EventBusSubscriber(modid = Create.MOD_ID, value = Dist.CLIENT)
public final class TrainHudLayer {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Create.MOD_ID, "train_hud");

    /** Whether the layer drew this frame; read by the contextual-bar layers that come right after it. */
    private static boolean drawn;

    private TrainHudLayer() {
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND, ID, TrainHudLayer::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        drawn = !mc.gui.hud.isHidden() && TrainHUD.renderOverlay(mc, graphics, deltaTracker);
    }

    @SubscribeEvent
    public static void hideContextualBar(RenderGuiLayerEvent.Pre event) {
        if (drawn && (event.getName().equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND)
            || event.getName().equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR))) {
            event.setCanceled(true);
        }
    }
}
