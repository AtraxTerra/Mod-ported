package com.zurrtum.create.client;

import com.zurrtum.create.client.catnip.render.SuperByteBufferCache;
import com.zurrtum.create.client.compat.CompatMod;
import com.zurrtum.create.client.content.contraptions.glue.SuperGlueSelectionHandler;
import com.zurrtum.create.client.content.equipment.bell.SoulPulseEffectHandler;
import com.zurrtum.create.client.content.equipment.potatoCannon.PotatoCannonRenderHandler;
import com.zurrtum.create.client.content.equipment.zapper.ZapperRenderHandler;
import com.zurrtum.create.client.content.schematics.client.ClientSchematicLoader;
import com.zurrtum.create.client.content.schematics.client.SchematicAndQuillHandler;
import com.zurrtum.create.client.content.schematics.client.SchematicHandler;
import com.zurrtum.create.client.flywheel.impl.Flywheel;
import com.zurrtum.create.client.foundation.ClientResourceReloadListener;
import com.zurrtum.create.client.foundation.blockEntity.ValueSettingsClient;
import com.zurrtum.create.client.foundation.ponder.CreatePonderPlugin;
import com.zurrtum.create.client.foundation.utility.CameraAngleAnimationService;
import com.zurrtum.create.client.infrastructure.config.AllConfigs;
import com.zurrtum.create.client.ponder.Ponder;
import com.zurrtum.create.client.ponder.foundation.PonderIndex;
import com.zurrtum.create.client.vanillin.Vanillin;
import com.zurrtum.create.content.trains.GlobalRailwayManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.minecraft.resources.Identifier;

import static com.zurrtum.create.Create.MOD_ID;

@Mod(value = MOD_ID, dist = Dist.CLIENT)
public class Create {
    public static SoulPulseEffectHandler SOUL_PULSE_EFFECT_HANDLER;
    public static ValueSettingsClient VALUE_SETTINGS_HANDLER;
    public static SuperGlueSelectionHandler GLUE_HANDLER;
    public static GlobalRailwayManager RAILWAYS;
    public static PotatoCannonRenderHandler POTATO_CANNON_RENDER_HANDLER;
    public static ClientSchematicLoader SCHEMATIC_SENDER;
    public static SchematicHandler SCHEMATIC_HANDLER;
    public static SchematicAndQuillHandler SCHEMATIC_AND_QUILL_HANDLER;
    public static ZapperRenderHandler ZAPPER_RENDER_HANDLER;
    public static ClientResourceReloadListener RESOURCE_RELOAD_LISTENER;

    /**
     * Client entry point, replacing the Fabric {@code ClientModInitializer} entrypoint.
     * {@code dist = Dist.CLIENT} is what keeps NeoForge from constructing it on a dedicated server;
     * it is the equivalent of the {@code "client"} entrypoint key in {@code fabric.mod.json}.
     *
     * <p>The body is unchanged from upstream. Any of these registrations that turn out to need a
     * NeoForge lifecycle event rather than construction time should move to a listener on
     * {@code modEventBus} -- see PORTING.md, "client registration".
     */
    public Create(IEventBus modEventBus) {
        // Port: the body that used to be here is onInitializeClient() below. NeoForge constructs
        // mods BEFORE Minecraft exists; Fabric runs client entrypoints from inside Minecraft's
        // constructor. See onInitializeClient().
    }

    /**
     * Upstream's client entrypoint body, unchanged.
     *
     * <p>Port: called from {@code MinecraftMixin} right after {@code Minecraft.gameDirectory} is
     * set -- the point where Fabric Loader starts client mods. {@code Minecraft.getInstance()}
     * is non-null there, while options, resources and the font are not created yet. Upstream
     * code relies on both halves of that: {@code SchematicHandler} builds a screen from
     * {@code Minecraft.getInstance()} (the first client launch died on it, from the @Mod
     * constructor, where the instance is still null), and {@code OptionsMixin} needs
     * {@code AllKeys} registered before {@code Options} reads its key list.
     */
    public static void onInitializeClient() {
        new Flywheel().onInitializeClient();
        new Ponder().onInitializeClient();
        new Vanillin().onInitializeClient();
        SOUL_PULSE_EFFECT_HANDLER = new SoulPulseEffectHandler();
        VALUE_SETTINGS_HANDLER = new ValueSettingsClient();
        GLUE_HANDLER = new SuperGlueSelectionHandler();
        RAILWAYS = new GlobalRailwayManager();
        POTATO_CANNON_RENDER_HANDLER = new PotatoCannonRenderHandler();
        SCHEMATIC_SENDER = new ClientSchematicLoader();
        SCHEMATIC_HANDLER = new SchematicHandler();
        SCHEMATIC_AND_QUILL_HANDLER = new SchematicAndQuillHandler();
        ZAPPER_RENDER_HANDLER = new ZapperRenderHandler();
        RESOURCE_RELOAD_LISTENER = new ClientResourceReloadListener();
        AllConfigs.register();
        AllFluidConfigs.register();
        AllHandle.register();
        AllKeys.register();
        AllCasings.register();
        AllCTBehaviours.register();
        AllModels.register();
        AllPartialModels.register();
        AllEntityRenders.register();
        AllBlockEntityRenders.register();
        AllBlockEntityBehaviours.register();
        AllEntityBehaviours.register();
        AllItemTooltips.register();
        AllBufferCaches.register(SuperByteBufferCache.getInstance());
        AllExtensions.register();
        AllMovementRenders.register();
        AllDisplaySourceRenders.register();
        AllTrackMaterialModels.register();
        AllTrackRenders.register();
        AllBogeyStyleRenders.register();
        AllTrainIcons.register();
        AllScheduleRenders.register();
        AllMenuScreens.register();
        AllPotatoProjectileTransforms.register();
        CameraAngleAnimationService.register();
        PonderIndex.addPlugin(new CreatePonderPlugin());
        CompatMod.register();
        // Port: addons' Fabric `client` entrypoints, right after Create's (see AddonEntrypoint).
        com.zurrtum.create.platform.AddonEntrypoint.initializeClient();
    }

    public static Identifier asResource(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void invalidateRenderers() {
        SCHEMATIC_HANDLER.updateRenderers();
    }
}
