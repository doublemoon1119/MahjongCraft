package com.doublemoon1119.mahjongcraft.platform.minecraft.showcase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

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

    /** registry 凍結後不得再加入 definition。 */
    @Test
    fun rejectsRegistrationAfterFreeze() {
        val registry = WinCelebrationShowcaseRegistryImpl().apply { freeze() }

        assertFailsWith<IllegalStateException> { registry.register(definition()) }
    }

    private fun definition(durationTicks: Int = 160): WinCelebrationShowcaseDefinition = WinCelebrationShowcaseDefinition(
        cueKey = "test:cue",
        titleTranslationKey = "showcase.test.cue",
        titleImageResourceId = "test:textures/showcase/cue.png",
        palette = ShowcasePalette(primary = -1, secondary = -1, accent = -1),
        showcaseDurationTicks = durationTicks,
    )
}
