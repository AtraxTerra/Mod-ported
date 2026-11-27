// NeoForge 26.2 build of DragonLib (port/dragonlib-neoforge-26.2).
//
// Upstream builds three Architectury Loom projects: common, fabric and neoforge. This port targets
// NeoForge only, so one ModDevGradle project compiles upstream's common and neoforge sources together
// (same folders as upstream, which keeps later upstream syncs readable). The fabric folder stays in
// the tree as upstream's reference and is not built.
plugins {
    id("net.neoforged.moddev") version "2.0.148"
}

group = property("maven_group") as String
version = "${property("mod_version")}+neoforge-mc${property("minecraft_version")}"

base {
    archivesName.set("${property("archives_name")}-neoforge")
}

repositories {
    mavenCentral()
    maven("https://maven.architectury.dev/") {
        name = "Architectury"
        content { includeGroup("dev.architectury") }
    }
}

val main: SourceSet = sourceSets.main.get()
main.java.setSrcDirs(listOf("common/src/main/java", "neoforge/src/main/java"))
main.resources.setSrcDirs(listOf("common/src/main/resources", "neoforge/src/main/resources"))

neoForge {
    version = property("neoforge_version") as String
    // Upstream's access widener (common/src/main/resources/dragonlib.accesswidener), as a NeoForge
    // access transformer. Architectury did this conversion at remapJar time.
    accessTransformers.from(file("neoforge/src/main/resources/META-INF/accesstransformer.cfg"))

    runs {
        register("client") {
            client()
        }
        register("server") {
            server()
            // CI: -PprobeClasses links every class of the mod on a dedicated server (ServerClassProbe).
            if (project.hasProperty("probeClasses")) {
                jvmArguments.add("-Ddragonlib.port.probeClasses=" + layout.buildDirectory.dir("classes/java/main").get().asFile.absolutePath)
            }
        }
    }

    mods {
        register(property("mod_id") as String) {
            sourceSet(main)
        }
    }
}

dependencies {
    // Upstream bundles Architectury in its NeoForge jar; on 26.2 it is a separate mod (the server
    // already runs it), declared as a dependency in neoforge.mods.toml.
    implementation("dev.architectury:architectury-neoforge:${property("architectury_api_version")}")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    // CI reads every error of a pass, not javac's default first hundred.
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "100000"))
}

tasks.withType<AbstractCopyTask>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.processResources {
    // Upstream's expansion map (root build.gradle), with the NeoForge 26.2 values.
    val expansions = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to project.property("minecraft_version") as String,
        "datapack_version" to project.property("datapack_version") as String,
        "issues" to project.property("issues") as String,
        "source" to project.property("source") as String,
        "license" to project.property("license") as String,
        "modid" to project.property("archives_name") as String,
        "display_name" to project.property("display_name") as String,
        "homepage" to project.property("homepage") as String,
        "authors" to project.property("authors") as String,
        "description" to project.property("description") as String,
        "icon" to project.property("icon") as String,
        "discord" to project.property("discord") as String,
        "next_unsupported_minecraft_version" to project.property("next_unsupported_minecraft_version") as String,
        "dragonlib_version" to "${project.property("minecraft_version")}-${project.property("mod_version")}",
        "neoforge_loader_version" to project.property("neoforge_loader_version") as String,
        "neoforge_dependency_version" to project.property("neoforge_dependency_version") as String,
        "java_version" to project.property("java_version") as String,
        "architectury_api_version" to project.property("architectury_api_version") as String,
        "maven_group" to project.property("maven_group") as String,
    )
    expansions.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching(listOf("pack.mcmeta", "META-INF/neoforge.mods.toml", "dragonlib.mixins.json")) { expand(expansions) }
    // Architectury metadata and the access widener mean nothing to NeoForge (the access transformer
    // above replaces the widener).
    exclude("architectury.common.json", "dragonlib.accesswidener", ".cache")
}

tasks.jar {
    from("LICENSE")
    manifest {
        attributes(
            "Specification-Title" to project.property("display_name").toString(),
            "Specification-Vendor" to project.property("authors").toString(),
            "Specification-Version" to "1",
            "Implementation-Title" to project.property("display_name").toString(),
            "Implementation-Version" to project.version.toString(),
            "Implementation-Vendor" to project.property("authors").toString(),
            "Built-On" to project.property("minecraft_version").toString(),
        )
    }
}

tasks.named<Jar>("sourcesJar") {
    from("LICENSE")
}
