package com.zurrtum.create.client.vanillin;


public class VanillinXplatImpl implements VanillinXplat {
    @Override
    public boolean isDevelopmentEnvironment() {
        return Platform.isDevelopmentEnvironment();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return Platform.isModLoaded(modId);
    }
}
