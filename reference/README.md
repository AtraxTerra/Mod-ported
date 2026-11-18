# Reference copies, not compiled

The Fabric Transfer API adapters this port replaced. They are kept out of `src/` so they do not
compile, and kept in the repo so the behaviour they implemented can be checked against the NeoForge
replacements in `com.zurrtum.create.infrastructure.transfer`.

| Fabric original | lines | NeoForge replacement |
|---|---|---|
| `InventoryWrapper` | 1991 | `ItemHandlerContainer` |
| `FluidInventoryWrapper` | 1244 | `FluidHandlerInventory` |
| `FluidItemInventoryWrapper` + `FluidItemContext` | 263 | `FluidHandlerItemInventory` |
| `FluidInventoryStorage` / `...Impl` / `Sided...` | 236 | `FluidInventoryHandler` |
| `FluidInventorySlotWrapper` / `Sided...` / `SingleFluidStackStorage` | 291 | folded into the two handlers above |

Most of the bulk was Fabric `Transaction` bookkeeping -- snapshot, commit, roll back. NeoForge's
handlers take a `simulate` / `FluidAction.SIMULATE` flag instead, so that machinery has no
counterpart and is simply gone.
