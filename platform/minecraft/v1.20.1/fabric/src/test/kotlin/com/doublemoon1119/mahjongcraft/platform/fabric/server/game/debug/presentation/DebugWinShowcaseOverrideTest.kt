package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInWinCelebrationCueIds
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
 * 驗證被覆寫的 [WinCelebrationRequest] 除了展示理由以外一個欄位都沒變。
 */
class DebugWinShowcaseOverrideTest {
    private val tableId = Uuid.random()
    private val override = DebugWinShowcaseOverride(DevelopmentEnvironment)

    /** 武裝後下一次胡牌的所有贏家都會拿到指定 cue。 */
    @Test
    fun `an armed override replaces every winner's cue`() {
        override.arm(tableId, CUE_KEY)

        val applied = override.applyTo(tableId, request(cues = listOf(emptyList(), emptyList())))

        assertEquals(listOf(listOf(CUE_KEY), listOf(CUE_KEY)), applied.winners.map { it.cueIds })
    }

    /** 一次性：用掉之後第二次胡牌就恢復原本的 cue。 */
    @Test
    fun `the override is consumed after a single use`() {
        override.arm(tableId, CUE_KEY)
        override.applyTo(tableId, request(cues = listOf(emptyList())))

        val second = request(cues = listOf(emptyList()))

        assertSame(second, override.applyTo(tableId, second))
        assertEquals(emptySet(), override.armedTableIds())
    }

    /** 沒武裝時原樣回傳，連物件都不重建。 */
    @Test
    fun `an unarmed table passes the request through untouched`() {
        val original = request(cues = listOf(emptyList()))

        assertSame(original, override.applyTo(tableId, original))
    }

    /** 以桌為範圍：對另一桌武裝不得影響這一桌。 */
    @Test
    fun `arming one table does not affect another`() {
        override.arm(Uuid.random(), CUE_KEY)
        val original = request(cues = listOf(emptyList()))

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
        val original = request(cues = listOf(emptyList()))

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
        val original = request(cues = listOf(emptyList(), listOf("some:other_cue")))

        val applied = override.applyTo(tableId, original)

        assertEquals(original.winningTileId, applied.winningTileId)
        assertEquals(original.isTsumo, applied.isTsumo)
        assertEquals(original.winners.map { it.seatIndex }, applied.winners.map { it.seatIndex })
        assertEquals(original.copy(winners = applied.winners), applied)
    }

    /**
     * 覆寫必須讓這次胡牌帶有展示理由。
     *
     * 平台的阻塞判定正是看贏家有沒有展示理由——覆寫如果只換了 cue 卻沒讓這個判斷翻成 true，
     * showcase 會播但不擋桌，剛好把這個工具想驗證的路徑跳過去。
     */
    @Test
    fun `an overridden celebration carries a showcase reason`() {
        val celebration = request(cues = listOf(emptyList()))
        assertFalse(celebration.winners.any { it.cueIds.isNotEmpty() }, "A non-yakuman win has no showcase to watch.")
        override.arm(tableId, CUE_KEY)

        assertTrue(override.applyTo(tableId, celebration).winners.any { it.cueIds.isNotEmpty() })
    }

    private fun request(cues: List<List<String>>): WinCelebrationRequest = WinCelebrationRequest(
        winningTileId = Uuid.random(),
        isTsumo = true,
        winners = cues.mapIndexed { index, ids -> WinCelebrationWinner(seatIndex = index, cueIds = ids) },
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
