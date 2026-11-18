package com.zurrtum.create.client.vanillin;

import com.zurrtum.create.platform.Platform;
import com.zurrtum.create.catnip.config.Builder;
import com.zurrtum.create.catnip.config.ConfigBase;
import com.zurrtum.create.client.vanillin.config.*;
import net.neoforged.fml.ModContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.zurrtum.create.client.vanillin.Vanillin.MOD_ID;


public class VanillinConfig {
    public static final String VANILLIN_OVERRIDES = "vanillin:overrides";
    private static ModOverrides overrides;
    private static CClient client;

    public static ModOverrides modOverrides() {
        var blockEntities = new ArrayList<VisualOverride>();
        var entities = new ArrayList<VisualOverride>();

        // Ported from Fabric custom values to NeoForge mod properties.
        //
        // On Fabric an addon declares its Vanillin overrides as a JSON object under a custom key in
        // fabric.mod.json, read back as a CustomValue tree. NeoForge's equivalent is the mod's own
        // properties from neoforge.mods.toml, handed back as a plain Map. So the tree-walking below
        // becomes Map and String checks, and the nested TOML table arrives as a nested Map.
        //
        // The declaration an addon writes therefore differs between loaders, the same way the
        // registration plugin does. An addon would declare, in its [[mods]] block:
        //
        //     [mods.modproperties."vanillin:overrides".block_entities]
        //     "minecraft:chest" = "..."
        //
        // An addon that has not been updated simply contributes no overrides, which is the same
        // outcome as not declaring any -- it degrades quietly rather than failing to load.
        for (ModContainer container : Platform.getAllMods()) {
            var modid = container.getModId();
            Object overridesValue = container.getModInfo().getModProperties().get(VANILLIN_OVERRIDES);

            if (overridesValue == null) {
                continue;
            }

            if (!(overridesValue instanceof Map<?, ?> overrides)) {
                Vanillin.CONFIG_LOGGER.warn(
                    "Mod '{}' attempted to override options with an invalid value, ignoring",
                    modid
                );
                continue;
            }

            readSection(blockEntities, modid, overrides, "block_entities", "block entity");
            readSection(entities, modid, overrides, "entities", "entity");
        }

        return new ModOverrides(blockEntities, entities);
    }

    private static void readSection(
        List<VisualOverride> dst,
        String modid,
        Map<?, ?> overrides,
        String sectionName,
        String singular
    ) {
        Object sectionValue = overrides.get(sectionName);

        if (sectionValue == null) {
            return;
        }

        if (!(sectionValue instanceof Map<?, ?> section)) {
            Vanillin.CONFIG_LOGGER.warn(
                "Mod '{}' attempted to override {} with an invalid value, ignoring",
                modid,
                sectionName
            );
            return;
        }

        for (Map.Entry<?, ?> entry : section.entrySet()) {
            var value = entry.getValue();
            var key = String.valueOf(entry.getKey());
            if (!(value instanceof String valueString)) {
                Vanillin.CONFIG_LOGGER.warn(
                    "Mod '{}' attempted to override {} '{}' with an invalid value, ignoring",
                    modid,
                    singular,
                    key
                );
                continue;
            }

            var parsed = VisualOverrideValue.parse(valueString);

            if (parsed == null) {
                Vanillin.CONFIG_LOGGER.warn(
                    "Mod '{}' attempted to override {} '{}' with an invalid value '{}', ignoring",
                    modid,
                    singular,
                    key,
                    valueString
                );
                continue;
            }

            dst.add(new VisualOverride(key, modid, parsed));
        }
    }

    public static CClient client() {
        return client;
    }

    public static ModOverrides overrides() {
        return overrides;
    }

    public static void register() {
        client = Builder.create(CClient::new, MOD_ID, "client", true);
        overrides = modOverrides();
    }

    public static void apply(Configurator configurator) {
        var blockEntities = client.blockEntities;
        var blockEntityOverrides = overrides.blockEntities();
        for (Configurator.ConfiguredVisual configured : configurator.blockEntities.values()) {
            apply(configured, blockEntities, blockEntityOverrides);
        }

        var entities = client.entities;
        var entityOverrides = overrides.entities();
        for (Configurator.ConfiguredVisual configured : configurator.entities.values()) {
            apply(configured, entities, entityOverrides);
        }
    }

    private static void apply(
        Configurator.ConfiguredVisual configured,
        Map<String, ConfigBase.ConfigEnum<VisualConfigValue>> config,
        Map<String, List<VisualOverride>> overrides
    ) {
        String key = configured.configKey();
        VisualConfigValue enabled = config.get(key).get();
        configured.set(enabled, overrides.get(key));
    }
}
