package de.mrjulsen.mcdragonlib.data;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class ReadOnlyCompoundTag {
    private final CompoundTag delegate;

    public ReadOnlyCompoundTag(CompoundTag delegate) {
        this.delegate = delegate;
    }

    public Set<String> getAllKeys() {
        return delegate.keySet();
    }

    // NeoForge 26.2 port: CompoundTag's typed getters return Optional themselves now (empty when the
    // key is missing or holds another type), which is what these wrappers computed by hand.
    public Optional<Byte> getByte(String key) {
        return delegate.getByte(key);
    }

    public Optional<Short> getShort(String key) {
        return delegate.getShort(key);
    }

    public Optional<Integer> getInt(String key) {
        return delegate.getInt(key);
    }

    public Optional<Long> getLong(String key) {
        return delegate.getLong(key);
    }

    public Optional<Float> getFloat(String key) {
        return delegate.getFloat(key);
    }

    public Optional<Double> getDouble(String key) {
        return delegate.getDouble(key);
    }

    public Optional<String> getString(String key) {
        return delegate.getString(key);
    }

    public Optional<byte[]> getByteArray(String key) {
        return delegate.getByteArray(key);
    }

    public Optional<int[]> getIntArray(String key) {
        return delegate.getIntArray(key);
    }

    public Optional<long[]> getLongArray(String key) {
        return delegate.getLongArray(key);
    }

    public Optional<UUID> getUUID(String key) {
        // 26.2 has no CompoundTag#getUUID; UUIDs are int arrays read through UUIDUtil's codec.
        return delegate.read(key, UUIDUtil.CODEC);
    }

    public Optional<Boolean> getBoolean(String key) {
        return delegate.getBoolean(key);
    }

    public Optional<ReadOnlyCompoundTag> getCompound(String key) {
        return delegate.getCompound(key).map(ReadOnlyCompoundTag::new);
    }

    public Optional<ListTag> getList(String key, int tagType) {
        // 26.2 lists may mix element types; the 1.21.1 call returned only lists of tagType (an empty
        // list otherwise). Keep the elements of that type.
        return delegate.getList(key).map(list -> {
            ListTag filtered = new ListTag();
            for (Tag tag : list) {
                if (tag.getId() == tagType) {
                    filtered.add(tag);
                }
            }
            return filtered;
        });
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}

