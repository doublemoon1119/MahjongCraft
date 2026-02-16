plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(libs.versions.java.compiler.get().toInt())
}

dependencies {
    api(project(":core"))

    api(platform(libs.koin.bom))
    api(libs.koin.core)

    // 僅用於編譯期，不打包進 Jar
    compileOnly(libs.kotlin.coroutines)

    // 單元測試相關
    testImplementation(kotlin("test"))
}