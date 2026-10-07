package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.di.RiichiWinCelebrationCueResolver
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPointResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue.RiichiCatalogueCategory
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue.RiichiCatalogueYaku
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistryImpl
import kotlin.test.Test
import kotlin.test.assertNotNull

/** 驗證日麻役滿的展示理由都對得上內建展示定義。 */
class RiichiWinCelebrationShowcasesTest {
    /** 每個役滿與雙倍役滿役種解析出的展示理由都有登記的內建展示。 */
    @Test
    fun `every yakuman resolves to a registered showcase`() {
        val registry = WinCelebrationShowcaseRegistryImpl().apply { BundledRiichiMinecraftExtension.registerWinCelebrationShowcases(this) }
        val yakuman = RiichiCatalogueYaku.entries.filter {
            it.category == RiichiCatalogueCategory.YAKUMAN || it.category == RiichiCatalogueCategory.DOUBLE_YAKUMAN
        }

        yakuman.forEach { definition ->
            val result = RiichiHandValueResult(
                yakuResults = listOf(YakuResult.yakuman(definition.type)),
                totalHan = -1,
                totalFu = 0,
                pointResult = RiichiPointResult.Ron(32_000),
            )
            val cue = RiichiWinCelebrationCueResolver.resolve(result).single()
            assertNotNull(registry.find(cue), "No showcase for ${definition.type}: $cue")
        }
    }
}
