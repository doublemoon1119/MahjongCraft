plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.fabric.loom)
}

dependencies {
    minecraft(libs.minecraft1201)
    mappings("net.fabricmc:yarn:${libs.versions.yarnMappings1201.get()}:v2")
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabricApi1201)
    modImplementation(libs.fabric.language.kotlin)

    implementation(project(":minecraft_common"))
    implementation(project(":minecraft_v1.20.1_common"))
}
