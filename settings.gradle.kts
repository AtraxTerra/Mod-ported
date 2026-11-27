// NeoForge 26.2 build of DragonLib (port/dragonlib-neoforge-26.2). Upstream's Architectury build
// (common + fabric + neoforge projects, Architectury Loom) lives on upstream/dragonlib-1.21.1.
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

rootProject.name = "DragonLib"
