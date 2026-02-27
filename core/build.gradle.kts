plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(libs.versions.java.compiler.get().toInt())
}

dependencies {
    // 僅用於編譯期，不打包進 Jar
    compileOnly(libs.kotlin.coroutines)

    // 單元測試相關
    testImplementation(kotlin("test"))
}