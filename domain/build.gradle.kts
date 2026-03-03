plugins {
    alias(libs.plugins.kotlin.jvm)
}

/**
 * 指定此模組的編譯目標版本。
 * 此值讀取自 libs.versions.toml 中的 jvm-domain-release。
 * 鎖定在 Java 17 是為了確保 domain 模組能被所有計畫支援的 Minecraft 版本（如 1.20.1）相容。
 */
extra["javaRelease"] = libs.versions.jvm.domain.release.get().toInt()

dependencies {
    // 單元測試相關
    testImplementation(kotlin("test"))
}