package com.doublemoon1119.mahjongcraft.ai.taiwan

import com.doublemoon1119.mahjongcraft.ai.expectation.ReadingDepth
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerComponents
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanReadyKind
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeMeldFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.taiwan.toTaiwanSnapshotForTests
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證台灣麻將對手模型只依公開資訊估計對手威脅與捨牌危險度。 */
class TaiwanOpponentModelTest {
    /** 本局規則設定。 */
    private val config = TaiwanRuleConfig()

    /** 受測的對手模型。 */
    private val model = TaiwanOpponentModel(scoreConfig = config.scoreConfig, readingDepth = ReadingDepth.BASIC)

    /** 宣告聽牌的對手視為一定聽牌，沒宣告的門清對手不是。 */
    @Test
    fun `a declared opponent is certainly ready`() {
        val declared = table(southComponents = ComponentSet.EMPTY.with(TaiwanPlayerComponents.READY_DECLARATION, TaiwanReadyKind.GENERAL))
        val undeclared = table()

        assertEquals(1.0, model.threat(view(declared), declared.players[SOUTH].id).readyProbability)
        assertTrue(model.threat(view(undeclared), undeclared.players[SOUTH].id).readyProbability < 1.0)
    }

    /** 宣告聽牌與副露中的三元牌都讓放槍給他的損失變大。 */
    @Test
    fun `declaration and a dragon triplet raise the expected loss`() {
        val plain = table()
        val declared = table(southComponents = ComponentSet.EMPTY.with(TaiwanPlayerComponents.READY_DECLARATION, TaiwanReadyKind.GENERAL))
        val withDragon = table(southMelds = listOf(pon(Tile.Honor.Red)))
        val withSequence = table(southMelds = listOf(chi()))

        val plainValue = model.threat(view(plain), plain.players[SOUTH].id).expectedWinValue
        val declaredValue = model.threat(view(declared), declared.players[SOUTH].id).expectedWinValue
        val dragonValue = model.threat(view(withDragon), withDragon.players[SOUTH].id).expectedWinValue
        val sequenceValue = model.threat(view(withSequence), withSequence.players[SOUTH].id).expectedWinValue

        assertTrue(declaredValue > plainValue, "declared $declaredValue must exceed closed $plainValue")
        assertEquals(config.scoreConfig.pointPerTai, dragonValue - sequenceValue)
    }

    /** 台灣麻將沒有振聽，對手自己打過的牌仍有危險。 */
    @Test
    fun `a tile in the opponent discards is not safe`() {
        val table = table()

        assertTrue(model.discardDanger(view(table), table.players[SOUTH].id, OPPONENT_DISCARD) > 0.0)
    }

    /**
     * 建立莊家為東家、評估者為西家的四人桌況；南家打過一張五萬。
     *
     * @param southComponents 南家的玩家組件。
     * @param southMelds 南家的副露。
     * @return 桌況。
     */
    private fun table(
        southComponents: ComponentSet = ComponentSet.EMPTY,
        southMelds: List<Meld> = emptyList(),
    ): TableState {
        val players = Wind.entries.mapIndexed { index, wind ->
            val isSouth = index == SOUTH
            FakeMahjongPlayerFactory.create(
                initialSeat = wind,
                hand = Hand(melds = if (isSouth) southMelds else emptyList()),
                discardPile = TaiwanDiscardPile().let { pile -> if (isSouth) pile.discardTile(FakeIdentifiedTileFactory.create(OPPONENT_DISCARD)) else pile },
                components = if (isSouth) southComponents else ComponentSet.EMPTY,
            )
        }
        return FakeTableStateFactory.create(players = players, dealerPlayerId = players.first().id, config = config)
    }

    /**
     * 以西家為評估者的視角。
     *
     * @param table 權威桌況。
     * @return 查詢視角。
     */
    private fun view(table: TableState): PositionView {
        val evaluator: MahjongPlayer = table.players[EVALUATOR]
        return PositionView(snapshot = table.toTaiwanSnapshotForTests(evaluator.id), evaluatorId = evaluator.id)
    }

    /**
     * 建立碰副露。
     *
     * @param tile 碰牌的牌面。
     * @return 由三張相同牌面組成的碰副露。
     */
    private fun pon(tile: Tile): Meld = FakeMeldFactory.create(
        type = MeldType.PON,
        tiles = List(3) { FakeIdentifiedTileFactory.create(tile) },
        sourceDirection = RelativeDirection.Left,
    )

    /**
     * 建立一二三萬的吃副露。
     *
     * @return 吃副露。
     */
    private fun chi(): Meld = FakeMeldFactory.create(
        type = MeldType.CHI,
        tiles = (1..3).map { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, it)) },
        sourceDirection = RelativeDirection.Left,
    )

    /** 測試常數。 */
    private companion object {
        /** 受估計的南家座位。 */
        const val SOUTH = 1

        /** 評估者的西家座位。 */
        const val EVALUATOR = 2

        /** 南家打過的牌。 */
        val OPPONENT_DISCARD: Tile = Tile.Numeric(Tile.Suit.Character, 5)
    }
}
