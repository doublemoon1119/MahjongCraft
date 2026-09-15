plugins {
    id("mahjongcraft.kotlin-jvm")
}

// 只在建置期間供 ktlint 載入，不發布，比照 testing 模組固定為 dev 版本。
version = "0.0.0-dev"

dependencies {
    // ktlint 在執行 rule set 時自行提供這些 API，打包進 jar 會與 ktlint 自己的版本衝突。
    compileOnly(libs.ktlint.rule.engine.core)
    compileOnly(libs.ktlint.cli.ruleset.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktlint.rule.engine.core)
    testImplementation(libs.ktlint.cli.ruleset.core)
    // 測試直接驅動 rule engine 執行規則。
    testImplementation(libs.ktlint.rule.engine)
    // rule engine 透過 kotlin-logging 要求一個 slf4j 實作；規則測試不需要輸出記錄。
    testRuntimeOnly(libs.slf4j.nop)
}
