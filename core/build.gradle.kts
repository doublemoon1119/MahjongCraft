plugins {
    alias(libs.plugins.kotlin.jvm)
}

/**
 * 指定此模組的編譯目標版本。
 * 此值讀取自 libs.versions.toml 中的 jvm-core-release。
 * 鎖定在 Java 17 是為了確保 core 模組能被所有計畫支援的 Minecraft 版本（如 1.20.1）相容。
 */
extra["javaRelease"] = libs.versions.jvm.core.release.get().toInt()

dependencies {
    // 僅用於編譯期，不打包進 Jar
    compileOnly(libs.kotlin.coroutines)

    // 單元測試相關
    testImplementation(kotlin("test"))
}