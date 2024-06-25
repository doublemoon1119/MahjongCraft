plugins {
    id("fabric-loom")
    val kotlinVersion: String by System.getProperties()
    kotlin("jvm").version(kotlinVersion)
    kotlin("plugin.serialization").version(kotlinVersion)
}

base {
    val minecraftVersion: String by project
    val archivesBaseName: String by project
    archivesName.set("$archivesBaseName-mc$minecraftVersion")
}

val modVersion: String by project
version = modVersion
val mavenGroup: String by project
group = mavenGroup

repositories {
    maven(url = "https://server.bbkr.space/artifactory/libs-release") { name = "CottonMC" }
    maven(url = "https://jitpack.io")  //Mahjong4j
    maven(url = "https://maven.shedaniel.me/") //Cloth Config
    maven(url = "https://maven.terraformersmc.com/") //Mod Menu
}

dependencies {
    val minecraftVersion: String by project
    minecraft("com.mojang:minecraft:$minecraftVersion")
    val yarnMappings: String by project
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    val loaderVersion: String by project
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    val fabricVersion: String by project
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")
    val fabricKotlinVersion: String by project
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")
    // Mahjong4J (https://github.com/mahjong4j/mahjong4j)
    include("com.github.mahjong4j:mahjong4j:0.3.2")?.let { modImplementation(it) }
    // LibGui (https://github.com/cottonmc/libgui)
    val libguiVersion: String by project
    include("io.github.cottonmc:LibGui:$libguiVersion")?.let { modImplementation(it) }
    val clothConfigVersion: String by project
    // Cloth Config API (https://www.curseforge.com/minecraft/mc-mods/cloth-config)
    modApi("me.shedaniel.cloth:cloth-config-fabric:$clothConfigVersion") {
        exclude(group = "net.fabricmc.fabric-api")
    }
    // Mod Menu (https://www.curseforge.com/minecraft/mc-mods/modmenu)
    val modMenuVersion: String by project
    modImplementation("com.terraformersmc:modmenu:$modMenuVersion")
}

tasks {
    val javaVersion = JavaVersion.VERSION_21
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(javaVersion.toString().toInt())
        sourceCompatibility = "$javaVersion"
        targetCompatibility = "$javaVersion"
    }

    jar { from("LICENSE") { rename { "${it}_${base.archivesName}" } } }
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") { expand(mutableMapOf("version" to project.version)) }
    }
    java {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        withSourcesJar()
    }
}