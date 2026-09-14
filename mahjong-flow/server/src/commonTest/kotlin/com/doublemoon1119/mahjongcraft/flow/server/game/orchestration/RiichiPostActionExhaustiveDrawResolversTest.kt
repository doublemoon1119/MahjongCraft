package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * 驗證 [RiichiSuufonRendaResolver]／[RiichiSuuchaRiichiResolver]／[RiichiSuukanNagareResolver]
 * 只回應各自對應的 [GameAction]——即使桌況本身已經符合成立條件，傳入其他動作也不能誤判成立。
 * 不同途中流局共用同一個 [TableState] 形狀，resolver 必須以 context 內的實際完成動作區分。
 */
class RiichiPostActionExhaustiveDrawResolversTest {
    private val riichiModule = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig())
    private val taiwanModule = TaiwanRuleModule("mahjongcraft:taiwan", TaiwanRuleConfig())

    private fun tableWithFirstDiscards(discards: List<Tile>): TableState {
        val winds = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH)
        val players = winds.zip(discards).map { (wind, tile) ->
            FakeMahjongPlayerFactory.create(
                initialSeat = wind,
                discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(tile)),
            )
        }
        return FakeTableStateFactory.create(players = players, config = riichiModule.config)
    }

    private fun allRiichiTable(): TableState {
        val players = List(4) {
            FakeMahjongPlayerFactory.create(
                playerRuleState = RiichiPlayerState(riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)),
            )
        }
        return FakeTableStateFactory.create(players = players, config = riichiModule.config)
    }

    private fun kanMeld(): Meld {
        val tile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        return Meld(MeldType.CLOSED_KAN, listOf(tile, tile, tile, tile), sourceDirection = RelativeDirection.Self)
    }

    private fun allKansTable(): TableState {
        val players = List(4) { FakeMahjongPlayerFactory.create(hand = Hand(melds = listOf(kanMeld()))) }
        return FakeTableStateFactory.create(players = players, config = riichiModule.config)
    }

    /** 建立指定動作的通用完成 context。 */
    private fun context(table: TableState, action: GameAction): CompletedGameActionContext = CompletedGameActionContext(
        table.players.first().id,
        action,
        table,
    )

    /** 將第一位玩家的動作歷史替換為指定動作，並建立最後一個動作的完成 context。 */
    private fun contextWithActorHistory(table: TableState, actions: List<GameAction>): CompletedGameActionContext {
        val actor = table.players.first().copy(actionHistory = actions)
        val updatedTable = table.copy(players = listOf(actor) + table.players.drop(1))
        return CompletedGameActionContext(actor.id, actions.last(), updatedTable)
    }

    /** 建立測試用槓動作。 */
    private fun kanAction(): GameAction.Kan = GameAction.Kan(
        type = GameAction.KanType.CLOSED_KAN,
        tileId = Uuid.random(),
        withTiles = List(3) { Uuid.random() },
    )

    /** 驗證四風連打只處理捨牌動作，其餘動作一律回傳 null。 */
    @Test
    fun `suufon renda resolver only fires on discard completed trigger`() {
        val table = tableWithFirstDiscards(List(4) { Tile.Honor.East })
        val resolver = RiichiSuufonRendaResolver()

        assertEquals(
            RiichiExhaustiveDrawReason.SuufonRenda,
            resolver.resolve(context(table, GameAction.Discard(Uuid.random())), riichiModule),
        )
        assertNull(resolver.resolve(context(table, RIICHI_GAME_ACTION), riichiModule))
        assertNull(resolver.resolve(context(table, kanAction()), riichiModule))
    }

    /** 驗證四家立直只在立直宣告緊接的捨牌完成後成立。 */
    @Test
    fun `suucha riichi resolver only fires on riichi discard completed trigger`() {
        val table = allRiichiTable()
        val resolver = RiichiSuuchaRiichiResolver()
        val discard = GameAction.Discard(Uuid.random())

        assertEquals(
            RiichiExhaustiveDrawReason.SuuchaRiichi,
            resolver.resolve(contextWithActorHistory(table, listOf(RIICHI_GAME_ACTION, discard)), riichiModule),
        )
        assertNull(resolver.resolve(context(table, discard), riichiModule))
        assertNull(resolver.resolve(context(table, RIICHI_GAME_ACTION), riichiModule))
        assertNull(resolver.resolve(context(table, kanAction()), riichiModule))
    }

    /** 驗證四槓散了只在槓、補摸後的第一張捨牌完成時成立。 */
    @Test
    fun `suukan nagare resolver only fires on post-kan discard completed trigger`() {
        val table = allKansTable()
        val resolver = RiichiSuukanNagareResolver()
        val kan = kanAction()
        val discard = GameAction.Discard(Uuid.random())

        assertEquals(
            RiichiExhaustiveDrawReason.SuukanNagare,
            resolver.resolve(contextWithActorHistory(table, listOf(kan, GameAction.Draw, discard)), riichiModule),
        )
        assertNull(resolver.resolve(context(table, discard), riichiModule))
        assertNull(resolver.resolve(contextWithActorHistory(table, listOf(kan, GameAction.Draw, discard, discard)), riichiModule))
        assertNull(resolver.resolve(context(table, kan), riichiModule))
        assertNull(resolver.resolve(context(table, RIICHI_GAME_ACTION), riichiModule))
    }

    /** 驗證三個 resolver 面對非日麻規則模組時一律回傳 null，即使觸發時機正確。 */
    @Test
    fun `resolvers return null for a non-riichi rule module`() {
        val discardTable = tableWithFirstDiscards(List(4) { Tile.Honor.East })
        val riichiTable = allRiichiTable()
        val kanTable = allKansTable()

        assertNull(RiichiSuufonRendaResolver().resolve(context(discardTable, GameAction.Discard(Uuid.random())), taiwanModule))
        val discard = GameAction.Discard(Uuid.random())
        assertNull(
            RiichiSuuchaRiichiResolver().resolve(
                contextWithActorHistory(riichiTable, listOf(RIICHI_GAME_ACTION, discard)),
                taiwanModule,
            ),
        )
        assertNull(
            RiichiSuukanNagareResolver().resolve(
                contextWithActorHistory(kanTable, listOf(kanAction(), GameAction.Draw, discard)),
                taiwanModule,
            ),
        )
    }
}
