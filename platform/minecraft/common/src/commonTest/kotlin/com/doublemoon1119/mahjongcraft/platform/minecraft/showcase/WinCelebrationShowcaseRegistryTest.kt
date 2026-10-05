package com.doublemoon1119.mahjongcraft.platform.minecraft.showcase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 宣告式役滿展示 registry 與 definition 邊界測試。 */
class WinCelebrationShowcaseRegistryTest {
    /** 內建 definition 使用八秒正式展示。 */
    @Test
    fun registersBuiltInsWithEightSecondShowcase() {
        val registry = WinCelebrationShowcaseRegistryImpl().apply { registerBuiltInWinCelebrationShowcases() }

        assertEquals(160, assertNotNull(registry.find("mahjongcraft:kokushi_musou")).showcaseDurationTicks)
    }

    /** cue key 快照同時包含內建與第三方 definition，且不允許呼叫端修改 registry。 */
    @Test
    fun exposesRegisteredCueKeySnapshot() {
        val registry = WinCelebrationShowcaseRegistryImpl().apply {
            registerBuiltInWinCelebrationShowcases()
            register(definition())
        }
        val snapshot = registry.cueKeys

        assertEquals(true, "mahjongcraft:kokushi_musou" in snapshot)
        assertEquals(true, "test:cue" in snapshot)
        registry.register(
            WinCelebrationShowcaseDefinition(
                cueKey = "test:later",
                titleTranslationKey = "showcase.test.later",
                titleImageResourceId = "test:textures/showcase/later.png",
                palette = ShowcasePalette(primary = -1, secondary = -1, accent = -1),
            ),
        )
        assertEquals(false, "test:later" in snapshot)
        assertEquals(true, "test:later" in registry.cueKeys)
    }

    /** 第三方展示時間限制為四至十二秒。 */
    @Test
    fun validatesExtensionDurationRange() {
        assertFailsWith<IllegalArgumentException> { definition(durationTicks = 79) }
        assertFailsWith<IllegalArgumentException> { definition(durationTicks = 241) }
    }

    /** 多個展示理由時挑優先序最高的已登記定義；內建順序與役滿倍數一致。 */
    @Test
    fun selectsHighestPriorityRegisteredDefinition() {
        val registry = WinCelebrationShowcaseRegistryImpl().apply { registerBuiltInWinCelebrationShowcases() }

        assertEquals(
            "mahjongcraft:daisuushii",
            registry.select(listOf("mahjongcraft:daisangen", "mahjongcraft:daisuushii"))?.cueKey,
        )
        assertEquals(
            "mahjongcraft:kokushi_musou",
            registry.select(listOf("unknown:cue", "mahjongcraft:kokushi_musou"))?.cueKey,
        )
        assertNull(registry.select(listOf("unknown:cue")))
        assertNull(registry.select(emptyList()))
    }

    /** 優先序相同時取規則給的順序中較前者。 */
    @Test
    fun breaksPriorityTiesByRuleOrder() {
        val registry = WinCelebrationShowcaseRegistryImpl().apply {
            register(definition(cueKey = "test:first"))
            register(definition(cueKey = "test:second"))
        }

        assertEquals("test:second", registry.select(listOf("test:second", "test:first"))?.cueKey)
    }

    /** registry 凍結後不得再加入 definition。 */
    @Test
    fun rejectsRegistrationAfterFreeze() {
        val registry = WinCelebrationShowcaseRegistryImpl().apply { freeze() }

        assertFailsWith<IllegalStateException> { registry.register(definition()) }
    }

    private fun definition(
        durationTicks: Int = 160,
        cueKey: String = "test:cue",
    ): WinCelebrationShowcaseDefinition = WinCelebrationShowcaseDefinition(
        cueKey = cueKey,
        titleTranslationKey = "showcase.test.cue",
        titleImageResourceId = "test:textures/showcase/cue.png",
        palette = ShowcasePalette(primary = -1, secondary = -1, accent = -1),
        showcaseDurationTicks = durationTicks,
    )
}
