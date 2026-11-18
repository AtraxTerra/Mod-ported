package com.zurrtum.create.platform;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.StringRepresentable;

import java.util.function.Supplier;

/**
 * Port-only: an enum codec that also reads the original Create's saves.
 *
 * <p>Create Fly stores enums by their serialized name ({@code "forward"}); the original Create --
 * and so Create: Adapted, whose worlds this port has to load -- stored {@code Enum.name()}
 * ({@code "FORWARD"}). Read with the plain {@code StringRepresentable.fromEnum} codec, every such
 * field failed with "Unknown element name", and with it the whole block entity's data (mechanical
 * crafters, ejectors, brass tunnels, sequenced gearshifts, elevators, schematicannons, chain
 * conveyors... in the migration test). This codec writes exactly what {@code fromEnum} writes and,
 * only when that fails to decode, accepts the constant's {@code name()}.
 *
 * <p>Applied to every {@code StringRepresentable.fromEnum(...)} in Create by
 * {@code tools/codemod_legacy_enums.py}; re-run it after an upstream sync.
 */
public final class LegacyEnumCodec {
    private LegacyEnumCodec() {
    }

    public static <E extends Enum<E> & StringRepresentable> Codec<E> of(Supplier<E[]> values) {
        Codec<E> current = StringRepresentable.fromEnum(values);
        E[] constants = values.get();
        Codec<E> legacy = Codec.STRING.comapFlatMap(name -> {
            for (E constant : constants) {
                if (constant.name().equals(name)) {
                    return DataResult.success(constant);
                }
            }
            return DataResult.error(() -> "Unknown element name:" + name);
        }, StringRepresentable::getSerializedName);
        return Codec.withAlternative(current, legacy);
    }
}
