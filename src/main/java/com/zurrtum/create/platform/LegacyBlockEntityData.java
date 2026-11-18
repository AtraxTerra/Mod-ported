package com.zurrtum.create.platform;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Port-only: rewrites block entity NBT saved by the original Create (and so by Create: Adapted,
 * whose worlds this port has to take over) into Create Fly's layout, before the block entity reads
 * it. Hooked into {@code BlockEntity.loadStatic} by {@code BlockEntityLegacyDataMixin}, which every
 * chunk load and every schematic / contraption block placement goes through.
 *
 * <p>Create Fly rewrote most of Create's serialization for the {@code ValueInput} API; the data a
 * Create: Adapted world holds differs in three ways:
 * <ul>
 *   <li>value encodings: block positions as {@code {X,Y,Z}} instead of int arrays, UUIDs as strings
 *   instead of int arrays, directions as ints or {@code "NORTH"} instead of {@code "north"}, text as
 *   JSON strings, fluid amounts in millibuckets instead of droplets (81 per mB);</li>
 *   <li>inventories: NeoForge's {@code ItemStackHandler} ({@code {Size, Items:[{Slot,...}]}}) and
 *   Create's {@code ItemSlots} ({@code {size, items:{"0":...}}}) instead of Fly's plain stack lists;</li>
 *   <li>layouts: maps stored as lists of entries, LerpedFloat levels nested, keys renamed.</li>
 * </ul>
 * Every rewrite is keyed on a shape only the original layout has, so data Create Fly saved itself
 * passes through untouched and running this twice changes nothing. Sub-trees named
 * {@code components} (item and block data components) are never touched.
 *
 * <p>Checked by {@code tools/migration-test.sh} (CI job {@code migration-test}), which saves a world
 * with Create: Adapted and loads it with this port.
 */
public final class LegacyBlockEntityData {
    private static final Pattern UUID_STRING = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    /** Keys holding text a player typed; never read as a UUID even if they look like one. */
    private static final Set<String> TEXT_KEYS = Set.of(
        "Address", "PreviousAddress", "RecipeAddress", "Name", "CustomName", "Text", "Label", "Title");
    /** Keys whose (vanilla) Direction value the original Create wrote as an int or upper case. */
    private static final Set<String> DIRECTION_KEYS = Set.of(
        "PreferredSpoutput", "DisabledSpoutput", "Ringing", "StackEnteredFrom", "Flaps", "Sides", "InDirection");
    /** Keys whose vanilla enum value (DyeColor) the original Create wrote as {@code Enum.name()}. */
    private static final Set<String> LOWER_CASE_KEYS = Set.of("Dye");
    /** Keys holding a block position the original Create wrote as {@code BlockPos.asLong()}. */
    private static final Set<String> LONG_POS_KEYS = Set.of("LastKnownPosition");
    private static final Map<String, Consumer<CompoundTag>> BY_TYPE = new HashMap<>();

    private LegacyBlockEntityData() {
    }

    /** Rewrites {@code tag} in place when it is a Create block entity saved in the original layout. */
    public static CompoundTag upgrade(CompoundTag tag) {
        String id = tag.getStringOr("id", "");
        if (!id.startsWith("create:")) {
            return tag;
        }
        try {
            values(tag);
            text(tag, "CustomName");
            Consumer<CompoundTag> fixer = BY_TYPE.get(id.substring("create:".length()));
            if (fixer != null) {
                fixer.accept(tag);
            }
            behaviours(tag);
        } catch (RuntimeException e) {
            // never cost the player the block entity: whatever was not converted reads as before
            com.zurrtum.create.Create.LOGGER.warn("Could not convert legacy data of {} at {} {} {}", id,
                tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0), e);
        }
        return tag;
    }

    // --- value encodings, everywhere in the tree ---------------------------------------------------

    private static void values(CompoundTag tag) {
        for (String key : List.copyOf(tag.keySet())) {
            if (key.equals("components")) {
                continue;
            }
            Tag value = tag.get(key);
            Tag fixed = value(key, value);
            if (fixed != value) {
                tag.put(key, fixed);
            }
        }
    }

    private static Tag value(String key, Tag value) {
        if (value instanceof CompoundTag compound) {
            int[] pos = blockPos(compound);
            if (pos != null) {
                return new IntArrayTag(pos);
            }
            // TransportedItemStack (belts, depots, drains, tunnels): Create: Adapted calls its stack "Stack"
            if (compound.contains("InSegment") && compound.get("Stack") instanceof CompoundTag stack && !compound.contains(
                "Item")) {
                compound.remove("Stack");
                compound.put("Item", stack);
            }
            values(compound);
            return compound;
        }
        if (value instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                Tag element = list.get(i);
                Tag fixed = value(key, element);
                if (fixed != element) {
                    list.set(i, fixed);
                }
            }
            return list;
        }
        if (value instanceof StringTag(String s)) {
            if (!TEXT_KEYS.contains(key) && UUID_STRING.matcher(s).matches()) {
                return new IntArrayTag(UUIDUtil.uuidToIntArray(UUID.fromString(s)));
            }
            if (DIRECTION_KEYS.contains(key)) {
                Direction d = Direction.byName(s.toLowerCase(Locale.ROOT));
                return d == null ? value : StringTag.valueOf(d.getSerializedName());
            }
            if (LOWER_CASE_KEYS.contains(key) && !s.equals(s.toLowerCase(Locale.ROOT))) {
                return StringTag.valueOf(s.toLowerCase(Locale.ROOT));
            }
            return value;
        }
        if (LONG_POS_KEYS.contains(key) && value instanceof LongTag(long packed)) {
            return new IntArrayTag(new int[]{
                net.minecraft.core.BlockPos.getX(packed), net.minecraft.core.BlockPos.getY(packed),
                net.minecraft.core.BlockPos.getZ(packed)});
        }
        if (DIRECTION_KEYS.contains(key) && value instanceof NumericTag number) {
            return StringTag.valueOf(Direction.from3DDataValue(number.asInt().orElse(0)).getSerializedName());
        }
        return value;
    }

    /** {@code {X:1,Y:2,Z:3}} (and nothing else) -> {@code [I;1,2,3]}. */
    private static int[] blockPos(CompoundTag compound) {
        if (compound.size() != 3 || !(compound.get("X") instanceof NumericTag x) || !(compound.get(
            "Y") instanceof NumericTag y) || !(compound.get("Z") instanceof NumericTag z)) {
            return null;
        }
        return new int[]{x.asInt().orElse(0), y.asInt().orElse(0), z.asInt().orElse(0)};
    }

    // --- behaviours shared by many block entities ---------------------------------------------------

    private static void behaviours(CompoundTag tag) {
        for (String key : List.copyOf(tag.keySet())) {
            // SmartFluidTankBehaviour: [{TankContent, Level:{Value,Target,Speed}}] -> [{TankContent, Value, Target, Speed}]
            if (key.endsWith("Tanks") && tag.get(key) instanceof ListTag tanks) {
                for (Tag t : tanks) {
                    if (t instanceof CompoundTag tank && tank.get("Level") instanceof CompoundTag level) {
                        tank.remove("Level");
                        for (String k : level.keySet()) {
                            tank.put(k, level.get(k));
                        }
                        CompoundTag fluid = fluidOf(tank.get("TankContent"));
                        if (fluid != null) {
                            millibucketsToDroplets(fluid);
                            tank.put("TankContent", fluid);
                        } else {
                            tank.remove("TankContent");
                        }
                    }
                }
            }
            // FluidTransportBehaviour: one compound per pipe side; its open end changed layout
            if (Direction.byName(key) != null && tag.get(key) instanceof CompoundTag side && side.get(
                "OpenEnd") instanceof CompoundTag openEnd && openEnd.contains("Location")) {
                CompoundTag fixed = new CompoundTag();
                fixed.putBoolean("Pulling", openEnd.getBooleanOr("Pulling", false));
                CompoundTag fluid = fluidOf(openEnd.contains("Tank") ? openEnd.get("Tank") : openEnd);
                if (fluid != null) {
                    millibucketsToDroplets(fluid);
                    fixed.put("Fluid", fluid);
                }
                fixed.putString("Direction", openEnd.getCompoundOrEmpty("Location").getStringOr("direction", key));
                side.put("OpenEnd", fixed);
            }
        }
        // DepotBehaviour (depots, ejectors, stations): output buffer became a plain stack list
        if (tag.contains("OutputBuffer") && !tag.contains("Inventory")) {
            tag.put("Inventory", compact(slots(tag.get("OutputBuffer"))));
            tag.remove("OutputBuffer");
        }
        // ServerSidedFilteringBehaviour (brass tunnels): filters keyed by an int side
        if (tag.get("Filters") instanceof ListTag filters) {
            for (Tag t : filters) {
                if (t instanceof CompoundTag filter && filter.get("Side") instanceof NumericTag side) {
                    filter.putString("Side", Direction.from3DDataValue(side.asInt().orElse(0)).getSerializedName());
                }
            }
        }
    }

    // --- per block entity type ------------------------------------------------------------------------

    static {
        type(t -> {
            // InputItems / OutputItems handlers -> Inventory {Input:[...], Output:[...]}
            if (t.contains("InputItems") || t.contains("OutputItems")) {
                CompoundTag inventory = new CompoundTag();
                inventory.put("Input", compact(slots(t.get("InputItems"))));
                inventory.put("Output", compact(slots(t.get("OutputItems"))));
                t.put("Inventory", inventory);
                t.remove("InputItems");
                t.remove("OutputItems");
            }
        }, "basin");
        type(t -> {
            if (t.contains("TankContent")) {
                CompoundTag fluid = fluidOf(t.get("TankContent"));
                t.remove("TankContent");
                if (fluid != null) {
                    millibucketsToDroplets(fluid);
                    t.put("Fluid", fluid);
                }
            }
        }, "fluid_tank", "creative_fluid_tank");
        type(t -> {
            // the hose pulley's internal tank
            if (t.contains("Tank") && !t.contains("Fluid")) {
                CompoundTag fluid = fluidOf(t.get("Tank"));
                t.remove("Tank");
                if (fluid != null) {
                    millibucketsToDroplets(fluid);
                    t.put("Fluid", fluid);
                }
            }
        }, "hose_pulley");
        type(t -> {
            if (t.get("Inventory") instanceof CompoundTag) {
                t.put("Inventory", compact(slots(t.get("Inventory"))));
            }
        }, "item_vault");
        type(t -> {
            if (t.contains("InputInventory") || t.contains("OutputInventory")) {
                List<CompoundTag> all = new ArrayList<>(slots(t.get("InputInventory"), 1));
                all.addAll(slots(t.get("OutputInventory"), 9));
                t.put("Inventory", positional(all));
                t.remove("InputInventory");
                t.remove("OutputInventory");
            }
        }, "millstone");
        type(t -> {
            // ProcessingInventory: slot 0 is the input, kept in place; outputs follow, compacted
            if (t.get("Inventory") instanceof CompoundTag inv) {
                List<CompoundTag> stacks = slots(inv);
                ListTag list = new ListTag();
                list.add(stacks.isEmpty() || stacks.getFirst() == null ? new CompoundTag() : stacks.getFirst());
                for (int i = 1; i < stacks.size(); i++) {
                    if (stacks.get(i) != null) {
                        list.add(stacks.get(i));
                    }
                }
                t.put("Inventory", list);
                for (String k : List.of("ProcessingTime", "RecipeTime", "AppliedRecipe")) {
                    if (inv.contains(k)) {
                        t.put(k, inv.get(k));
                    }
                }
            }
        }, "saw", "crushing_wheel_controller");
        type(t -> {
            if (t.get("Inventory") instanceof CompoundTag inv) {
                List<CompoundTag> stacks = slots(inv, 1);
                t.put("Stack", stacks.getFirst() == null ? new CompoundTag() : stacks.getFirst());
                t.remove("Inventory");
            }
            groupedItems(t, "GroupedItems");
        }, "mechanical_crafter");
        type(t -> {
            if (t.get("Inventory") instanceof CompoundTag) {
                t.put("Inventory", positional(slots(t.get("Inventory"))));
            }
        }, "schematic_table", "schematicannon");
        type(t -> {
            // ItemStackHandler entries already carry the Slot ItemStackWithSlot wants
            if (t.get("Inventory") instanceof CompoundTag inv) {
                t.put("Inventory", withSlots(slots(inv)));
            }
        }, "package_postbox", "package_frogport");
        type(t -> {
            if (t.get("Inventory") instanceof CompoundTag inv && inv.get("Items") instanceof ListTag) {
                List<CompoundTag> stacks = slots(inv);
                CompoundTag items = new CompoundTag();
                for (int i = 0; i < stacks.size(); i++) {
                    if (stacks.get(i) != null) {
                        items.put(Integer.toString(i), stacks.get(i));
                    }
                }
                CompoundTag itemSlots = new CompoundTag();
                itemSlots.put("items", items);
                itemSlots.putInt("size", stacks.size());
                inv.remove("Size");
                inv.put("Items", itemSlots);
            }
            text(t, "CustomName");
        }, "toolbox");
        type(t -> {
            // the fake player's inventory list -> {Inventory: [...]}
            if (t.get("Inventory") instanceof ListTag list) {
                CompoundTag inventory = new CompoundTag();
                inventory.put("Inventory", list);
                t.put("Inventory", inventory);
            }
        }, "deployer");
        type(t -> {
            for (String key : List.of("Targets", "FilteredTargets")) {
                if (t.get(key) instanceof ListTag targets) {
                    ListTag fixed = new ListTag();
                    for (Tag e : targets) {
                        if (e instanceof CompoundTag target && target.contains("Pos")) {
                            CompoundTag pair = new CompoundTag();
                            pair.put("first", target.get("Pos"));
                            pair.putString("second",
                                Direction.from3DDataValue(target.getIntOr("Face", 0)).getSerializedName());
                            fixed.add(pair);
                        } else {
                            fixed.add(e);
                        }
                    }
                    t.put(key, fixed);
                }
            }
        }, "brass_tunnel", "andesite_tunnel");
        type(t -> {
            // [{Target, Packages}] -> {Keys:[Target...], Values:[Packages...]}
            if (t.get("TravellingPackages") instanceof ListTag list) {
                ListTag keys = new ListTag(), values = new ListTag();
                for (Tag e : list) {
                    if (e instanceof CompoundTag entry && entry.contains("Target")) {
                        keys.add(entry.get("Target"));
                        values.add(entry.getListOrEmpty("Packages"));
                    }
                }
                CompoundTag map = new CompoundTag();
                map.put("Keys", keys);
                map.put("Values", values);
                t.put("TravellingPackages", map);
            }
        }, "chain_conveyor");
        type(t -> {
            if (t.get("ReceivedPayments") instanceof CompoundTag payments) {
                t.put("Inventory", compact(slots(payments)));
                t.remove("ReceivedPayments");
            }
            // [{Id, Indices}] -> {"<uuid>": [indices]}
            if (t.get("HiddenCategories") instanceof ListTag list) {
                CompoundTag map = new CompoundTag();
                for (Tag e : list) {
                    if (e instanceof CompoundTag entry) {
                        UUID player = uuid(entry.get("Id"));
                        if (player != null) {
                            ListTag indices = new ListTag();
                            for (int i : entry.getIntArray("Indices").orElse(new int[0])) {
                                indices.add(IntTag.valueOf(i));
                            }
                            map.put(player.toString(), indices);
                        }
                    }
                }
                t.put("HiddenCategories", map);
            }
        }, "stock_ticker");
    }

    static {
        type(t -> {
            if (t.get("Facing") instanceof NumericTag facing) {
                // a horizontal index (get2DDataValue), not a 3D one
                t.putString("Facing", Direction.from2DDataValue(Math.floorMod(facing.asInt().orElse(0), 4)).getSerializedName());
            }
        }, "table_cloth");
        type(t -> text(t, "PrevTrainName"), "track_station");
    }

    private static void type(Consumer<CompoundTag> fixer, String... ids) {
        for (String id : ids) {
            BY_TYPE.merge(id, fixer, Consumer::andThen);
        }
    }

    // --- helpers --------------------------------------------------------------------------------------

    /** RecipeGridHandler.GroupedItems: {Grid:[{x,y,item}]} -> {Grid:{Keys:[{first,second}], Values:[item]}}. */
    private static void groupedItems(CompoundTag t, String key) {
        if (t.get(key) instanceof CompoundTag grouped && grouped.get("Grid") instanceof ListTag grid) {
            ListTag keys = new ListTag(), values = new ListTag();
            for (Tag e : grid) {
                if (e instanceof CompoundTag cell) {
                    CompoundTag pair = new CompoundTag();
                    pair.putInt("first", cell.getIntOr("x", 0));
                    pair.putInt("second", cell.getIntOr("y", 0));
                    keys.add(pair);
                    values.add(cell.getCompoundOrEmpty("item"));
                }
            }
            CompoundTag map = new CompoundTag();
            map.put("Keys", keys);
            map.put("Values", values);
            grouped.put("Grid", map);
        }
    }

    private static List<CompoundTag> slots(Tag inventory) {
        return slots(inventory, 0);
    }

    /**
     * The stacks of an inventory in either original layout, by slot, {@code null} for an empty slot:
     * an ItemStackHandler {@code {Size, Items:[{Slot, Stack:{id, count, components}}]}} (NeoForge's own
     * inlines the stack next to {@code Slot}; both are read) or Create's
     * ItemSlots {@code {size, items:{"<slot>": {id, count, components}}}}. At least {@code minSize} long.
     */
    private static List<CompoundTag> slots(Tag inventory, int minSize) {
        List<CompoundTag> out = new ArrayList<>();
        if (inventory instanceof CompoundTag inv) {
            int size = Math.max(inv.getIntOr("Size", 0), inv.getIntOr("size", 0));
            if (inv.get("Items") instanceof ListTag items) {
                for (Tag e : items) {
                    if (e instanceof CompoundTag entry) {
                        int slot = entry.getIntOr("Slot", out.size());
                        // Create: Adapted nests the stack: {Slot, Stack:{id, count}}; NeoForge inlines it
                        set(out, slot, entry.get("Stack") instanceof CompoundTag nested ? nested : entry);
                    }
                }
            } else if (inv.get("items") instanceof CompoundTag items) {
                for (String k : items.keySet()) {
                    try {
                        set(out, Integer.parseInt(k), items.getCompoundOrEmpty(k));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            while (out.size() < size) {
                out.add(null);
            }
        }
        while (out.size() < minSize) {
            out.add(null);
        }
        return out;
    }

    private static void set(List<CompoundTag> out, int slot, CompoundTag stack) {
        if (slot < 0 || slot > 4096) {
            return;
        }
        while (out.size() <= slot) {
            out.add(null);
        }
        CompoundTag copy = stack.copy();
        copy.remove("Slot");
        out.set(slot, isEmpty(copy) ? null : copy);
    }

    private static boolean isEmpty(CompoundTag stack) {
        String id = stack.getStringOr("id", "minecraft:air");
        return id.equals("minecraft:air") || stack.getIntOr("count", 1) <= 0;
    }

    /** Non-empty stacks in slot order (Fly's ItemStackHandler, vaults, depots, basins). */
    private static ListTag compact(List<CompoundTag> stacks) {
        ListTag list = new ListTag();
        for (CompoundTag stack : stacks) {
            if (stack != null) {
                list.add(stack);
            }
        }
        return list;
    }

    /** Every slot, {@code {}} for an empty one (read with ItemStack.OPTIONAL_CODEC, by position). */
    private static ListTag positional(List<CompoundTag> stacks) {
        ListTag list = new ListTag();
        for (CompoundTag stack : stacks) {
            list.add(stack == null ? new CompoundTag() : stack);
        }
        return list;
    }

    /** Non-empty stacks with their {@code Slot} (ItemStackWithSlot). */
    private static ListTag withSlots(List<CompoundTag> stacks) {
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.size(); i++) {
            if (stacks.get(i) != null) {
                CompoundTag stack = stacks.get(i).copy();
                stack.putInt("Slot", i);
                list.add(stack);
            }
        }
        return list;
    }

    /**
     * The fluid stack in an original tank NBT: NeoForge's FluidTank wrote {@code {Fluid:{id,amount}}},
     * a bare FluidStack is {@code {id,amount}}; {@code null} when empty. Returns a copy.
     */
    private static CompoundTag fluidOf(Tag tag) {
        if (!(tag instanceof CompoundTag compound)) {
            return null;
        }
        CompoundTag fluid = compound.get("Fluid") instanceof CompoundTag inner ? inner : compound;
        String id = fluid.getStringOr("id", "minecraft:empty");
        if (id.equals("minecraft:empty") || fluid.getIntOr("amount", 0) <= 0) {
            return null;
        }
        return fluid.copy();
    }

    private static void millibucketsToDroplets(CompoundTag fluid) {
        if (fluid.get("amount") instanceof NumericTag amount) {
            long droplets = (long) amount.asInt().orElse(0) * FluidUnits.DROPLETS_PER_BUCKET / FluidUnits.MILLIBUCKETS_PER_BUCKET;
            fluid.putInt("amount", (int) Math.min(Integer.MAX_VALUE, droplets));
        }
    }

    private static UUID uuid(Tag tag) {
        if (tag instanceof IntArrayTag array && array.getAsIntArray().length == 4) {
            return UUIDUtil.uuidFromIntArray(array.getAsIntArray());
        }
        if (tag instanceof StringTag(String s) && UUID_STRING.matcher(s).matches()) {
            return UUID.fromString(s);
        }
        return null;
    }

    /** A JSON text component string (how the original Create stored names) -> an NBT component. */
    private static void text(CompoundTag t, String key) {
        if (!(t.get(key) instanceof StringTag(String s)) || s.isEmpty() || "{[\"".indexOf(s.charAt(0)) < 0) {
            return;
        }
        try {
            ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(s))
                .flatMap(c -> ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, c))
                .ifSuccess(nbt -> t.put(key, nbt));
        } catch (JsonParseException ignored) {
            // not JSON after all: Create Fly reads a plain string as literal text
        }
    }
}
