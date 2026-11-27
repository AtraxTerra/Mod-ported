package de.mrjulsen.mcdragonlib.util.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import de.mrjulsen.mcdragonlib.data.INBTSerializable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

public class DLRegistry<T extends INBTSerializable & IRegisterable<T>> {
    private final Map<Identifier, DLRegistryObject<? extends T>> TYPES = new HashMap<>();

    public <S extends T> DLRegistryObject<S> register(Identifier id, Supplier<S> factory) {
        DLRegistryObject<S> type = new DLRegistryObject<>(id, factory);
        TYPES.put(id, type);
        return type;
    }

    @SuppressWarnings("unchecked")
    private DLRegistryObject<T> get(Identifier id) {
        return (DLRegistryObject<T>)TYPES.get(id);
    }

    public T load(CompoundTag tag) {
        return DLRegistryObject.load(tag, this::get);
    }

}
