package com.doublemoon1119.mahjongcraft.platform.minecraft.showcase

import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
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
        val registry = WinCelebrationShowcaseRegistryImpl().apply { BundledRiichiMinecraftExtension.registerWinCelebrationShowcases(this) }

        assertEquals(160, assertNotNull(registry.find("mahjongcraft:riichi/yakuman/kokushi_musou")).showcaseDurationTicks)
    }

    /** cue key 快照同時包含內建與第三方 definition，且不允許呼叫端修改 registry。 */
    @Test
    fun exposesRegisteredCueKeySnapshot() {
        val registry = WinCelebrationShowcaseRegistryImpl().apply {
            BundledRiichiMinecraftExtension.registerWinCelebrationShowcases(this)
            register(definition())
        }
        val snapshot = registry.cueKeys

        assertEquals(true, "mahjongcraft:riichi/yakuman/kokushi_musou" in snapshot)
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
        val registry = WinCelebrationShowcaseRegistryImpl().apply { BundledRiichiMinecraftExtension.registerWinCelebrationShowcases(this) }

        assertEquals(
            "mahjongcraft:riichi/yakuman/daisuushii",
            registry.select(listOf("mahjongcraft:riichi/yakuman/daisangen", "mahjongcraft:riichi/yakuman/daisuushii"))?.cueKey,
        )
        assertEquals(
            "mahjongcraft:riichi/yakuman/kokushi_musou",
            registry.select(listOf("unknown:cue", "mahjongcraft:riichi/yakuman/kokushi_musou"))?.cueKey,
        )
        assertNull(registry.select(listOf("unknown:cue")))
        assertNull(registry.select(emptyList()))
    }

    /** 內建日麻展示的 ID、標題 key 與貼圖都放在日麻專屬的命名下。 */
    @Test
    fun scopesBuiltInsToRiichi() {
        val registry = WinCelebrationShowcaseRegistryImpl().apply { BundledRiichiMinecraftExtension.registerWinCelebrationShowcases(this) }
        val definition = assertNotNull(registry.find("mahjongcraft:riichi/yakuman/daisuushii"))

        assertEquals("mahjongcraft.showcase.riichi.daisuushii", definition.titleTranslationKey)
        assertEquals("mahjongcraft:textures/showcase/riichi/daisuushii.png", definition.titleImageResourceId)
        assertEquals(true, registry.cueKeys.all { it.startsWith("mahjongcraft:riichi/yakuman/") })
    }

    /** 沒有指定時，展示會讓仍在本局中的玩家等它播完；內建展示全部維持等待。 */
    @Test
    fun pausesContinuingRoundByDefault() {
        val registry = WinCelebrationShowcaseRegistryImpl().apply { BundledRiichiMinecraftExtension.registerWinCelebrationShowcases(this) }

        assertEquals(true, definition().pausesContinuingRound)
        assertEquals(true, registry.pausesContinuingRound(listOf(listOf("mahjongcraft:riichi/yakuman/daisangen"))))
    }

    /** 只有不要求等待的展示時不暫停；任何一位贏家的展示要求等待時就暫停。 */
    @Test
    fun pausesContinuingRoundWhenAnySelectedDefinitionRequiresIt() {
        val registry = WinCelebrationShowcaseRegistryImpl().apply {
            register(definition(cueKey = "test:quiet", pausesContinuingRound = false))
            register(definition(cueKey = "test:loud"))
        }

        assertEquals(false, registry.pausesContinuingRound(listOf(listOf("test:quiet"), emptyList())))
        assertEquals(true, registry.pausesContinuingRound(listOf(listOf("test:quiet"), listOf("test:loud"))))
        assertEquals(false, registry.pausesContinuingRound(listOf(emptyList(), emptyList())))
    }

    /** 展示理由選不出已登記的定義時不播放展示，也不暫停。 */
    @Test
    fun doesNotPauseContinuingRoundForUnregisteredCues() {
        val registry = WinCelebrationShowcaseRegistryImpl().apply { BundledRiichiMinecraftExtension.registerWinCelebrationShowcases(this) }

        assertEquals(false, registry.pausesContinuingRound(listOf(listOf("unknown:cue"))))
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
        pausesContinuingRound: Boolean = true,
    ): WinCelebrationShowcaseDefinition = WinCelebrationShowcaseDefinition(
        cueKey = cueKey,
        titleTranslationKey = "showcase.test.cue",
        titleImageResourceId = "test:textures/showcase/cue.png",
        palette = ShowcasePalette(primary = -1, secondary = -1, accent = -1),
        showcaseDurationTicks = durationTicks,
        pausesContinuingRound = pausesContinuingRound,
    )
}
