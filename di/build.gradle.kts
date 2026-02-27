plugins {
    alias(libs.plugins.kotlin.jvm)
}

/**
 * di 模組負責 core 模組的依賴注入配置。
 * 為了確保能被所有適配層模組使用，此模組的編譯版本應與 core 保持一致。
 */
extra["javaRelease"] = libs.versions.jvm.core.release.get().toInt()

dependencies {
    api(project(":core"))

    api(platform(libs.koin.bom))
    api(libs.koin.core)

    // 僅用於編譯期，不打包進 Jar
    compileOnly(libs.kotlin.coroutines)

    // 單元測試相關
    testImplementation(kotlin("test"))
}