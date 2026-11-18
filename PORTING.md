# Create Fly — Fabric 26.2 → NeoForge 26.2

Port of [ZurrTum/Create-Fly](https://github.com/ZurrTum/Create-Fly) (branch `26.2`, commit
`3a9fe3e`, Create 6.0.9) from Fabric to NeoForge, same Minecraft version.

> **This has never been compiled.** It was written without network access to the NeoForged, Mojang
> or Fabric Maven repositories, so Gradle could not resolve a single dependency and no `javac` ever
> ran over it. Treat every NeoForge API signature in the new code as *unverified*. Section
> [First build](#first-build) is the order to work through.

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

## 1b. ⚠ Correction: the transfer layer targets the wrong API

**Read this before touching `infrastructure/transfer/`.** The adapters in that package were written
against NeoForge's `IItemHandler` / `IFluidHandler`. That is the right API for NeoForge 1.21.x. It is
**not** the API in NeoForge 26.2, and CI proved it: `Capabilities.ItemHandler` does not exist.

26.2 replaced both with a unified resource-transfer API under
`net.neoforged.neoforge.transfer`:

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

**This is good news, and it inverts the plan.** Note the `TransactionContext`: 26.2 adopted a
transaction model, and immutable resource values (`ItemResource`, `FluidResource`) instead of
mutable stacks. That is essentially Fabric's Transfer API design. So the claim made in section 2
below -- that Fabric's transaction bookkeeping has no counterpart and collapses away -- **is wrong
for this version**, and the 4,025 lines of upstream Fabric adapters are not dead weight: they are
the right starting point, needing a largely mechanical type-rename rather than a rewrite.

They are preserved, uncompiled, in `reference/fabric-transfer/`. The mapping:

| Fabric Transfer API | NeoForge 26.2 |
|---|---|
| `Storage<ItemVariant>` | `ResourceHandler<ItemResource>` |
| `Storage<FluidVariant>` | `ResourceHandler<FluidResource>` |
| `SlottedStorage` / `SingleSlotStorage` | the indexed `ResourceHandler` itself |
| `ItemVariant` | `ItemResource` (`of(stack)`, `toStack(count)`) |
| `FluidVariant` | `FluidResource` |
| `Transaction` / `TransactionContext` | `net.neoforged.neoforge.transfer.transaction.TransactionContext` |
| `SnapshotParticipant` | `SnapshotJournal` |
| `CombinedStorage` | `CombinedResourceHandler` |
| `ContainerStorage.of(container, side)` | **`VanillaContainerWrapper`** — NeoForge ships it |
| `StoragePreconditions` | `TransferPreconditions` |
| `ContainerItemContext` | `ItemAccess` / `ItemAccessResourceHandler` |
| `ItemStorage.SIDED.registerForBlockEntity` | `RegisterCapabilitiesEvent.registerBlockEntity` |

`VanillaContainerWrapper` is worth singling out: it is NeoForge's own vanilla-`Container`-to-handler
adapter, so it replaces upstream's `ContainerStorage.of(...)` directly, and `ContainerItemHandler`
in this port exists only because the old API had no such thing.

**Fluid units still need converting.** `FluidType.BUCKET_VOLUME` is 1000 and Create counts 81000
per bucket, so `FluidUnits` and its rounding rules stand unchanged — that part of section 2 is
still correct, and still the sharpest edge in the port.

**What to do.** Delete the five adapters in `infrastructure/transfer/` (keeping `SlotRangeCache`),
restore the files from `reference/fabric-transfer/`, and run the rename table above over them. Then
point `AllTransfer` and the three `Cached*InventoryBehaviour`s at `ResourceHandler`. Expect this to
remove most of the 417 errors CI currently reports, since `AllTransfer` alone accounts for 39 of
them.

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
| no `pack.mcmeta` (loader synthesises one) | `pack.mcmeta` added, `pack_format` **88** (the 26.2 resource format) |

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
3. Expect the first wave in the **new code only** — the five `platform` classes, the five transfer
   adapters, `AllTransfer`, the three `Cached*InventoryBehaviour`s, the two entry points, the two
   `MixinPlugin`s. Everything else was already loader-neutral. Specific things to check against the
   pinned NeoForge build, because they move between versions:
   - `AddPackFindersEvent.addPackFinders(...)` parameters (`CreatePackFinders`)
   - `FMLLoader.isProduction()` vs `FMLEnvironment.production` (`Platform`)
   - `ModList.getSortedMods()` return type (`Platform`)
   - `IModInfo.getModProperties()` value types (`VanillinConfig`)
   - `IClientFluidTypeExtensions.getTintColor` signature (`FluidRendering`)
   - NeoForge `FluidStack` constructor: `Fluid` vs `Holder<Fluid>` (`FluidHandlerInventory`)
4. **Boot a dedicated server to `Done`.** This is the authoritative common-mixin check. Then grep
   the log for `FAILED during APPLY`, `was not applied`, `Found a remappable @Shadow` — **after every
   fix**, because mixin faults surface in waves, each hiding the next.
5. **Client mixins are invisible to a server boot.** A dedicated server never loads the `client`
   array. Launch a real client.
6. **Bake gate.** Resolve blockstate → model → parent → texture across the whole resource view.
   Anything purple or invisible is a bake failure, not a render bug.
7. **In-world QA.** Place every block, run fluid through pipes into and out of a foreign tank, push
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
7. **JEI is off** (`enable_jei = false`). The categories call Fabric's custom-ingredient API
   (`FabricIngredient`, `CustomIngredient`, `DefaultCustomIngredients`); switching JEI on means
   porting those to NeoForge `ICustomIngredient` first.
8. **CC: Tweaked is off** (`enable_cct = false`), same shape.
9. **Shader mods.** `QuadRenderHelperMixin` / `IrisPipelinesMixin` target Fabric Sodium and Iris
   internals and are excluded. Supporting a NeoForge shader mod means rewriting them.
10. **Vanillin addon overrides** now read `neoforge.mods.toml` mod properties instead of
    `fabric.mod.json` custom values, so an addon declares them differently. An un-updated addon
    contributes no overrides and degrades quietly.
11. **Data pack format.** `pack.mcmeta` carries the 26.2 **resource** format (88). The 26.2 **data**
    format is 107 — the two diverge in 26.x. Mod jars are lenient about this; if data-side resources
    are rejected, that is the number to reach for.

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
  platform/                      the loader seam (4 classes)
  client/platform/               client-side loader seam (1 class)
  infrastructure/transfer/       the capability bridge (5 classes)
reference/fabric-transfer/       the Fabric adapters this replaced; NOT compiled
tools/aw2at.py                   access widener → access transformer
tools/codemod_fabric_imports.py  re-runnable import codemod
```

Both tools are idempotent — safe to re-run after rebasing on upstream.

---

## 6. Licensing

Upstream is CC0-1.0 (`LICENSE`) and ships third-party licences in `licenses/` (Create, Fabric,
Flywheel, NeoForge, Ponder); both are carried over unchanged. Note that upstream's CC0 declaration
covers ZurrTum's porting work — Create's own assets are bundled here under
`licenses/Create-LICENSE`, and that is the licence to read before redistributing a build.
