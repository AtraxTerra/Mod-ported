package com.zurrtum.create.platform;

import com.zurrtum.create.Create;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;

import java.util.Locale;
import java.util.UUID;

/**
 * Port-only: rewrites the NBT of a Create entity saved by the original Create / Create: Adapted
 * into Create Fly's layout before the entity loads ({@code EntityLegacyDataMixin}, on
 * {@code Entity.load}). The value encodings are the block entities' ones
 * ({@link LegacyBlockEntityData#values}); contraptions also store several maps as lists of entries
 * and several position lists as {@code [{Pos: ...}]}.
 *
 * <p>Without this a contraption from such a world -- a windmill, a bearing, a piston, a cart --
 * failed to load ({@code Anchor} was {@code {X,Y,Z}}, read with {@code orElseThrow}) and vanished
 * with every block it carried. Same rules as the block entity side: keyed on shapes only the
 * original layout has, idempotent, {@code components} never touched.
 */
public final class LegacyEntityData {
    private LegacyEntityData() {
    }

    public static void upgrade(CompoundTag tag) {
        try {
            LegacyBlockEntityData.values(tag);
            if (tag.get("Contraption") instanceof CompoundTag contraption) {
                contraption(contraption);
            }
        } catch (RuntimeException e) {
            Create.LOGGER.warn("Could not convert legacy data of entity {}", tag.getStringOr("id", "?"), e);
        }
    }

    private static void contraption(CompoundTag c) {
        // [{Id, Seat}] -> {"<uuid>": seat}
        if (c.get("Passengers") instanceof ListTag list) {
            CompoundTag map = new CompoundTag();
            for (Tag e : list) {
                if (e instanceof CompoundTag entry && uuid(entry.get("Id")) instanceof UUID id) {
                    map.putInt(id.toString(), entry.getIntOr("Seat", 0));
                }
            }
            c.put("Passengers", map);
        }
        // [{Id, Location}] -> {"<uuid>": {pos, direction}}
        if (c.get("SubContraptions") instanceof ListTag list) {
            CompoundTag map = new CompoundTag();
            for (Tag e : list) {
                if (e instanceof CompoundTag entry && uuid(entry.get("Id")) instanceof UUID id
                    && entry.get("Location") instanceof CompoundTag face) {
                    map.put(id.toString(), blockFace(face));
                }
            }
            c.put("SubContraptions", map);
        }
        flattenPositions(c, "Seats");
        flattenPositions(c, "Interactors");
        if (c.get("CapturedMultiblocks") instanceof ListTag multiblocks) {
            for (Tag e : multiblocks) {
                if (e instanceof CompoundTag multiblock) {
                    flattenPositions(multiblock, "Parts");
                }
            }
        }
        // [{From:[x,y,z], To:[x,y,z]}] -> [[minX, minY, minZ, maxX, maxY, maxZ]]
        if (c.get("Superglue") instanceof ListTag glue) {
            ListTag boxes = new ListTag();
            for (Tag e : glue) {
                if (e instanceof CompoundTag box && box.get("From") instanceof ListTag from && box.get("To") instanceof ListTag to) {
                    ListTag fixed = new ListTag();
                    for (ListTag corner : new ListTag[]{from, to}) {
                        for (int i = 0; i < 3; i++) {
                            fixed.add(DoubleTag.valueOf(i < corner.size() ? corner.get(i).asDouble().orElse(0d) : 0d));
                        }
                    }
                    boxes.add(fixed);
                } else {
                    boxes.add(e);
                }
            }
            c.put("Superglue", boxes);
        }
    }

    /** {@code [{Pos: p}, ...]} -> {@code [p, ...]} (BLOCK_POS_LIST_CODEC). */
    private static void flattenPositions(CompoundTag tag, String key) {
        if (tag.get(key) instanceof ListTag list && !list.isEmpty() && list.get(0) instanceof CompoundTag) {
            ListTag fixed = new ListTag();
            for (Tag e : list) {
                if (e instanceof CompoundTag entry && entry.get("Pos") instanceof Tag pos) {
                    fixed.add(pos);
                }
            }
            tag.put(key, fixed);
        }
    }

    /** catnip BlockFace: {@code {Pos, Face:"NORTH"}} -> {@code {pos, direction:"north"}}. */
    private static Tag blockFace(CompoundTag face) {
        if (!face.contains("Pos")) {
            return face;
        }
        CompoundTag fixed = new CompoundTag();
        fixed.put("pos", face.get("Pos"));
        Tag dir = face.get("Face");
        Direction d = dir instanceof NumericTag n ? Direction.from3DDataValue(n.asInt().orElse(0))
            : Direction.byName(dir == null ? "" : dir.asString().orElse("").toLowerCase(Locale.ROOT));
        fixed.putString("direction", (d == null ? Direction.NORTH : d).getSerializedName());
        return fixed;
    }

    private static UUID uuid(Tag tag) {
        if (tag instanceof IntArrayTag array && array.getAsIntArray().length == 4) {
            return UUIDUtil.uuidFromIntArray(array.getAsIntArray());
        }
        if (tag instanceof StringTag(String s)) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }
}
