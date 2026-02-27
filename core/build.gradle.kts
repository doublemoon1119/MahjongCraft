plugins {
    alias(libs.plugins.kotlin.jvm)
}

/**
 * 指定此模組的編譯目標版本。
 *
 * 為了確保最大相容性，core 模組應鎖定在專案計畫支援的最低 Java 版本（目前為 Java 17）。
 * 這樣當 core 被 ShadowJar 打包進適配層（如 v1_20_1）時，能確保該版本遊戲環境可執行。
 *
 * 若未來決定放棄支援 Java 17 的 Minecraft 版本（如 1.20.4 以下），可將此值調整為 21。
 */
extra["javaRelease"] = 17

dependencies {
    // 僅用於編譯期，不打包進 Jar
    compileOnly(libs.kotlin.coroutines)

    // 單元測試相關
    testImplementation(kotlin("test"))
}