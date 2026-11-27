package de.mrjulsen.mcdragonlib.client.model;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.LinkedList;
import java.util.List;

public abstract class ModelResourceLocationBuilder {

    private final Identifier location;

    public ModelResourceLocationBuilder(Identifier location) {
        this.location = location;
    }

    public Identifier getLocation() {
        return location;
    }

    public abstract ModelResourceLocation build();


    public static class Base extends ModelResourceLocationBuilder {

        public Base(Identifier location) {
            super(location);
        }

        @Override
        public ModelResourceLocation build() {
            return new ModelResourceLocation(getLocation(), "");
        }
    }

    public static class BlockState extends ModelResourceLocationBuilder {

        private final List<String> properties = new LinkedList<>();

        public BlockState(Identifier location) {
            super(location);
        }

        public <T extends Comparable<T>> BlockState with(Property<T> property, T value) {
            this.properties.add(property.getName() + "=" + property.getName(value));
            return this;
        }

        @Override
        public ModelResourceLocation build() {
            return new ModelResourceLocation(getLocation(), String.join(",", this.properties));
        }
    }

    public static class Inventory extends ModelResourceLocationBuilder {

        public Inventory(Identifier location) {
            super(location);
        }

        @Override
        public ModelResourceLocation build() {
            return new ModelResourceLocation(getLocation(), "inventory");
        }
    }
}
