# Create Fly — NeoForge 26.2

[Create Fly](https://github.com/ZurrTum/Create-Fly) ported from Fabric to NeoForge, on Minecraft
26.2. Upstream is ZurrTum's Fabric port of [Create](https://github.com/Creators-of-Create/Create);
this repository carries the loader change only, on the same Minecraft version.

> **Status: not yet known to compile.** The port was written without access to the NeoForged and
> Mojang Maven repositories, so no dependency ever resolved and `javac` never ran over it. The
> **Build** workflow is where it is compiled for the first time — check its run summary, which
> lists the compile errors grouped by file.

**Read [PORTING.md](PORTING.md)** for what changed, the order to work through the first build, and
the 11 known gaps. The two sharp edges, in short: fluid amounts are droplets inside Create and
millibuckets in NeoForge (a factor of 81 that throws nothing when you get it wrong), and the `c:`
convention tags are now NeoForge's rather than a bundled Fabric snapshot, which can leave a recipe
silently uncraftable rather than erroring.

## Branches

| Branch | What it is |
|---|---|
| `port/neoforge-26.2` | the port. Sits directly on upstream's 26.2 commit, so the port is a readable diff and upstream merges work normally |
| `main` | untouched |

## Automation

| Workflow | When | What it does |
|---|---|---|
| [Build](.github/workflows/build.yml) | every push and PR, manual | compiles, builds the jar, uploads it as an artifact. On failure, writes an error summary grouped by file. A `v*` tag also publishes a release |
| [Upstream sync](.github/workflows/upstream-sync.yml) | daily, manual | watches upstream `26.2` and opens a PR when it moves |

Neither workflow pushes to `port/neoforge-26.2` directly; every upstream change arrives as a pull
request, and the Build workflow runs on it.

The sync applies upstream's diff as a **path-rewritten patch** rather than merging. That is not
laziness: the port merged upstream's two source sets (`src/main/java` and `src/client/java`) into
one, because NeoForge has no equivalent of a client source set. A `git merge` would therefore
conflict on the *path* of every file upstream touches under `src/client/`, even when the content
merges fine. The workflow instead rewrites those paths and applies with `git apply --3way`, which
still does a real per-file three-way merge. Hunks that will not apply are committed as `.rej` files
and listed in the PR.

Upstream changes **outside `src/`** are reported in the PR but never applied — those are the files
the port replaced (build scripts, Fabric metadata, the access widener), where a change needs a
decision rather than a patch.

The commit currently synced is recorded in [`.upstream-sync`](.upstream-sync); only ever point it at
a commit whose changes are actually in the tree.

## Building locally

Needs JDK 25 (Minecraft 26.x requires it on every loader).

```sh
./gradlew build          # jar lands in build/libs/
./gradlew runClient      # or runServer
```

If Gradle cannot resolve the ModDevGradle plugin, fix `moddev_version` in `gradle.properties` —
that value is a guess, see PORTING.md section 3.

## Licence

Upstream is CC0-1.0; third-party licences are in [`licenses/`](licenses). Create's own assets are
bundled here under [`licenses/Create-LICENSE`](licenses/Create-LICENSE) — read that one before
redistributing a build.
