package com.zurrtum.create.client.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zurrtum.create.client.Create;
import com.zurrtum.create.client.content.trains.schedule.hat.TrainHatInfoReloadListener;
import com.zurrtum.create.client.flywheel.backend.compile.FlwProgramsReloader;
import com.zurrtum.create.client.model.obj.ObjLoader;
import com.zurrtum.create.client.ponder.Ponder;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

/**
 * Port-only: Create's client (resource pack) reload listeners, registered through NeoForge's event.
 *
 * <p>Upstream registers them from {@code MinecraftMixin} with
 * {@code resourceManager.registerReloadListener}, injected at {@code Window.updateRawMouseInput} in
 * {@code Minecraft}'s constructor. NeoForge requires mod listeners to go through
 * {@link AddClientReloadListenersEvent}. Today that injection happens to run just AFTER NeoForge's
 * check (initClientHooks is the line before), so it would not crash -- but the listeners would sit
 * outside NeoForge's ordering graph, and the contract says event-only. Same listeners, same order and
 * the same OpenGL-only condition; the GPU device already exists when the event fires.
 */
@EventBusSubscriber(modid = com.zurrtum.create.Create.MOD_ID, value = Dist.CLIENT)
public final class CreateClientReloadListeners {
    private CreateClientReloadListeners() {
    }

    @SubscribeEvent
    public static void addClientListeners(AddClientReloadListenersEvent event) {
        if (RenderSystem.getDevice().getDeviceInfo().backendName().equals("OpenGL")) {
            event.addListener(id("flywheel_programs"), FlwProgramsReloader.INSTANCE);
        }
        event.addListener(id("obj_models"), ObjLoader.INSTANCE);
        event.addListener(id("client_resources"), Create.RESOURCE_RELOAD_LISTENER);
        event.addListener(id("train_hats"), TrainHatInfoReloadListener.LISTENER);
        event.addListener(id("ponder"), Ponder.RESOURCE_RELOAD_LISTENER);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(com.zurrtum.create.Create.MOD_ID, path);
    }
}
