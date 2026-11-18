package com.zurrtum.create.mixin;

import com.zurrtum.create.Create;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Selects which common mixins to apply.
 *
 * <h2>What changed in the port, and why it is this short</h2>
 * The Fabric version of this class asked {@code FabricLoader} which Fabric API modules were present
 * and picked a mixin set accordingly. Every one of those questions answers "absent" on NeoForge, so
 * the branching collapses to a constant -- and the branch it collapses to is Create's own
 * self-contained path, which upstream already ships and maintains for the no-Fabric-API case
 * (the Fabric build declares Fabric API as {@code compileOnly}).
 *
 * <p>Spelling the choice out as a constant list, rather than keeping the conditions and letting them
 * all return false, is deliberate: a mixin config plugin's {@code onLoad} runs before the mod list
 * exists, so any loader query here is a crash risk. See {@link com.zurrtum.create.platform.EarlyModDetection}.
 *
 * <p>Concretely, versus upstream:
 * <ul>
 *   <li>{@code RegistriesMixin} -- NOT applied. It is the "Fabric API present, defer registration"
 *       path. {@code Create.Lazy} stays false.</li>
 *   <li>{@code CreativeModeTabMixin}, {@code CreativeModeTabsMixin}, {@code SavedDataStorageMixin},
 *       {@code IngredientMixin} -- applied. These are the self-contained implementations that stand
 *       in for the corresponding Fabric API modules.</li>
 *   <li>{@code MenuProviderMixin} -- dropped. Upstream adds it only when
 *       {@code fabric-networking-api-v1} is present, to adjust that module's behaviour; with no such
 *       module there is nothing to adjust.</li>
 *   <li>{@code ReloadListenerMixin} -- dropped, same reasoning for
 *       {@code fabric-resource-loader-v1}.</li>
 * </ul>
 *
 * <p>TODO(port): the two dropped mixins are the likeliest source of a missing-behaviour bug on
 * NeoForge -- menu opening and resource reload respectively. If either misbehaves, the fix is a
 * NeoForge-side equivalent, not re-adding these: they patch Fabric code that is not here.
 */
public class MixinPlugin implements IMixinConfigPlugin {
    private List<String> mixins;

    @Override
    public void onLoad(String mixinPackage) {
        Create.Lazy = false;
        mixins = new ArrayList<>(List.of(
            "CreativeModeTabMixin",
            "CreativeModeTabsMixin",
            "SavedDataStorageMixin",
            "IngredientMixin"
        ));
    }

    @Override
    public String getRefMapperConfig() {
        // 26.x is mojmap on every loader: no remap, so no refmap.
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
