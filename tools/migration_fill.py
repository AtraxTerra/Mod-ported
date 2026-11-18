#!/usr/bin/env python3
"""Commands that give the migration test's Create blocks something to carry across the switch.

Usage: migration_fill.py grid.tsv <half> fill.cmds extra.cmds

fill.cmds puts a hopper of items on top of the blocks that hold items (the hopper feeds them through
their item capability, the way a player's machines get filled), merges fluids into the tanks, and
builds a few extras on a row just outside the grid: a bearing contraption, a powered shaft line, a
three-segment belt fed by a hopper, and a chest of Create items carrying data components.
The NBT written here is the original Create's format (what Create: Adapted reads).

extra.cmds holds the `data get` / `execute if` commands that read the extras back in both phases.
"""
import sys

grid_file, half, fill_file, extra_file = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4]
pos = {}
for line in open(grid_file):
    x, y, z, block = line.rstrip("\n").split("\t")
    pos[block.split(":", 1)[1]] = (int(x), int(y), int(z))

ITEMS = '{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:6},{Slot:1b,id:"minecraft:wheat",count:6}]}'
HOPPER_FED = {
    "item_vault": ITEMS, "basin": ITEMS, "depot": ITEMS, "weighted_ejector": ITEMS, "millstone": ITEMS,
    "mechanical_crafter": ITEMS, "schematic_table": '{Items:[{Slot:0b,id:"create:empty_schematic",count:3}]}',
    "schematicannon": '{Items:[{Slot:0b,id:"minecraft:gunpowder",count:5}]}', "white_postbox": ITEMS,
    "package_frogport": ITEMS, "deployer": ITEMS, "smart_chute": ITEMS, "chute": ITEMS,
    "item_drain": '{Items:[{Slot:0b,id:"minecraft:water_bucket",count:1}]}',
    "blaze_burner": '{Items:[{Slot:0b,id:"minecraft:coal",count:4}]}', "white_toolbox": ITEMS,
}
LEVEL = "Level:{Value:0.5f,Target:0.5f,Speed:0.25f}"
MERGES = {
    "fluid_tank": '{TankContent:{id:"minecraft:water",amount:4000}}',
    "creative_fluid_tank": '{TankContent:{id:"minecraft:lava",amount:1000}}',
    "spout": '{Tanks:[{TankContent:{id:"minecraft:water",amount:500},%s}]}' % LEVEL,
    "basin": '{InputTanks:[{TankContent:{id:"minecraft:lava",amount:250},%s},{%s}]}' % (LEVEL, LEVEL),
    "placard": '{Item:{id:"minecraft:diamond",count:1}}',
    "copycat_panel": '{Item:{id:"minecraft:oak_planks",count:1},Material:{Name:"minecraft:oak_planks"}}',
}

fill = []
for block, items in HOPPER_FED.items():
    if block in pos:
        x, y, z = pos[block]
        fill.append(f"setblock {x} {y + 1} {z} minecraft:hopper[facing=down]{items}")
for block, nbt in MERGES.items():
    if block in pos:
        x, y, z = pos[block]
        fill.append(f"data merge block {x} {y} {z} {nbt}")

zr = half + 4
y = -60
# a bearing on a creative motor turns a plank: the bearing assembles it into a contraption entity
fill += [f"setblock 0 {y} {zr} create:creative_motor[facing=up]",
         f"setblock 0 {y + 1} {zr} create:mechanical_bearing[facing=up]",
         f"setblock 0 {y + 2} {zr} minecraft:oak_planks"]
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

extra = [
    f"execute if entity @e[type=create:contraption]",
    f"data get entity @e[type=create:contraption,limit=1]",
    f"data get block 0 {y + 1} {zr}",
    f"data get block 4 {y} {zr}",
    f"data get block 8 {y} {zr}",
    f"data get block 11 {y} {zr}",
    f"data get block 16 {y} {zr}",
    f"data get block 18 {y} {zr}",
]
open(fill_file, "w").write("\n".join(fill) + "\n")
open(extra_file, "w").write("\n".join(extra) + "\n")
