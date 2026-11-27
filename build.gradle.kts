import java.util.zip.ZipFile

// NeoForge build of Create Fly: Trackwright (port/trackwright-neoforge-26.2). Upstream's Fabric build
// (Loom) lives on upstream/trackwright-fabric-26.2; the verification tasks at the end are upstream's.
plugins {
    // Same ModDevGradle as the Create Fly NeoForge port (gradle.properties: moddev_version).
    id("net.neoforged.moddev") version "2.0.148"
}

group = property("maven_group") as String
version = "${property("mod_version")}+neoforge-mc${property("mc_line")}"

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    mavenCentral()
}

// Upstream splits common and client code into two source sets (Loom's splitEnvironmentSourceSets), so
// common code cannot reach client classes at compile time. The NeoForge build keeps that split: both
// source sets make one mod, and one jar.
val main: SourceSet = sourceSets.main.get()
val clientSourceSet: SourceSet = sourceSets.create("client") {
    compileClasspath += main.output
    runtimeClasspath += main.output
}

// The NeoForge build of Create Fly, as a plain jar: compiled against, and loaded as a mod in runs.
val createFlyLibs = file(property("create_fly_libs") as String)
val createFly = fileTree(createFlyLibs) {
    include("create-fly-*.jar")
    exclude("*-sources.jar", "*-javadoc.jar")
}

neoForge {
    version = property("neoforge_version") as String
    // Create Fly's Container -> ContainerExtension injections, so its inventory API resolves here too.
    interfaceInjectionData.from(file("create-fly-interface-injection.json"))
    addModdingDependenciesTo(clientSourceSet)

    runs {
        register("client") {
            client()
            sourceSet = clientSourceSet
        }
        register("server") {
            server()
            // CI: -PprobeClasses links every common class of the mod on a dedicated server (ServerClassProbe).
            if (project.hasProperty("probeClasses")) {
                jvmArguments.add("-Dtrackwright.port.probeClasses=" + layout.buildDirectory.dir("classes/java/main").get().asFile.absolutePath)
            }
        }
    }

    mods {
        register("trackwright") {
            sourceSet(main)
            sourceSet(clientSourceSet)
        }
    }
}

val localRuntime: Configuration by configurations.creating
configurations.runtimeClasspath { extendsFrom(localRuntime) }
configurations.named(clientSourceSet.compileOnlyConfigurationName) { extendsFrom(configurations.compileOnly.get()) }
configurations.named(clientSourceSet.runtimeOnlyConfigurationName) { extendsFrom(localRuntime) }

dependencies {
    compileOnly(createFly)
    localRuntime(createFly)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.withType<AbstractCopyTask>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.processResources {
    val expansions = mapOf(
        "version" to project.version.toString(),
        "minecraft_version_range" to project.property("minecraft_version_range") as String,
        "neoforge_version_range" to project.property("neoforge_version_range") as String,
        "create_fly_version_range" to project.property("create_fly_version_range") as String,
    )
    expansions.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching("META-INF/neoforge.mods.toml") { expand(expansions) }
    // Upstream's Fabric metadata stays in the sources as the reference; NeoForge reads neoforge.mods.toml.
    exclude("fabric.mod.json")
}

tasks.jar {
    from(clientSourceSet.output)
    from("LICENSE")
    from("NOTICE")
}

tasks.named<Jar>("sourcesJar") {
    from(clientSourceSet.allSource)
    from("LICENSE")
    from("NOTICE")
}

val jarContainmentPrefix = "dev/chaevsfe/trackwright/"

val checkJarContainment = tasks.register("checkJarContainment") {
    description = "Fails if the mod jar or the sources jar carries a class, a source file or a nested jar outside this mod's own namespace."
    group = "verification"
    val archives = files(
        tasks.named<Jar>("jar").flatMap { it.archiveFile },
        tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile }
    )
    dependsOn(tasks.named("jar"), tasks.named("sourcesJar"))
    inputs.files(archives)
    val prefix = jarContainmentPrefix
    doLast {
        val offenders = mutableListOf<String>()
        var own = 0
        archives.forEach { archive ->
            ZipFile(archive).use { zip ->
                for (name in zip.entries().toList().map { it.name }) {
                    if (name.endsWith(".jar")) {
                        offenders += archive.name + " -> nested jar " + name
                        continue
                    }
                    if (!name.endsWith(".class") && !name.endsWith(".java")) continue
                    if (name.startsWith(prefix)) {
                        own++
                        continue
                    }
                    offenders += archive.name + " -> " + name
                }
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Trackwright must ship nothing outside " + prefix + "; found " + offenders.size +
                    " foreign entries:\n" + offenders.joinToString("\n") { "  " + it }
            )
        }
        logger.lifecycle("checkJarContainment: $own own class/source entries, 0 foreign, 0 nested jars")
    }
}

val checkTranslations = tasks.register("checkTranslations") {
    description = "Fails if the sources use a translation key that no language file defines, or if one of our keys is never used."
    group = "verification"
    val sources = fileTree("src") { include("**/*.java") }
    val ownLang = file("src/main/resources/assets/trackwright/lang/en_us.json")
    val compileClasspath = configurations.named("compileClasspath")
    inputs.files(sources)
    inputs.file(ownLang)
    doLast {
        val slurper = groovy.json.JsonSlurper()
        @Suppress("UNCHECKED_CAST")
        val own = (slurper.parse(ownLang) as Map<String, Any>).keys
        val createJar = compileClasspath.get().files.firstOrNull { it.name.startsWith("create-fly-") && it.name.endsWith(".jar") }
            ?: throw GradleException("Create Fly is not on the compile classpath")
        val createKeys = ZipFile(createJar).use { zip ->
            val entry = zip.getEntry("assets/create/lang/en_us.json")
                ?: throw GradleException("Create Fly ships no assets/create/lang/en_us.json")
            @Suppress("UNCHECKED_CAST")
            (zip.getInputStream(entry).use { slurper.parse(it) } as Map<String, Any>).keys
        }
        val createPrefix = "create.track."
        val literal = Regex("\"((?:create|trackwright)(?:\\.[a-z0-9_]+)+)\"")
        val category = Regex("Category\\.register\\(Identifier\\.fromNamespaceAndPath\\(Trackwright\\.MOD_ID, \"([a-z0-9_]+)\"\\)\\)")
        val used = sources.files.flatMap { source ->
            val text = source.readText()
            literal.findAll(text).map { it.groupValues[1] }.toList() +
                category.findAll(text).map { "key.category.trackwright." + it.groupValues[1] }.toList()
        }.toSortedSet()
        val missing = used.filter { key ->
            if (key.startsWith("trackwright.")) key !in own && (createPrefix + key) !in own
            else if (key.startsWith("key.")) key !in own
            else key !in own && key !in createKeys
        }
        val unused = own.filter { key -> key !in used && !(key.startsWith(createPrefix) && key.removePrefix(createPrefix) in used) }
        if (missing.isNotEmpty() || unused.isNotEmpty()) {
            throw GradleException(
                "Translation check failed:\n" +
                    missing.joinToString("") { "  missing: $it\n" } +
                    unused.joinToString("") { "  unused: $it\n" }
            )
        }
        logger.lifecycle("checkTranslations: ${used.size} keys used, ${own.size} own keys, all defined and used")
    }
}

tasks.named("check") {
    dependsOn(checkJarContainment)
    dependsOn(checkTranslations)
}
