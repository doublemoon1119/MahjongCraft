package com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證每個日麻役種（含古役）都有唯一且可反查的胡牌詳情 ID。 */
class RiichiWinSettlementIdsTest {
    /** 每個役種都有合法、不重複的 ID，並能由 ID 反查回原役種。 */
    @Test
    fun `every yaku has a unique namespaced id`() {
        val ids = YakuType.entries.map(RiichiWinSettlementIds::yaku)

        assertEquals(ids.size, ids.distinct().size)
        ids.forEach { assertTrue(NamespacedId.isValid(it), it) }
        YakuType.entries.forEach { assertEquals(it, RiichiWinSettlementIds.yakuType(RiichiWinSettlementIds.yaku(it))) }
    }
}
