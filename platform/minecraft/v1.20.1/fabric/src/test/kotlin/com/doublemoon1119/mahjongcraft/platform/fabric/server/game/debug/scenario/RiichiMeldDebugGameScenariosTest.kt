package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.closedKanFaceDownTileIds
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證副露擺放情境能依序做出加槓、暗槓與嶺上自摸。 */
class RiichiMeldDebugGameScenariosTest {
    /** 提供副露擺放情境的登錄表。 */
    private val registry = DebugGameScenarioRegistry()

    /** 檢查情境結果是否符合對局約束。 */
    private val scenarioValidator = DebugGameScenarioValidator(scenarioModuleRegistry)

    /** 情境登記在 registry 中，且在各種赤寶牌張數下都通過權威驗證。 */
    @Test
    fun `meld showcase scenario is registered and valid`() {
        RED_DORA_COUNTS.forEach { redDoraCount ->
            val context = createRiichiScenarioContext(config = RiichiRuleConfig(redDoraCount = redDoraCount))
            val result = registry.get(MELD_SHOWCASE_ID)!!.build(context)

            scenarioValidator.validate(context, result)
            assertEquals(context.invokingPlayerId, result.game.tableState.currentPlayer.id)
        }
    }

    /** 三組碰分別從上家、對家、下家鳴取，鳴取的牌是每組的最後一張。 */
    @Test
    fun `meld showcase pons come from every direction`() {
        val melds = build().currentPlayer.hand.melds

        assertEquals(
            listOf(
                Tile.Honor.White to RelativeDirection.Left,
                Tile.Honor.East to RelativeDirection.Across,
                Tile.Honor.Green to RelativeDirection.Right,
            ),
            melds.map { meld -> meld.tiles.first().tile to meld.sourceDirection },
        )
        melds.forEach { meld ->
            assertEquals(MeldType.PON, meld.type)
            assertEquals(meld.tiles.last(), meld.sourceTile)
        }
    }

    /** 剛摸到的白可以加槓；第一張嶺上牌八筒可以暗槓；第二張嶺上牌九萬可以自摸。 */
    @Test
    fun `meld showcase chains an added kan and a closed kan into a replacement tsumo`() {
        val state = build()
        val invoker = state.currentPlayer
        val (firstReplacement, secondReplacement) = state.reservedWallTiles.take(2)

        assertEquals(listOf(EIGHT_DOT, NINE_CHARACTER), listOf(firstReplacement.tile, secondReplacement.tile))
        assertTrue(state.currentOwnTurnActions().filterIsInstance<GameAction.Kan>().any { it.type == GameAction.KanType.ADDED_KAN })
        assertTrue(
            state.ownTurnActions(offset = 0, drawnTile = firstReplacement)
                .filterIsInstance<GameAction.Kan>()
                .any { it.type == GameAction.KanType.CLOSED_KAN },
        )

        val eightDots = invoker.hand.tiles.filter { it.tile == EIGHT_DOT } + firstReplacement
        val closedKanTiles = eightDots.sortedBy { it.id.toString() }
        val closedKan = Meld(
            type = MeldType.CLOSED_KAN,
            tiles = closedKanTiles,
            sourceDirection = RelativeDirection.Self,
            publiclyRevealedTileIds = closedKanTiles.mapTo(LinkedHashSet()) { it.id },
            faceDownTileIds = closedKanFaceDownTileIds(closedKanTiles),
        )
        val afterKans = state.withHandFor(
            playerId = invoker.id,
            hand = invoker.hand.copy(
                tiles = invoker.hand.tiles.filter { it.tile != EIGHT_DOT },
                melds = invoker.hand.melds + closedKan,
                lastDrawn = secondReplacement,
            ),
        )
        assertTrue(
            GameAction.Tsumo in afterKans.scenarioValidator().getLegalActions(LegalActionContext.ownTurn(afterKans, invoker.id)),
        )
    }

    /** @return 由 registry 建立的副露擺放情境桌況。 */
    private fun build(): TableState = registry.get(MELD_SHOWCASE_ID)!!.build(createRiichiScenarioContext()).game.tableState

    /** 副露擺放情境共用的識別碼與牌面。 */
    private companion object {
        /** 副露擺放情境的識別碼。 */
        const val MELD_SHOWCASE_ID: String = "mahjongcraft:riichi_meld_showcase"

        /** 四人日麻房間設定可選的赤寶牌張數。 */
        val RED_DORA_COUNTS: List<Int> = listOf(0, 3, 4)

        /** 八筒，第一張嶺上牌與暗槓的牌面。 */
        val EIGHT_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 8)
    }
}
