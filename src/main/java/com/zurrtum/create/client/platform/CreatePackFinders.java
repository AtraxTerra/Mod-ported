package com.zurrtum.create.client.platform;

import com.zurrtum.create.Create;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Registers Create's optional built-in resource packs.
 *
 * <h2>What this replaces, and why it is not a mixin any more</h2>
 * Upstream does this from {@code ClientPackSourceMixin}, injecting into
 * {@code ClientPackSource.populatePackList} and mounting subdirectories of the mod jar by hand from
 * {@code ModContainer.getRootPaths()}. NeoForge has a first-class event for exactly this, so the
 * mixin is dropped in favour of {@link AddPackFindersEvent} -- fewer moving parts, and no injection
 * to re-validate on every Minecraft update.
 *
 * <p>Upstream also registered the mod's main {@code assets/} directory as a built-in pack, guarded
 * on Fabric API being absent. That is deliberately NOT carried over: NeoForge mounts a mod's own
 * {@code assets/} automatically, and registering it again would mount Create's resources twice.
 * Only the two genuinely optional packs, which live in their own subdirectories and are not
 * auto-mounted, are registered here.
 *
 * <ul>
 *   <li>{@code legacy_copper} -- replacement textures for vanilla copper. Off by default, as upstream.</li>
 *   <li>{@code holdmyitems} -- compatibility textures, registered only when that mod is present.
 *       On by default, as upstream.</li>
 * </ul>
 *
 * <p>TODO(port, needs a build to confirm): {@code addPackFinders} has gained and lost parameters
 * across NeoForge versions. If this does not compile against the pinned build, the shape to check is
 * whether it still takes {@code (Identifier, PackType, Component, PackSource, boolean,
 * Pack.Position)}; the alternative is {@code event.addRepositorySource} with a
 * {@code PathPackResources} built from {@code ModList.get().getModFileById(MOD_ID).getFile()
 * .findResource(directory)}.
 */
@EventBusSubscriber(modid = Create.MOD_ID, value = Dist.CLIENT)
public final class CreatePackFinders {
    private CreatePackFinders() {
    }

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        event.addPackFinders(
            Identifier.fromNamespaceAndPath(Create.MOD_ID, "legacy_copper"),
            PackType.CLIENT_RESOURCES,
            Component.literal("Create legacy copper"),
            PackSource.BUILT_IN,
            false,
            Pack.Position.TOP
        );

        if (ModList.get().isLoaded("holdmyitems")) {
            event.addPackFinders(
                Identifier.fromNamespaceAndPath(Create.MOD_ID, "holdmyitems"),
                PackType.CLIENT_RESOURCES,
                Component.literal("Create & Hold My Items"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.TOP
            );
        }
    }
}
