# Create Fly: Diesel Generators

Oil prospecting and pumpjacks, distillation, diesel engines, chemical sprayers, fueled tools, cement and asphalt.

> [!IMPORTANT]
> **This is an unofficial port.** It is not affiliated with or endorsed by
> George VI. 
> Please report problems here.

## This branch: NeoForge 26.2

`port/cdg-neoforge-26.2` ports chaevsfe's Fabric version to **NeoForge 26.2**, on top of the
Create Fly NeoForge port (`port/neoforge-26.2` of this repository). The CI builds Create Fly, then
this mod, and on every push:

- boots a dedicated server with both;
- runs the **world migration test**: a world saved with Create: Adapted and "Create: Diesel
  Generators Adapted" is loaded with Create Fly and this port, and every block, item and fluid is
  compared (engines, barrels, distillation tanks, fermenters, canisters and turrets keep their fuel;
  `CDGLegacyData` holds this mod's conversions);
- boots a server and a headless client with the survival server's mod list (see Create Fly's
  `PORTING.md`, section 1e).

The oil left in each chunk (`data/createdieselgenerators/oil_chunks.dat`) is outside that test.
Create: Diesel Generators Adapted saved it as a flat `"x,z"` -> mB map; `OilChunksSavedData` reads
that layout as well as this port's `OilChunks` list, and writes the map, so a world keeps its
amounts across the swap (checked on a world whose oil fields were set under the Adapted mod).

| Component | Version |
| --- | --- |
| Minecraft | `26.2` |
| Mod loader | NeoForge `26.2.0.93` or newer |
| Create Fly (NeoForge port) | `6.0.9` or newer |
| Java | `25` or newer |
| Environments | Client and server |

The jars are the Build workflow's artifacts (`createdieselgenerators-neoforge-<commit>`, which also
holds the Create Fly jar it was built against).

Not ported yet: the Strut Your Stuff and recipe-viewer integrations (`deferred/excludes.txt`).

## Building

```
git clone -b port/neoforge-26.2 https://github.com/AtraxTerra/Mod-ported deps/create-fly
(cd deps/create-fly && ./gradlew jar)
./gradlew build -Pcreate_fly_libs=deps/create-fly/build/libs
```

Requires JDK 25.

## Credits

- **Create: Diesel Generators** by George VI
- **Create Fly** by ZurrTum
- **Create** by the Create Team

## Reporting Bugs
When reporting bugs, always include the version number of the mod.  If you're reporting a crash, include your client or server log depending on where the crash occurred.
