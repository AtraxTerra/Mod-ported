#!/usr/bin/env python3
"""Commands that give the migration test's Create blocks something to carry across the switch.

Usage: migration_fill.py grid.tsv <half> fill.cmds unfill.cmds extra.cmds fresh.cmds

fill.cmds puts a hopper of items on top of the blocks that hold items (the hopper feeds them through
their item capability, the way a player's machines get filled), merges fluids into the tanks, and
builds a few extras on a row just outside the grid: a bearing contraption, a powered shaft line, a
three-segment belt fed by a hopper, and a chest of Create items carrying data components.
The NBT written here is the original Create's format (what Create: Adapted reads).

unfill.cmds removes the hoppers again once they are empty, so that phase B does not see items they
would keep feeding in. extra.cmds holds the `data get` / `execute if` commands that read the extras back in both phases.
"""
import sys

grid_file, half, fill_file, unfill_file, extra_file, fresh_file = sys.argv[1], int(sys.argv[2]), *sys.argv[3:7]
pos = {}
for line in open(grid_file):
    x, y, z, block = line.rstrip("\n").split("\t")
    pos[block] = (int(x), int(y), int(z))

ITEMS = '{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:6},{Slot:1b,id:"minecraft:wheat",count:6}]}'
HOPPER_FED = {
    "create:item_vault": ITEMS, "create:basin": ITEMS, "create:depot": ITEMS, "create:weighted_ejector": ITEMS, "create:millstone": ITEMS,
    "create:mechanical_crafter": ITEMS, "create:schematic_table": '{Items:[{Slot:0b,id:"create:empty_schematic",count:3}]}',
    "create:schematicannon": '{Items:[{Slot:0b,id:"minecraft:gunpowder",count:5}]}', "create:white_postbox": ITEMS,
    "create:package_frogport": ITEMS, "create:deployer": ITEMS, "create:smart_chute": ITEMS, "create:chute": ITEMS,
    "create:blaze_burner": '{Items:[{Slot:0b,id:"minecraft:coal",count:4}]}', "create:white_toolbox": ITEMS,
}
LEVEL = "Level:{Value:0.5f,Target:0.5f,Speed:0.25f}"
MERGES = {
    "create:fluid_tank": '{TankContent:{id:"minecraft:water",amount:4000}}',
    "create:creative_fluid_tank": '{TankContent:{id:"minecraft:lava",amount:1000}}',
    # Create: Adapted keeps a smart tank's fluid under "Fluid" (seen in a drain it filled itself)
    "create:spout": '{Tanks:[{Fluid:{id:"minecraft:water",amount:500},%s}]}' % LEVEL,
    "create:basin": '{InputTanks:[{Fluid:{id:"minecraft:lava",amount:250},%s},{%s}]}' % (LEVEL, LEVEL),
    # a drain empties the bucket it holds into its own tank, so that tank is written by Create: Adapted
    "create:item_drain": '{HeldItem:{Stack:{id:"minecraft:water_bucket",count:1},InSegment:0,InDirection:1,'
                  'Pos:0.5f,PrevPos:0.5f,Offset:0.0f,PrevOffset:0.0f,Angle:0}}',
    "create:placard": '{Item:{id:"minecraft:diamond",count:1}}',
    "create:copycat_panel": '{Item:{id:"minecraft:oak_planks",count:1},Material:{Name:"minecraft:oak_planks"}}',
}

# Diesel Generators (when its blocks are in the grid): fuel in its engines and tanks. Guesses at
# the layout Create: Adapted's port reads; the phase A dump shows which ones took. 500 mB stays
# under every tank's capacity (a merge is not capped, a load is).
CDG_FLUID = '{Tanks:[{Fluid:{id:"createdieselgenerators:diesel",amount:500},%s}],TankContent:{id:"createdieselgenerators:diesel",amount:500}}' % LEVEL
for block in ("diesel_engine", "large_diesel_engine", "huge_diesel_engine", "oil_barrel", "canister",
              "distillation_tank", "bulk_fermenter", "burner", "chemical_turret"):
    MERGES["createdieselgenerators:" + block] = CDG_FLUID

fill, unfill = [], []
for block, items in HOPPER_FED.items():
    if block in pos:
        x, y, z = pos[block]
        fill.append(f"setblock {x} {y + 1} {z} minecraft:hopper[facing=down]{items}")
        unfill.append(f"setblock {x} {y + 1} {z} minecraft:air")
for block, nbt in MERGES.items():
    if block in pos:
        x, y, z = pos[block]
        fill.append(f"data merge block {x} {y} {z} {nbt}")

zr = half + 4
y = -60

def bearing(z, chest_items):
    """A plank and a chest of items, glued together, on a bearing that a creative motor turns: the
    bearing assembles them into a contraption entity, the chest becoming mounted storage."""
    return [f"setblock 0 {y + 2} {z} minecraft:oak_planks",
            f"setblock 0 {y + 3} {z} minecraft:chest{chest_items}",
            f"summon create:super_glue 0 {y + 2} {z} {{From:[0.0d,0.0d,0.0d],To:[1.0d,2.0d,1.0d]}}",
            f"setblock 0 {y} {z} create:creative_motor[facing=up]",
            f"setblock 0 {y + 1} {z} create:mechanical_bearing[facing=up]"]


fill += bearing(zr, ITEMS)
# a chest of Create items with data components (and ids that were renamed or removed)
components = ",".join([
    '{Slot:0b,id:"create:incomplete_precision_mechanism",count:1,components:{"create:sequenced_assembly":'
    '{id:"create:sequenced_assembly/precision_mechanism",step:1,progress:0.2f}}}',
    '{Slot:1b,id:"create:copper_backtank",count:1,components:{"create:banktank_air":300}}',
    '{Slot:2b,id:"create:filter",count:1,components:{"create:filter_items":[{slot:0,item:{id:"minecraft:iron_ingot",count:1}}]}}',
    '{Slot:3b,id:"create:copper_nugget",count:5}',
    '{Slot:4b,id:"create:shadow_steel",count:2}',
    '{Slot:5b,id:"create:andesite_alloy",count:7}',
    '{Slot:6b,id:"create:white_toolbox",count:1,components:{"create:toolbox_inventory":'
    '{items:{items:{"0":{id:"minecraft:iron_ingot",count:4}},size:32},filters:[{},{},{},{},{},{},{},{}]}}}',
])
fill.append(f"setblock 4 {y} {zr} minecraft:chest{{Items:[{components}]}}")
# a motor driving a line of shafts: every shaft records where its rotation comes from
fill.append(f"setblock 8 {y} {zr} create:creative_motor[facing=east]")
fill += [f"setblock {x} {y} {zr} create:shaft[axis=x]" for x in range(9, 13)]
# a three-segment belt, its controller fed by a hopper
for i, part in enumerate(("start", "middle", "end")):
    nbt = f'{{Controller:{{X:16,Y:{y},Z:{zr}}},IsController:{"1b" if i == 0 else "0b"},Length:3,Index:{i}}}'
    fill.append(f"setblock {16 + i} {y} {zr} create:belt[facing=east,slope=horizontal,part={part}]{nbt}")
fill.append(f"setblock 16 {y + 1} {zr} minecraft:hopper[facing=down]{ITEMS}")
unfill.append(f"setblock 16 {y + 1} {zr} minecraft:air")

extra = [
    f"execute if entity @e[type=create:stationary_contraption]",
    f"data get entity @e[type=create:stationary_contraption,x=0,y={y + 2},z={zr},distance=..3,limit=1]",
    f"data get block 0 {y + 1} {zr}",
    f"data get block 4 {y} {zr}",
    f"data get block 8 {y} {zr}",
    f"data get block 11 {y} {zr}",
    f"data get block 16 {y} {zr}",
    f"data get block 18 {y} {zr}",
]
open(fill_file, "w").write("\n".join(fill) + "\n")
open(unfill_file, "w").write("\n".join(unfill) + "\n")
# phase B only: the same bearing built fresh under Create Fly, for its own contraption layout
zf = zr + 4
open(fresh_file, "w").write("\n".join(bearing(zf, ITEMS)) + "\n")
open(fresh_file + ".get", "w").write(f"data get entity @e[type=create:stationary_contraption,x=0,y={y + 2},z={zf},distance=..3,limit=1]\n")
open(extra_file, "w").write("\n".join(extra) + "\n")
