package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
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

/**
 * 驗證 [RiichiSuufonRendaResolver]／[RiichiSuuchaRiichiResolver]／[RiichiSuukanNagareResolver]
 * 只回應各自對應的 [PostActionTrigger]——即使桌況本身已經符合成立條件，用錯觸發時機呼叫也不能誤判
 * 成立；這是把三個判定收斂進同一個 registry 之後最需要保護的正確性：不同途中流局共用同一個
 * [TableState] 形狀，唯一的區分依據就是呼叫時機。
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

    /** 驗證四風連打只在 [PostActionTrigger.DiscardCompleted] 成立，其餘時機一律回傳 null。 */
    @Test
    fun `suufon renda resolver only fires on discard completed trigger`() {
        val table = tableWithFirstDiscards(List(4) { Tile.Honor.East })
        val resolver = RiichiSuufonRendaResolver()

        assertEquals(RiichiExhaustiveDrawReason.SuufonRenda, resolver.resolve(PostActionTrigger.DiscardCompleted(table), riichiModule))
        assertNull(resolver.resolve(PostActionTrigger.RiichiDeclared(table), riichiModule))
        assertNull(resolver.resolve(PostActionTrigger.KanDeclared(table), riichiModule))
    }

    /** 驗證四家立直只在 [PostActionTrigger.RiichiDeclared] 成立，其餘時機一律回傳 null。 */
    @Test
    fun `suucha riichi resolver only fires on riichi declared trigger`() {
        val table = allRiichiTable()
        val resolver = RiichiSuuchaRiichiResolver()

        assertEquals(RiichiExhaustiveDrawReason.SuuchaRiichi, resolver.resolve(PostActionTrigger.RiichiDeclared(table), riichiModule))
        assertNull(resolver.resolve(PostActionTrigger.DiscardCompleted(table), riichiModule))
        assertNull(resolver.resolve(PostActionTrigger.KanDeclared(table), riichiModule))
    }

    /** 驗證四槓散了只在 [PostActionTrigger.KanDeclared] 成立，其餘時機一律回傳 null。 */
    @Test
    fun `suukan nagare resolver only fires on kan declared trigger`() {
        val table = allKansTable()
        val resolver = RiichiSuukanNagareResolver()

        assertEquals(RiichiExhaustiveDrawReason.SuukanNagare, resolver.resolve(PostActionTrigger.KanDeclared(table), riichiModule))
        assertNull(resolver.resolve(PostActionTrigger.DiscardCompleted(table), riichiModule))
        assertNull(resolver.resolve(PostActionTrigger.RiichiDeclared(table), riichiModule))
    }

    /** 驗證三個 resolver 面對非日麻規則模組時一律回傳 null，即使觸發時機正確。 */
    @Test
    fun `resolvers return null for a non-riichi rule module`() {
        val discardTable = tableWithFirstDiscards(List(4) { Tile.Honor.East })
        val riichiTable = allRiichiTable()
        val kanTable = allKansTable()

        assertNull(RiichiSuufonRendaResolver().resolve(PostActionTrigger.DiscardCompleted(discardTable), taiwanModule))
        assertNull(RiichiSuuchaRiichiResolver().resolve(PostActionTrigger.RiichiDeclared(riichiTable), taiwanModule))
        assertNull(RiichiSuukanNagareResolver().resolve(PostActionTrigger.KanDeclared(kanTable), taiwanModule))
    }
}
