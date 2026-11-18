package com.zurrtum.create.foundation.utility;

import com.zurrtum.create.platform.Platform;

import java.nio.file.Path;

public class CreatePaths {
    // These are all absolute, so anything that is resolved via Path#resolve on these paths will also always be absolute
    //
    // Note on the loader call in a static initialiser: that pattern killed the first server boot
    // when it was in Create's own class, because FML instantiates that class while ModList is
    // still null. It is safe here. This class is only touched when a schematic path is needed,
    // which is long after startup, and FMLPaths is populated well before that. If this ever does
    // move earlier, make it lazy the way Create.getVersion() is.
    public static final Path GAME_DIR = Platform.getGameDir();
    public static final Path SCHEMATICS_DIR = GAME_DIR.resolve("schematics");
    public static final Path UPLOADED_SCHEMATICS_DIR = SCHEMATICS_DIR.resolve("uploaded");

    private CreatePaths() {
    }
}
