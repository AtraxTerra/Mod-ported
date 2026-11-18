# Create Fly — Fabric 26.2 → NeoForge 26.2

Port of [ZurrTum/Create-Fly](https://github.com/ZurrTum/Create-Fly) (branch `26.2`, commit
`3a9fe3e`, Create 6.0.9) from Fabric to NeoForge, same Minecraft version.

> **Status:** builds, passes the bytecode audit of every mixin, boots a dedicated server and a
> headless client to the title screen, and takes over a world saved by Create: Adapted (blocks,
> block entity contents, contraptions) -- all checked in CI on every push. See
> [Running it](#1c-running-it-what-neoforges-patches-changed-under-the-mixins) and
> [Taking over a Create: Adapted world](#1d-taking-over-a-create-adapted-world).
> Still unchecked: real play on a client (rendering, GUIs, JEI screens); CC: Tweaked is off.

---

## 1. Why this port is smaller than the line count suggests

391,000 lines across 3,728 files sounds like a multi-month project. It is not, and the reasons are
worth stating because they decide where the remaining effort goes.

**It is loader-glue, not a version lift.** Source and target are both Minecraft 26.2 and both use
mojmap. Everything expensive about 26.2 — the retained-mode GUI rewrite (`GuiGraphics` →
`GuiGraphicsExtractor`), the in-world submit render pipeline, `CompoundTag` → `ValueInput`/
`ValueOutput` — ZurrTum already did. None of it is touched here. Mappings matching also means the
**185 mixins carry over unchanged**, and no refmap is needed on either side.

**Create Fly barely uses Fabric.** 71 of 3,728 files imported anything from `net.fabricmc`, and
between them they referenced only **56 distinct symbols**. Create's item logistics runs on vanilla
`Container`, extended by an injected `ContainerExtension` interface whose implementations live in a
mixin on `Container` itself. Its fluids run on Create's own `FluidInventory`. Neither touches Fabric.

**Fabric API was already optional.** The upstream build declares it `compileOnly`, and both
`MixinPlugin` classes branch on which Fabric modules are present — selecting a **self-contained
vanilla path** when they are absent. On NeoForge every such check answers "absent", so the mod
already chooses a path that ZurrTum wrote and maintains. This is the single biggest reason the
client side was tractable: *there is no Fabric Renderer API to reimplement, because a vanilla
rendering path already ships.*

**Registration is not loader-bound.** Create's content registers from `BuiltInRegistriesMixin` at
`BuiltInRegistries.freeze()`, i.e. from vanilla's own bootstrap rather than a loader hook.

Result: **3,393 files compile as-is; 175 are excluded as unreachable on this loader; 0 compiled
files still reference `net.fabricmc`.**

---

## 1b. The transfer layer, and the API that caught me out

The adapters were first written against NeoForge's `IItemHandler` / `IFluidHandler`. That is right
for 1.21.x and wrong for 26.2, which CI proved immediately: `Capabilities.ItemHandler` does not
exist. 26.2 replaced both with a unified resource API under `net.neoforged.neoforge.transfer`:

```java
Capabilities.Item.BLOCK   // BlockCapability<ResourceHandler<ItemResource>,  @Nullable Direction>
Capabilities.Fluid.BLOCK  // BlockCapability<ResourceHandler<FluidResource>, @Nullable Direction>
Capabilities.Fluid.ITEM   // ItemCapability<ResourceHandler<FluidResource>, ItemAccess>

public interface ResourceHandler<T extends Resource> {
    int  size();
    T    getResource(int index);
    long getAmountAsLong(int index);
    long getCapacityAsLong(int index, T resource);
    boolean isValid(int index, T resource);
    int  insert(int index, T resource, int amount, TransactionContext transaction);
    int  extract(int index, T resource, int amount, TransactionContext transaction);
}
```

Note the `TransactionContext`. 26.2 adopted a transaction model and immutable resource values, which
is Fabric's Transfer API design. So upstream's 4,000 lines of Fabric adapters were not dead weight
but the right starting point, and the layer is now a **port of them** rather than a rewrite.

**`ResourceView` is the hinge.** Fabric models a storage as a collection of `StorageView` objects;
NeoForge's handler is flat and takes an `int index`. Semantically identical, addressed differently.
`ResourceView` is a two-field record over (handler, index) that re-creates the view, so upstream's
call sites port by renaming types instead of being restructured around an index parameter.

`tools/codemod_transfer_api.py` does the rename pass and is re-runnable:

| Fabric | NeoForge 26.2 |
|---|---|
| `Storage` / `SlottedStorage` | `ResourceHandler` |
| `StorageView` / `SingleSlotStorage` | `ResourceView` (consuming) or a one-slot `ResourceHandler` (implementing) |
| `ItemVariant` / `FluidVariant` | `ItemResource` / `FluidResource` |
| `Transaction.openOuter()` | `Transaction.openRoot()` |
| `transaction.abort()` | nothing — close without commit rolls back |
| `SnapshotParticipant` | `SnapshotJournal` (`readSnapshot` → `revertToSnapshot`) |
| `StoragePreconditions` | `TransferPreconditions` |
| `ContainerStorage.of(c, null)` / `(c, side)` | `VanillaContainerWrapper.of(c)` / `new WorldlyContainerWrapper(c, side)` |
| `ContainerItemContext` | `ItemAccess` (`ItemAccess.forStack`) |
| `BlockApiCache` | `BlockCapabilityCache` |
| `*.SIDED.registerForBlockEntity` | `RegisterCapabilitiesEvent.registerBlockEntity` |
| amounts are `long` | amounts are `int` |

Four things needed judgement rather than a rename:

- **The `Direct`/`Slotted` split is gone.** It existed because Fabric's `Storage` came in an opaque
  iterable shape and an indexed one; `ResourceHandler` is always indexed, so `Direct` would have
  been unreachable code pretending to handle a case that no longer exists.
- **`Filter` is unreachable.** `ResourceHandler` exposes no insert/extract capability query to
  branch on, and its contract is that a refused operation returns 0. The 25
  `supportsInsertion`/`supportsExtraction` call sites route through two documented helpers on
  `ResourceView` so the assumption sits in one place.
- **A class that *implements* a one-slot storage cannot implement `ResourceView`,** which is a
  record. Those become `ResourceHandler` with `size() == 1` and an ignored index. The blanket
  `SingleSlotStorage` → `ResourceView` rename is right in a consuming position and wrong in an
  implementing one — this was the first pass's mistake.
- **`CombinedResourceHandler` cannot stand in for Fabric's `CombinedStorage`.** Its children are
  final and its javadoc says the wrapper must be recreated to follow a size change; Create's fluid
  inventories resize at runtime. Both impls hold their own part list, and the delegation lives in
  the `FluidInventoryStorage` interface as defaults.

**Fluid units are unchanged by any of this.** `FluidType.BUCKET_VOLUME` is 1000 against Create's
81000, so `FluidUnits` and its rounding rules stand, and remain the sharpest edge in the port.

### Two lessons worth keeping

**A falling error count can be a lie.** A regex that matched `DebugMessages.forGlobalPos(...)` up to
the first closing paren mangled a nested call. javac aborted on the syntax error and reported
`1 error` — down from 315, and meaningless. Always read what the remaining error *is*.

**`extendable` in an access widener strips `final`.** Translating it as a plain `public` access
transformer line applies cleanly and then fails far away: ten errors, including every
`cannot override` on `moveOrInterpolateTo`, `isLocalInstanceAuthoritative`,
`AbstractWidget.extractRenderState` and `Screen.init`, plus `cannot inherit from final Ingredient`.
NeoForge spells it `public-f`. Separately, an access widener implicitly opens the owning class when
a member is widened and an access transformer does not, so each member line needs one for its owner.
Both rules are documented in `tools/aw2at.py`.

## 1c. Running it: what NeoForge's patches changed under the mixins

Same Minecraft version, same mappings — and still most of the runtime work so far has been here.
Create Fly's mixins target *vanilla*; NeoForge runs a *patched* Minecraft, and its patches move,
rename or delete exactly the call sites Create hooks. Each mismatch compiles fine and is fatal only
when Mixin applies that class, so the server boot reported them one per launch.

### How they are found now

`tools/audit_mixin_targets.py` runs in CI right after compile. It reads the patched jar
ModDevGradle builds (`build/moddev/artifacts/minecraft-patched-<ver>.jar`) with `javap` and checks,
for every mixin a config or plugin activates: the target class, each injector's method, every
`INVOKE`/`FIELD`/`NEW` target (with ordinal and opcode), and `@Shadow`/`@Accessor`/`@Invoker`
members. It covers **client mixins too**, which a server boot never applies, and lists the
candidates when a method is missing (that is how the renumbered lambda below was retargeted without
another round trip). Its first run found 13 real problems at once; it is clean now.

The older `tools/audit_mixins_vs_patches.py` reads NeoForge's source patches and runs offline. It
cannot see `if (false)` guards — NeoForge disables some vanilla blocks that way; javac drops every
call inside, and the patch still shows those lines as unchanged context, often past the end of the
hunk. Two `LivingEntity.baseTick` targets died like that.

### The rule that keeps coming back: prefer the native hook

Most of Create Fly's injectors exist to call a Create interface from a vanilla method. NeoForge
patches many of those same methods to call **its own** extension hook instead — so the fix is
usually to implement that hook, not to re-aim the injector. Re-aiming would often *apply* and still
be wrong (the `FuelValues` case below), or erase NeoForge behaviour other mods rely on.

| Upstream mixin target | NeoForge replacement used |
|---|---|
| sound, friction, bounce, explosion resistance, weak power, scaffolding (`*ControlBlock` checks in `Entity`, `LivingEntity`, `Leashable`, boats, orbs, items, `ExplosionDamageCalculator`, `SignalGetter`) | the `*ControlBlock` interface extends `IBlockExtension` and overrides the hook (`getSoundType`, `getFriction`, `getBounceRestitution`, `getExplosionResistance`, `shouldCheckWeakPower`, `isScaffolding`) |
| `LivingEntity.baseTick` breathing (`isEyeInFluid`, `hasWaterBreathing`) | `LivingBreatheEvent` — `platform/BreathingHooks` |
| `ItemStack.useOn` (glue, symmetry) | one wrap around `CommonHooks.onPlaceItemIntoWorld`, the server-side path NeoForge routes through |
| `FuelValues.vanillaBurnTimes` | the `neoforge:furnace_fuels` data map (`data/neoforge/data_maps/item/furnace_fuels.json`), kept in step with `AllFuelTimes` by `tools/check_fuels.py` in CI. Retargeting the mixin would have applied and done nothing: on NeoForge that method only feeds datagen |
| `StructureTemplate.placeEntities` | `StructureProcessor.processEntity`, overridden by `EntityControlStructureProcessor` |
| `ReloadableServerResources.listeners()`, client `registerReloadListener` | `AddServerReloadListenersEvent` / `AddClientReloadListenersEvent` (`platform/CreateReloadListeners`, `client/platform/CreateClientReloadListeners`). NeoForge rejects listeners added any other way, fatally on the server |
| `GuiRenderer` PiP renderer map | `RegisterPictureInPictureRenderersEvent` — `client/platform/CreatePictureInPictureRenderers` |
| `Hud` train overlay | a GUI layer before the contextual bar that cancels the bar while it draws — `client/platform/TrainHudLayer` |
| `ModelBlockRenderer.tesselateBlock` (`@Overwrite`) | not overwritten: that would erase NeoForge's renderer patches for every block. `VanillinModelLayerMixin` keeps the one Vanillin call; `WrapperBlockStateModel` overrides the level-aware `collectParts`; enhanced AO is NeoForge's `EnhancedBlockModelLighter` |
| packs (`BuiltInPackSource`, `ClientPackSource`) | `AddPackFindersEvent` — `client/platform/CreatePackFinders` |
| OBJ models (`LoadBlockModelMixin`, `CuboidModelMixin`) | NeoForge's `neoforge:obj` loader |

Where no native hook exists the injector is re-aimed, with a `Port note` beside it saying why:
`StructureTemplate.addEntitiesToWorld`, `EntityFluidInteraction.update(Entity, Predicate)`,
`LevelExtractor.extractVisibleBlockEntities(..., Frustum)`, `BlockItem.getPlaceSound`,
`ServerPlayerGameMode.removeBlock`, and `lambda$startDestroyBlock$1`, whose captures NeoForge
changed. Upstream files whose mixin is fully replaced stay in the tree, excluded in `build.gradle`,
so upstream-sync PRs still show upstream's changes to them.

### Two more things the boot taught

**Nothing loader-related in a static initialiser.** `ModList.get()` is null while the `@Mod` class is
being constructed. The first boot died in `Create.<clinit>` reading the mod version.

**A config plugin's `getMixins()` applies on every side.** Fabric loads the whole client config on
the client only; NeoForge has no per-config environment, so the client `MixinPlugin` checks
`FMLEnvironment.getDist()` itself.

### Behaviour worth a second look

- Upstream's `SchematicProcessor.skip` returns `true` — drop — for entities that are creatable and
  not op-only, which is the set Create itself *keeps* on NeoForge. Kept as-is for parity; it looks
  like an inversion upstream and should be reported there before being "fixed" here.
- Copycat blocks on the flat (non-AO) render path now take light from their own state, not the
  material's. Emission is unaffected; only an emissive-rendering material would look different.

---

## 1d. Taking over a Create: Adapted world

The world this port is meant for was played with Create: Adapted (a NeoForge port of the original
Create 6.0.10). Registry ids are the same apart from two renames, aliased in `LegacyCreateIds`
(`create:simple_kinetic` block entity, `create:copper_nugget` item). The saved data is not: Create
Fly rewrote nearly all of Create's serialization, so the original layout read as garbage or, worse,
threw -- a mechanical crafter, chain conveyor or brass tunnel failed to load, a stock ticker could
no longer be saved, and every contraption (windmill, bearing, piston...) vanished with its blocks.

Two converters rewrite the original layout into Fly's before anything reads it:

- `LegacyBlockEntityData`, on `BlockEntity.loadStatic` (chunks, schematics, contraption
  disassembly);
- `LegacyEntityData`, on `Entity.load` for `create:` entities (through the tag behind the
  `TagValueInput`).

What they translate: `{X,Y,Z}` positions and `asLong()` positions, string UUIDs, int and
upper-case directions, JSON names, **millibuckets to droplets** (x81), NeoForge `ItemStackHandler`
inventories (Create: Adapted nests the stack: `{Slot, Stack:{...}}`) and Create `ItemSlots`
inventories into Fly's stack lists, nested LerpedFloat tank levels, pipe open ends, maps stored as
entry lists, `[{Pos}]` position lists, glue boxes. Each rewrite keys on a shape only the original
layout has, so a world Fly saved passes through untouched. Upper-case enum names are read by
`LegacyEnumCodec` instead. Three Fly readers that threw on bad input now report it and carry on.

`tools/migration-test.sh` (CI job `migration-test`) is the check: a production NeoForge server runs
Create: Adapted, places every Create block, fills the ones that hold things (hoppers, merged
fluids, a drain emptying a bucket), builds a bearing contraption with a glued chest, a powered shaft
line, a belt with items and a chest of Create items with data components, and saves; then the same
world boots with this jar and every block, block entity, item and fluid stack is compared. Current
result: nothing lost, no load or save error.

Known losses, all small:

- an item part-way through a **sequenced assembly** keeps its item but loses its progress (Fly
  tracks the sequence differently; `create:sequenced_assembly` is dropped);
- `create:shadow_steel` and `create:refined_radiance` do not exist in Create Fly and disappear;
- **trains** (graphs, signals, schedules in `create_tracks.dat`) are not covered by the test; the
  world this is for has no railway data;
- `TargetSpeed` of a speed controller (a ComputerCraft field) and items in flight from a weighted
  ejector are not carried over.

---

## 2. What changed

### Build and metadata

| Fabric | NeoForge |
|---|---|
| `fabric-loom` | ModDevGradle (`net.neoforged.moddev`) |
| `fabric.mod.json` | `META-INF/neoforge.mods.toml` |
| `create.classtweaker` (access widener, 283 lines) | `META-INF/accesstransformer.cfg` (281 lines), generated by `tools/aw2at.py` |
| `transitive-inject-interface` ×2 | `interface-injection.json` + ModDevGradle `interfaceInjectionData` |
| split `main` / `client` source sets | one source set; `@Mod(dist = Dist.CLIENT)` separates the client |
| no `pack.mcmeta` (loader synthesises one) | still none. An early version of this port added one with a bare `pack_format`; 26.x rejects that shape for formats past 81 (it wants `min_format`/`max_format`), and NeoForge, like the MDK, generates the metadata itself |

The access-widener converter is re-runnable: `python3 -I tools/aw2at.py <in.classtweaker> <out.cfg>`.
Re-run it if you rebase on upstream. `validateAccessTransformers = true` is on deliberately — a
stale AT line is otherwise a silent runtime failure.

### Entry points

`ModInitializer` / `ClientModInitializer` → two `@Mod` classes, both for mod id `create`, the client
one carrying `dist = Dist.CLIENT`. `Create.register()` is **not** called from the constructor: it
still runs from `BuiltInRegistriesMixin`, and calling it in both places would register everything
twice.

The `create_plugin` Fabric entrypoint became a **`ServiceLoader` service**
(`META-INF/services/com.zurrtum.create.api.registry.CreateRegisterPlugin`). This is the one piece of
Create's addon surface whose *declaration* differs between loaders; the interface is unchanged.

### The loader seam

Everything loader-specific is now in five small classes, and nothing else in the mod imports
`net.neoforged.fml`:

- `platform/Platform` — the 6 things the mod asked `FabricLoader` for, across 22 files.
- `platform/EarlyModDetection` — the same question from a mixin config plugin, where `ModList` does
  not exist yet. Reflective, fails to `false`. **Never use it for a hard dependency.**
- `platform/FluidUnits` — droplets ↔ millibuckets (see below).
- `platform/FluidRendering` — fluid tint colour.
- `client/platform/CreatePackFinders` — the optional built-in resource packs.

### Item and fluid transfer — the actual engineering

> The table and the reasoning in this subsection describe what was built against `IItemHandler`.
> **Section 1b supersedes it**: 26.2 uses `ResourceHandler`, and the conclusion about transactions
> disappearing is wrong for this version. The line counts are kept because they still show where
> the work is concentrated.

Create's core never spoke Fabric; a 13-file adapter layer in `infrastructure/transfer/` did. That
layer is replaced:

| Fabric original | lines | NeoForge replacement | lines |
|---|---|---|---|
| `InventoryWrapper` | 1991 | `ItemHandlerContainer` | ~190 |
| `FluidInventoryWrapper` | 1244 | `FluidHandlerInventory` | ~175 |
| `FluidItemInventoryWrapper` + `FluidItemContext` | 263 | `FluidHandlerItemInventory` | ~35 |
| `FluidInventoryStorage*` (3 files) | 236 | `FluidInventoryHandler` | ~120 |
| slot wrappers (3 files) | 291 | folded into the above | — |
| — | — | `ContainerItemHandler` (outbound, new) | ~140 |

**4,025 lines become about 660**, and not by cutting corners. Most of the original was Fabric
`Transaction` bookkeeping: Fabric's `Storage` mutates inside a transaction that may be rolled back,
so every wrapper had to snapshot and replay. NeoForge's handlers take a `simulate` flag
(`FluidAction.SIMULATE` for fluids), so a dry run is one call and the whole snapshot apparatus has
no counterpart.

The originals are kept, uncompiled, in `reference/fabric-transfer/` for behaviour comparison.

`AllTransfer` keeps **every public signature unchanged** — that is what lets several hundred call
sites compile untouched. Internally:

| Fabric | NeoForge |
|---|---|
| `ItemStorage.SIDED.find(...)` | `level.getCapability(Capabilities.ItemHandler.BLOCK, ...)` |
| `FluidStorage.SIDED.find(...)` | `Capabilities.FluidHandler.BLOCK` |
| `FluidStorage.ITEM.find(stack, ctx)` | `stack.getCapability(Capabilities.FluidHandler.ITEM)` |
| `BlockApiCache.create(...)` | `BlockCapabilityCache.create(...)` |
| `*.registerForBlockEntity(fn, type)` | `RegisterCapabilitiesEvent.registerBlockEntity(cap, type, fn)` |

Registration is two-phase. `register()` keeps its original early call site and only *records* what
to expose; `registerCapabilities(RegisterCapabilitiesEvent)`, wired on the mod event bus in
`Create`'s constructor, replays it when NeoForge is ready. This preserves Create's initialisation
order, which other registrations depend on.

### ⚠ Fluid units — the bug that will not announce itself

Create counts **droplets** (81,000 per bucket, the Fabric unit; the constant appears 20 times
upstream). NeoForge counts **millibuckets** (1,000 per bucket). The ratio is exactly 81.

Get this wrong and nothing throws — every fluid amount in the game is silently multiplied or divided
by 81, which presents as duplication or as tanks draining instantly, and is miserable to trace. All
conversion therefore goes through `FluidUnits` and nowhere else. The rules:

- Reporting Create's contents outward: round **down**. 1,040 droplets advertises as 12 mB.
- Accepting an amount inward: mB → droplets is exact.
- Giving fluid away: remove the droplet equivalent of the mB the receiver **actually took**, never
  the amount offered. Offer 1,040 droplets, receiver takes 12 mB, remove 972 — not 1,040. This is
  the rule that leaks fluid if ignored.
- Sub-millibucket amounts are refused rather than rounded up: rounding up creates fluid.

---

## 3. First build

Work in this order. Each stage is blind to what the next one catches.

1. **Versions are settled.** `moddev_version` (2.0.148) and `neoforge_version` (26.2.0.95) come
   from the official [MDK-26.2-ModDevGradle](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle)
   template, and CI confirmed ModDevGradle resolves and reaches
   `:createMinecraftArtifacts`. An earlier guess of `2.0.78` resolved too but ran its NeoForm step
   under Java 21, which 26.2 does not accept — so if that task fails again, the plugin version is
   the first thing to check, not the code.
2. **`./gradlew clean compileJava`.** Always `clean`: javac short-circuits per file, so unresolved
   imports hide body errors and a shrinking error count is not progress. Measure by a green compile,
   not by the error number going down.
3. The compile is green. Expect any new wave, after a rebase on upstream, in the **new code only** — the five `platform` classes, the five transfer
   adapters, `AllTransfer`, the three `Cached*InventoryBehaviour`s, the two entry points, the two
   `MixinPlugin`s. Everything else was already loader-neutral. Specific things to check against the
   pinned NeoForge build, because they move between versions:
   - `AddPackFindersEvent.addPackFinders(...)` parameters (`CreatePackFinders`)
   - `FMLLoader.isProduction()` vs `FMLEnvironment.production` (`Platform`)
   - `ModList.getSortedMods()` return type (`Platform`)
   - `IModInfo.getModProperties()` value types (`VanillinConfig`)
   - `IClientFluidTypeExtensions.getTintColor` signature (`FluidRendering`)
   - NeoForge `FluidStack` constructor: `Fluid` vs `Holder<Fluid>` (`FluidHandlerInventory`)
5. **Boot a dedicated server to `Done`.** This is the authoritative common-mixin check. Then grep
   the log for `FAILED during APPLY`, `was not applied`, `Found a remappable @Shadow` — **after every
   fix**, because mixin faults surface in waves, each hiding the next.
6. **Client mixins are invisible to a server boot.** A dedicated server never loads the `client`
   array. Launch a real client.
7. **Bake gate.** Resolve blockstate → model → parent → texture across the whole resource view.
   Anything purple or invisible is a bake failure, not a render bug.
8. **In-world QA.** Place every block, run fluid through pipes into and out of a foreign tank, push
   items through a hopper into a Create inventory and back. The fluid-unit rules above are what this
   is really testing.

---

## 4. Known gaps

Ordered by how likely they are to bite.

1. **Fluid data components are dropped at the boundary.** `FluidHandlerInventory.toNeoForge` carries
   fluid and amount but not components; the two sides use different component registries for
   fluid-specific data, so a faithful mapping needs a per-component table. Plain fluids are fine. A
   potion or other component-bearing fluid loses its components crossing into or out of Create.
2. **Convention tags.** Upstream bundles a frozen copy of `fabric-convention-tags-v2` and mounts it
   when Fabric API is absent. That is dropped — NeoForge supplies `c:` tags itself and the bundled
   copy would shadow them. **But the two projects do not agree on every tag name**, and a missing
   tag is a silently uncraftable recipe: no build error, no boot error. The `data/fabric/`
   directory still ships in the jar, unmounted, so a gap can be patched by mounting that
   subdirectory. Audit this against NeoForge's tag set.
3. **`getFluidInventory(ItemStack)` lifecycle.** On Fabric the result held a context that had to be
   closed to write the change back to the item; on NeoForge the capability owns a stack copy and the
   caller reads it back via `getContainer()`. `close()` is now a no-op. Call sites that *relied* on
   close() committing need review.
4. **`ContainerExtension` methods not overridden in `ItemHandlerContainer`.** The hot paths
   (`insert`, `extract`, `count`, `countSpace`, `extractAny`, `countAny`, `preciseInsert`,
   `preciseExtract`) delegate to `insertItem`/`extractItem`. The rest inherit the mixin defaults,
   which work by rewriting slots and so bypass a foreign handler's `getSlotLimit` and `isItemValid`.
   Override more as real misbehaviour shows up — not speculatively.
5. **Registration timing.** `BuiltInRegistriesMixin` calls `Create.register()` at
   `BuiltInRegistries.freeze()`. On Fabric that ordering is known-good. On NeoForge, confirm
   `freeze()` is reached after Create's classes are loadable; if not, move the call to
   `FMLConstructModEvent`.
6. **Two dropped Fabric-patching mixins.** `MenuProviderMixin` (patched
   `fabric-networking-api-v1`) and `ReloadListenerMixin` (patched `fabric-resource-loader-v1`).
   Nothing to patch here, but menu opening and resource reload are the two behaviours to watch. If
   either misbehaves, write a NeoForge equivalent — do not re-add these.
7. **JEI** is on. Its fluid ingredient is NeoForge's `FluidStack` in millibuckets, so fluid amounts
   cross through `JeiPlatform.toJei` / `fromJei`; Fabric's custom-ingredient calls are NeoForge's
   `Ingredient.getCustomIngredient()` / `DataComponentIngredient`. Checked by compiling and by the
   client boot test loading JEI; the recipe screens themselves want a look in game.
8. **CC: Tweaked is off** (`enable_cct = false`).
9. **Shader mods.** `QuadRenderHelperMixin` / `IrisPipelinesMixin` target Fabric Sodium and Iris
   internals and are excluded. Supporting a NeoForge shader mod means rewriting them.
10. **Vanillin addon overrides** now read `neoforge.mods.toml` mod properties instead of
    `fabric.mod.json` custom values, so an addon declares them differently. An un-updated addon
    contributes no overrides and degrades quietly.

---

## 5. Repo layout

```
build.gradle                     ModDevGradle; also the exclusion list with a reason per entry
gradle.properties                versions, matching the official 26.2 MDK
interface-injection.json         the 2 interfaces the access widener injected
src/main/resources/META-INF/
  neoforge.mods.toml             mod metadata, mixin configs, AT declaration
  accesstransformer.cfg          generated — do not hand-edit
  services/...CreateRegisterPlugin   replaces the create_plugin entrypoint
src/main/java/com/zurrtum/create/
  platform/                      the loader seam and native-hook listeners
  client/platform/               the same, client side
  infrastructure/transfer/       the capability bridge (5 classes)
reference/fabric-transfer/       the Fabric adapters this replaced; NOT compiled
tools/aw2at.py                   access widener → access transformer
tools/codemod_fabric_imports.py  re-runnable import codemod
tools/audit_mixin_targets.py     every mixin target vs the patched bytecode (CI)
tools/audit_mixins_vs_patches.py same, from source patches; offline, weaker
tools/check_fuels.py             furnace_fuels data map vs AllFuelTimes (CI)
tools/parse-check.sh             syntax-only javac pass, no Minecraft needed
tools/boot-test.sh               dedicated-server boot to `Done` (CI)
```

Both tools are idempotent — safe to re-run after rebasing on upstream.

---

## 6. Licensing

Upstream is CC0-1.0 (`LICENSE`) and ships third-party licences in `licenses/` (Create, Fabric,
Flywheel, NeoForge, Ponder); both are carried over unchanged. Note that upstream's CC0 declaration
covers ZurrTum's porting work — Create's own assets are bundled here under
`licenses/Create-LICENSE`, and that is the licence to read before redistributing a build.
