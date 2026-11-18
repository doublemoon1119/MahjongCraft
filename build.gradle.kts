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
    // CottonMC's maven (server.bbkr.space) is gone; LibGui jars come from its GitHub releases.
    ivy("https://github.com/CottonMC/LibGui/releases/download/") {
        name = "LibGui GitHub releases"
        patternLayout { artifact("[revision]/[module]-[revision]+[classifier].[ext]") }
        metadataSources { artifact() }
        content { includeModule("io.github.cottonmc", "LibGui") }
    }
    mavenCentral() // libninepatch (LibGui dependency, dev runtime)
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
    val libguiMinecraftVersion: String by project
    include("io.github.cottonmc:LibGui:$libguiVersion:$libguiMinecraftVersion")?.let { modImplementation(it) }
    val clothConfigVersion: String by project
    // Cloth Config API (https://www.curseforge.com/minecraft/mc-mods/cloth-config)
    modApi("me.shedaniel.cloth:cloth-config-fabric:$clothConfigVersion") {
        exclude(group = "net.fabricmc.fabric-api")
    }
    // Mod Menu (https://www.curseforge.com/minecraft/mc-mods/modmenu)
    val modMenuVersion: String by project
    modImplementation("com.terraformersmc:modmenu:$modMenuVersion")

    // LibGui ships Jankson-Fabric and libninepatch nested inside its jar, so players get them.
    // Loom drops nested jars in the dev environment, and Jankson-Fabric's only maven host is gone,
    // so for runClient take it out of the LibGui release jar.
    modLocalRuntime(files(libguiNestedJar("Jankson-Fabric")))
    localRuntime("io.github.juuxel:libninepatch:1.2.0")

    testImplementation(kotlin("test"))
}

/** Extracts the nested jar whose name starts with [prefix] from the LibGui release jar. */
fun libguiNestedJar(prefix: String): File {
    val libguiVersion: String by project
    val libguiMinecraftVersion: String by project
    val libgui = configurations.detachedConfiguration(
        dependencies.create("io.github.cottonmc:LibGui:$libguiVersion:$libguiMinecraftVersion")
    ).apply { isTransitive = false }.singleFile
    val outDir = layout.buildDirectory.dir("libgui-nested").get().asFile
    copy {
        from(zipTree(libgui)) { include("META-INF/jars/$prefix-*.jar") }
        eachFile { path = name }
        includeEmptyDirs = false
        into(outDir)
    }
    return outDir.listFiles { f -> f.name.startsWith(prefix) }!!.single()
}

tasks {
    val javaVersion = JavaVersion.VERSION_21
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(javaVersion.toString().toInt())
    }

    test { useJUnitPlatform() }

    jar { from("LICENSE") { rename { "${it}_${base.archivesName}" } } }
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") { expand(mutableMapOf("version" to project.version)) }
    }
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    withSourcesJar()
}

kotlin { jvmToolchain(21) }