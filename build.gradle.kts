import com.doublemoon1119.mahjongcraft.buildlogic.registerAutotestAggregate
import com.doublemoon1119.mahjongcraft.buildlogic.registerCoverageAggregate

plugins {
    base
    alias(libs.plugins.mahjongcraft.target.management)
    alias(libs.plugins.mahjongcraft.repository.verification)
    // Declared once here so every module loads the same classes of these plugins. Loom modules that apply
    // different plugin sets would otherwise load Loom in separate class loaders, and Loom fails when it
    // reaches another project's extension, for example while an IDE sync creates genSources tasks.
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.fabric.loom) apply false
    alias(libs.plugins.moddev.legacyforge) apply false
    alias(libs.plugins.moddev) apply false
    alias(libs.plugins.koin.compiler) apply false
}

group = "com.doublemoon1119.mahjongcraft"
version = "0.0.0-dev"

val projectId = "mahjongcraft"
val projectDisplayName = "MahjongCraft"
extra["projectId"] = projectId
extra["projectDisplayName"] = projectDisplayName

// -PmahjongcraftAutotest 時登記總任務 autotest：執行所選目標全部的自動測試並寫出總報告。
registerAutotestAggregate()

// -PmahjongcraftCoverage=true 時合併各模組的測試覆蓋率，koverHtmlReport 與 koverXmlReport 寫出總報告。
registerCoverageAggregate()
