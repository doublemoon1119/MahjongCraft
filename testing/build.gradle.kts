plugins {
    alias(libs.plugins.kotlin.jvm)
}

/**
 * testing 模組提供了跨模組共享的測試輔助工具。
 * 為了讓各模組間的測試環境保持一致，
 * 此模組的 Java 編譯版本應與 domain 模組相同。
 */
extra["javaRelease"] = libs.versions.jvm.domain.release.get().toInt()

dependencies {
    // testing 模組需要存取 domain 中的類別和介面來建立 Fake 物件。
    api(project(":domain"))
}
