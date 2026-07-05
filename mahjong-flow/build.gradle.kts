plugins {
    alias(libs.plugins.kotlin.jvm)
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    /**
     * 此模組 Java 編譯版本與 domain 模組應保持一致。
     */
    extra["javaRelease"] = rootProject.libs.versions.jvm.domain.release.get().toInt()

    dependencies {
        // 單元測試相關
        testImplementation(kotlin("test"))
        testImplementation(rootProject.libs.kotlinx.coroutines.test)
        testImplementation(project(":testing:testing-mahjong-logic"))
        testImplementation(project(":testing:testing-mahjong-flow"))
    }
}