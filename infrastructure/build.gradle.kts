plugins {
    alias(libs.plugins.kotlin.jvm)
}

/**
 * application 模組負責定義業務邏輯流程 (Use Cases) 與跨平台介面抽象。
 * 此模組同時整合了依賴注入 (Dependency Injection) 配置，
 * 用於連結 domain 層的業務規則與 platform 層的具體實作。
 * 為確保與核心領域邏輯的相容性並預留跨遊戲平台 (如 Minecraft) 的適配空間，
 * 此模組 Java 編譯版本與 domain 模組應保持一致。
 */
extra["javaRelease"] = libs.versions.jvm.domain.release.get().toInt()

dependencies {
    api(project(":application"))
    api(platform(libs.koin.bom))
    api(libs.koin.core)

    // 單元測試相關
    testImplementation(kotlin("test"))
    testImplementation(project(":testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}