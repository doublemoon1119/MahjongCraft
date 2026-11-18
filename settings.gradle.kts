pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net") { name = "Fabric" }
        gradlePluginPortal()
    }
    plugins {
        val loomVersion: String by settings
        id("fabric-loom").version(loomVersion)
        val kotlinVersion: String by System.getProperties()
        kotlin("jvm").version(kotlinVersion)
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
