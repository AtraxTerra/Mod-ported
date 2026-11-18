package com.jesz.createdieselgenerators.world;

import com.jesz.createdieselgenerators.CDGConfig;
import com.jesz.createdieselgenerators.CDGTags;
import com.jesz.createdieselgenerators.CreateDieselGenerators;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class OilChunksSavedData extends SavedData {

    public static final Identifier ID = CreateDieselGenerators.rl("oil_chunks");

    private record ChunkAmount(int x, int z, Optional<Integer> millibuckets) {
        static final Codec<ChunkAmount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(ChunkAmount::x),
            Codec.INT.fieldOf("z").forGetter(ChunkAmount::z),
            Codec.INT.optionalFieldOf("Amountmb").forGetter(ChunkAmount::millibuckets)
        ).apply(instance, ChunkAmount::new));

        int resolve() {
            return millibuckets.orElse(0);
        }
    }

    private static final Codec<OilChunksSavedData> LIST_CODEC = ChunkAmount.CODEC.listOf()
        .optionalFieldOf("OilChunks", List.of())
        .xmap(OilChunksSavedData::fromList, OilChunksSavedData::toList)
        .codec();

    // Port: "Create: Diesel Generators Adapted", the NeoForge 26.2 build this port replaces, saved
    // the chunks as one flat map, "x,z" -> mB, with no "OilChunks" list. A world coming from it is
    // read in that layout, and written back the same way, so the amounts survive the swap.
    private static final Codec<OilChunksSavedData> MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
        .xmap(OilChunksSavedData::fromMap, OilChunksSavedData::toMap);

    public static final Codec<OilChunksSavedData> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<OilChunksSavedData, T>> decode(DynamicOps<T> ops, T input) {
            boolean list = ops.getMap(input).result().map(map -> map.get("OilChunks") != null).orElse(false);
            return (list ? LIST_CODEC : MAP_CODEC).decode(ops, input);
        }

        @Override
        public <T> DataResult<T> encode(OilChunksSavedData input, DynamicOps<T> ops, T prefix) {
            return MAP_CODEC.encode(input, ops, prefix);
        }
    };

    public static final SavedDataType<OilChunksSavedData> TYPE = new SavedDataType<>(ID, OilChunksSavedData::new, CODEC, null);

    private final Map<ChunkPos, Integer> chunks = new HashMap<>();
    private ServerLevel level;

    public OilChunksSavedData() {
    }

    private static OilChunksSavedData fromList(List<ChunkAmount> list) {
        OilChunksSavedData data = new OilChunksSavedData();
        for (ChunkAmount entry : list)
            data.chunks.put(new ChunkPos(entry.x(), entry.z()), entry.resolve());
        return data;
    }

    private List<ChunkAmount> toList() {
        List<ChunkAmount> list = new ArrayList<>(chunks.size());
        chunks.forEach((pos, amount) -> list.add(new ChunkAmount(pos.x(), pos.z(), Optional.of(amount))));
        return list;
    }

    private static OilChunksSavedData fromMap(Map<String, Integer> map) {
        OilChunksSavedData data = new OilChunksSavedData();
        map.forEach((key, amount) -> {
            int comma = key.indexOf(',');
            if (comma < 0)
                return;
            try {
                data.chunks.put(new ChunkPos(Integer.parseInt(key.substring(0, comma).trim()),
                    Integer.parseInt(key.substring(comma + 1).trim())), amount);
            } catch (NumberFormatException ignored) {
            }
        });
        return data;
    }

    private Map<String, Integer> toMap() {
        Map<String, Integer> map = new HashMap<>(chunks.size());
        chunks.forEach((pos, amount) -> map.put(pos.x() + "," + pos.z(), amount));
        return map;
    }

    public static OilChunksSavedData load(ServerLevel level) {
        OilChunksSavedData data = level.getDataStorage().computeIfAbsent(TYPE);
        data.level = level;
        return data;
    }

    public void setChunkAmount(ChunkPos chunk, int amount) {
        chunks.put(chunk, amount);
        setDirty();
    }

    public void removeChunk(ChunkPos chunk) {
        chunks.remove(chunk);
        setDirty();
    }

    public int getChunkOilAmount(ChunkPos chunk) {
        Integer stored = chunks.get(chunk);
        if (stored != null)
            return stored > CDGConfig.server().OIL_CHUNK_INFINITE_THRESHOLD.get() ? Integer.MAX_VALUE : stored;
        return getBaseOilAmount(level, chunk);
    }

    public static int getChunkOilAmount(ServerLevel level, ChunkPos chunk) {
        return load(level).getChunkOilAmount(chunk);
    }

    public static void setChunkOilAmount(ServerLevel level, ChunkPos chunk, int amount) {
        load(level).setChunkAmount(chunk, amount);
    }

    public static void removeChunk(ServerLevel level, ChunkPos chunk) {
        load(level).removeChunk(chunk);
    }

    private static long noiseSeed;
    private static PerlinNoise cachedNoise;

    private static PerlinNoise noiseFor(long seed) {
        if (cachedNoise == null || noiseSeed != seed) {
            cachedNoise = PerlinNoise.create(RandomSource.create(seed), List.of(-2, -1, 0, 1));
            noiseSeed = seed;
        }
        return cachedNoise;
    }

    public static int getBaseOilAmount(ServerLevel level, ChunkPos chunk) {
        long seed = level.getSeed();
        List<Holder<Biome>> biomes = getBiomesInChunk(level, chunk);

        double scale = CDGConfig.server().OIL_CHUNK_SCALE.get();
        PerlinNoise noise = noiseFor(seed);
        float amount = (float) (noise.getValue(chunk.x() * scale, 0, chunk.z() * scale) + 1) / 1.6f;

        boolean isHighInOil = false;
        boolean isDenied = false;
        for (Holder<Biome> biome : biomes) {
            if (biome.is(CDGTags.OIL_BIOMES))
                isHighInOil = true;
            if (biome.is(CDGTags.DENY_OIL_BIOMES))
                isDenied = true;
        }

        if ((isHighInOil && CDGConfig.server().DISABLE_HIGH_OIL_CHUNKS.get()) ||
                (!isHighInOil && CDGConfig.server().DISABLE_NORMAL_OIL_CHUNKS.get()))
            return 0;

        if (isDenied)
            return 0;

        int max = (int) (7_000_000 * CDGConfig.server().OIL_MULTIPLIER.get());
        if (isHighInOil)
            max = (int) (7_000_000 * CDGConfig.server().HIGH_OIL_MULTIPLIER.get());

        amount = (float) Math.pow(amount, 2);
        amount *= max;

        if (amount < CDGConfig.server().OIL_CHUNK_THRESHOLD.get())
            return 0;

        if (amount > CDGConfig.server().OIL_CHUNK_INFINITE_THRESHOLD.get())
            return Integer.MAX_VALUE;
        return (int) amount;
    }

    public static List<Holder<Biome>> getBiomesInChunk(ServerLevel level, ChunkPos chunkPos) {
        Set<Holder<Biome>> set = new LinkedHashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int y = 60; y < 110; y++) {
                for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                    cursor.set(x, y, z);
                    set.add(level.getBiome(cursor));
                }
            }
        }
        return new ArrayList<>(set);
    }
}
