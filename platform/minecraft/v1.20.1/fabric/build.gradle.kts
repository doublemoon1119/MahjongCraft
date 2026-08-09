plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.fabric.loom)
}

// assets/mahjongcraft 底下的貼圖/模型/語言檔實體上放在 :minecraft_common（跨版本、跨 loader 共用，
// 避免每個版本/loader 模組各自留一份重複的素材），這裡只是把那個目錄多接一條 srcDir 進本模組的
// resources，讓既有的 processResources/打包流程照樣把它們收進最終的 mod jar，不需要額外的複製 task。
sourceSets {
    main {
        resources {
            srcDir(project(":minecraft_common").projectDir.resolve("src/jvmMain/resources"))
        }
    }
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
