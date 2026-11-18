package com.zurrtum.create.client.platform;

import com.zurrtum.create.Create;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.resource.JarContentsPackResources;
import net.neoforged.neoforgespi.language.IModInfo;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

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
 *
 * <p>Port note (first client launch): the sub-pack directories ship no {@code pack.mcmeta} --
 * upstream builds their metadata in code -- so NeoForge's {@code addPackFinders}, which reads it
 * from the directory, produced a null pack and the client died in pack discovery. The packs are
 * now built here with upstream's metadata (the current client format from SharedConstants), on
 * NeoForge's own jar-contents resources.
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
        // Same names, descriptions and positions as upstream's ClientPackSourceMixin.
        add(event, "legacy_copper", "Create legacy copper", "Replacement textures for Vanilla Copper", false);
        if (ModList.get().isLoaded("holdmyitems")) {
            add(event, "holdmyitems", "Create & Hold My Items", "Hold My Items compatible addon", true);
        }
    }

    private static void add(AddPackFindersEvent event, String directory, String name, String description, boolean required) {
        IModInfo modInfo = ModList.get().getModContainerById(Create.MOD_ID).orElseThrow().getModInfo();
        JarContents contents = modInfo.getOwningFile().getFile().getContents();
        String id = "mod/" + Create.MOD_ID + ":" + directory;
        PackLocationInfo info = new PackLocationInfo(
            id,
            Component.literal(name),
            PackSource.BUILT_IN,
            Optional.of(new KnownPack("neoforge", id, modInfo.getVersion().toString()))
        );
        PackMetadataSection metadata = new PackMetadataSection(
            Component.literal(description),
            SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).minorRange()
        );
        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new SubPackResources(location, contents, directory, metadata);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata packMetadata) {
                return openPrimary(location);
            }
        };
        Pack pack = Pack.readMetaAndCreate(
            info,
            resources,
            PackType.CLIENT_RESOURCES,
            new PackSelectionConfig(required, Pack.Position.TOP, false)
        );
        if (pack == null) {
            // readMetaAndCreate logs why; never hand NeoForge a null pack (it does not check).
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    /** A sub-directory of the mod jar as a pack, with its metadata supplied rather than read. */
    private static final class SubPackResources extends JarContentsPackResources {
        private final PackMetadataSection metadata;

        SubPackResources(PackLocationInfo location, JarContents contents, String prefix, PackMetadataSection metadata) {
            super(location, contents, prefix);
            this.metadata = metadata;
        }

        @SuppressWarnings("unchecked")
        @Override
        public @Nullable <T> T getMetadataSection(MetadataSectionType<T> type) {
            return type == PackMetadataSection.forPackType(PackType.CLIENT_RESOURCES) ? (T) metadata : null;
        }
    }
}
