plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // 單元測試相關
    testImplementation(kotlin("test"))
    testImplementation(project(":testing:testing-mahjong-logic"))
}