package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPositionRules
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/** 驗證沒有規則知識的規則查詢不回報任何規則事實，以及規則模組如何提供規則查詢。 */
class NeutralPositionRulesTest {
    private val self = FakeMahjongPlayerFactory.create(Wind.SOUTH)
    private val opponent = FakeMahjongPlayerFactory.create(
        initialSeat = Wind.EAST,
        discardPile = FakeDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.Red)),
    )
    private val view = PositionView(
        snapshot = FakeTableStateFactory.create(players = listOf(opponent, self)).toSnapshot(visibleHandPlayerIds = setOf(self.id), setAsideTiles = { emptyList() }),
        evaluatorId = self.id,
    )

    /** 和牌價值無法判斷。 */
    @Test
    fun `win values are unknown`() {
        assertEquals(WinValue.Unknown, NeutralPositionRules.winValue(view, Hand(), Tile.Honor.Red, isTsumo = true))
    }

    /** 宣告沒有任何效果，聽牌後也沒有可以宣告的動作。 */
    @Test
    fun `declarations have no effect`() {
        assertEquals(DeclarationEffect.NONE, NeutralPositionRules.declarationEffect(view, GameAction.Extension(OtherAction)))
        assertEquals(emptySet(), NeutralPositionRules.prospectiveDeclarations(view, Hand()))
    }

    /** 不知道規則時，即使是對手自己打過的牌也不能確定他不能榮和，沒有依牌張加計的打點，也沒有宣告聽牌時打出的牌。 */
    @Test
    fun `no tile is excluded bonus or declared`() {
        assertEquals(RonExclusions.NONE, NeutralPositionRules.ronExclusions(view, opponent.id))
        assertEquals(0, NeutralPositionRules.bonusTileCount(view, Tile.Honor.Red))
        assertNull(NeutralPositionRules.declarationTile(view, opponent.id))
    }

    /** 沒有專屬規則查詢的規則使用規則中立的查詢，日麻使用自己的查詢。 */
    @Test
    fun `rule modules provide their own position rules or the neutral one`() {
        assertSame(NeutralPositionRules, TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, TaiwanRuleConfig()).createPositionRules())
        assertIs<RiichiPositionRules>(RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, RiichiRuleConfig()).createPositionRules())
    }

    /** 測試用的擴充動作。 */
    private object OtherAction : ExtensionGameAction {
        override val id: String = "example:other"
    }
}
