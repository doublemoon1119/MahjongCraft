package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import kotlin.test.Test
import kotlin.test.assertEquals

/** Showcase debug 指令 cue 補全的純字串行為測試。 */
class FabricDebugAnimationCommandTest {
    /** 單一 cue 會套用到所有多家和贏家。 */
    @Test
    fun `expands one cue to every winner`() {
        assertEquals(
            listOf("kokushi_musou", "kokushi_musou", "kokushi_musou"),
            expandShowcaseCues(listOf("kokushi_musou"), winnerCount = 3, defaultCue = "generic"),
        )
    }

    /** 多個 cue 依贏家順序套用。 */
    @Test
    fun `keeps positional cues for multiple winners`() {
        assertEquals(
            listOf("kokushi_musou", "daisangen", "suuankou"),
            expandShowcaseCues(
                supplied = listOf("kokushi_musou", "daisangen", "suuankou"),
                winnerCount = 3,
                defaultCue = "generic",
            ),
        )
    }

    /** 多個 cue 數量不足時，尾端贏家使用預設 cue。 */
    @Test
    fun `fills missing positional cues with default`() {
        assertEquals(
            listOf("kokushi_musou", "daisangen", "generic"),
            expandShowcaseCues(
                supplied = listOf("kokushi_musou", "daisangen"),
                winnerCount = 3,
                defaultCue = "generic",
            ),
        )
    }

    /** 所有 cue 一律使用完整 namespace，generic fallback 也不例外。 */
    @Test
    fun `lists built-in extension and generic cues`() {
        val suggestions = buildShowcaseCueSuggestions(
            remaining = "",
            cueKeys = setOf("mahjongcraft:kokushi_musou", "example:custom"),
            allowMultiple = false,
        )

        assertEquals(listOf("example:custom", "mahjongcraft:generic", "mahjongcraft:kokushi_musou"), suggestions)
    }

    /** 輸入 namespace 時改用完整 cue key 補全。 */
    @Test
    fun `uses full cue keys for namespaced input`() {
        val suggestions = buildShowcaseCueSuggestions(
            remaining = "mahjongcraft:ko",
            cueKeys = setOf("mahjongcraft:kokushi_musou", "example:custom"),
            allowMultiple = false,
        )

        assertEquals(listOf("mahjongcraft:kokushi_musou"), suggestions)
    }

    /** 逗號清單保留已完成部分並只補全最後一段。 */
    @Test
    fun `completes only the last cue in a comma separated list`() {
        val suggestions = buildShowcaseCueSuggestions(
            remaining = "mahjongcraft:kokushi_musou,mahjongcraft:chu",
            cueKeys = setOf("mahjongcraft:kokushi_musou", "mahjongcraft:churen_poto", "mahjongcraft:chiihou"),
            allowMultiple = true,
        )

        assertEquals(listOf("mahjongcraft:kokushi_musou,mahjongcraft:churen_poto"), suggestions)
    }

    /** 單一 cue 欄位不把逗號視為可補全的分隔符。 */
    @Test
    fun `does not split comma input for a single cue`() {
        val suggestions = buildShowcaseCueSuggestions(
            remaining = "kokushi_musou,chu",
            cueKeys = setOf("mahjongcraft:churen_poto"),
            allowMultiple = false,
        )

        assertEquals(emptyList(), suggestions)
    }
}
