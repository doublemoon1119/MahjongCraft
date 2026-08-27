package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInWinCelebrationCueIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationCue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationWinner
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [DebugWinShowcaseOverride] 的一次性、以桌為範圍與「只動呈現」保證的測試。
 *
 * 最後一項特別重要：這個覆寫的整個正當性建立在「它動不到權威資料」上，因此除了驗證行為，也直接
 * 驗證被覆寫的 [WinCelebrationRequest] 除了 cue 以外一個欄位都沒變。
 */
class DebugWinShowcaseOverrideTest {
    private val tableId = Uuid.random()
    private val override = DebugWinShowcaseOverride(DevelopmentEnvironment)

    /** 武裝後下一次胡牌的所有贏家都會拿到指定 cue。 */
    @Test
    fun `an armed override replaces every winner's cue`() {
        override.arm(tableId, CUE_KEY)

        val applied = override.applyTo(tableId, request(cues = listOf(null, null)))

        assertEquals(listOf(WinCelebrationCue(CUE_KEY), WinCelebrationCue(CUE_KEY)), applied.winners.map { it.cue })
    }

    /** 一次性：用掉之後第二次胡牌就恢復原本的 cue。 */
    @Test
    fun `the override is consumed after a single use`() {
        override.arm(tableId, CUE_KEY)
        override.applyTo(tableId, request(cues = listOf(null)))

        val second = request(cues = listOf(null))

        assertSame(second, override.applyTo(tableId, second))
        assertEquals(emptySet(), override.armedTableIds())
    }

    /** 沒武裝時原樣回傳，連物件都不重建。 */
    @Test
    fun `an unarmed table passes the request through untouched`() {
        val original = request(cues = listOf(null))

        assertSame(original, override.applyTo(tableId, original))
    }

    /** 以桌為範圍：對另一桌武裝不得影響這一桌。 */
    @Test
    fun `arming one table does not affect another`() {
        override.arm(Uuid.random(), CUE_KEY)
        val original = request(cues = listOf(null))

        assertSame(original, override.applyTo(tableId, original))
    }

    /** 清除未用掉的武裝；對局結束／桌子被破壞時就是走這條路徑。 */
    @Test
    fun `clearing removes an unused arming`() {
        override.arm(tableId, CUE_KEY)
        override.clear(tableId)

        assertEquals(emptySet(), override.armedTableIds())
        assertNull(override.consume(tableId))
    }

    /** 正式產物裡完全 inert：武裝被拒絕，套用永遠原樣回傳。 */
    @Test
    fun `a production build refuses to arm`() {
        val production = DebugWinShowcaseOverride(ProductionEnvironment)
        val original = request(cues = listOf(null))

        assertFalse(production.arm(tableId, CUE_KEY))
        assertEquals(emptySet(), production.armedTableIds())
        assertSame(original, production.applyTo(tableId, original))
    }

    /**
     * 覆寫只能動 cue：[WinCelebrationRequest] 的其餘欄位（胡牌張、自摸與否、座位）必須完全不變。
     *
     * 權威役種／番數／分數／結算結果根本不在這個型別上（它們走獨立的
     * `WinSettlementPresentationRequest`，且更早就已經寫進 `TableState`），因此結構上就碰不到。
     */
    @Test
    fun `the override touches nothing but the cue`() {
        override.arm(tableId, CUE_KEY)
        val original = request(cues = listOf(null, WinCelebrationCue("some:other_cue")))

        val applied = override.applyTo(tableId, original)

        assertEquals(original.winningTileId, applied.winningTileId)
        assertEquals(original.isTsumo, applied.isTsumo)
        assertEquals(original.winners.map { it.seatIndex }, applied.winners.map { it.seatIndex })
        assertEquals(original.copy(winners = applied.winners), applied)
    }

    /**
     * 覆寫必須讓這次胡牌變成「有 showcase 可看」。
     *
     * 這正是 [WinPresentationRequest.hasWatchableShowcase] 讀的東西，而阻塞判定又完全靠它——覆寫如果
     * 只換了 cue 卻沒讓這個判斷翻成 true，showcase 會播但不擋桌，剛好把這個工具想驗證的路徑跳過去。
     */
    @Test
    fun `an overridden celebration becomes a watchable showcase`() {
        val celebration = request(cues = listOf(null))
        assertFalse(celebration.winners.any { it.cue != null }, "A non-yakuman win has no showcase to watch.")
        override.arm(tableId, CUE_KEY)

        assertTrue(override.applyTo(tableId, celebration).winners.any { it.cue != null })
    }

    private fun request(cues: List<WinCelebrationCue?>): WinCelebrationRequest = WinCelebrationRequest(
        winningTileId = Uuid.random(),
        isTsumo = true,
        winners = cues.mapIndexed { index, cue -> WinCelebrationWinner(seatIndex = index, cue = cue) },
    )

    private companion object {
        val CUE_KEY: String = BuiltInWinCelebrationCueIds.riichiYakuman("kokushi_musou")

        object DevelopmentEnvironment : MinecraftEnvironment {
            override val isDevelopment: Boolean = true
        }

        object ProductionEnvironment : MinecraftEnvironment {
            override val isDevelopment: Boolean = false
        }
    }
}
