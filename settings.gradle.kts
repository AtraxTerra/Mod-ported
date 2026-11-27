// NeoForge build of Create Fly: Steam 'n' Rails (port/snr-neoforge-26.2). The upstream Fabric
// settings live on upstream/snr-fabric-26.2.
pluginManagement {
    repositories {
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Railway"
