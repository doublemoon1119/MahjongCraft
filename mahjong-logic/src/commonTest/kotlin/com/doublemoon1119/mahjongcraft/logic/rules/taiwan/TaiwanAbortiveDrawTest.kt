package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證台灣麻將固定四槓流局與可選四風連打。 */
class TaiwanAbortiveDrawTest {
    /** 建立四張一萬組成的暗槓。 */
    private fun closedKan(): Meld {
        val tiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)) }
        return Meld(
            type = MeldType.ClosedKan,
            tiles = tiles,
            sourceDirection = RelativeDirection.Self,
            publiclyRevealedTileIds = emptySet(),
            faceDownTileIds = tiles.map { it.id }.toSet(),
        )
    }

    /** 建立四位玩家各自只打出一張指定牌的桌況。 */
    private fun firstDiscards(config: TaiwanRuleConfig, tiles: List<Tile>) = FakeTableStateFactory.create(
        config = config,
        players = tiles.map { tile ->
            FakeMahjongPlayerFactory.create(
                discardPile = TaiwanDiscardPile().discardTile(FakeIdentifiedTileFactory.create(tile)),
            )
        },
    )

    /** 四風連打預設關閉，即使四家首張牌相同也不流局。 */
    @Test
    fun `four winds is disabled by default`() {
        val module = TaiwanRuleModule("test:taiwan", TaiwanRuleConfig())
        val table = firstDiscards(module.config, List(4) { Tile.Honor.East })

        assertNull(module.resolveFourWinds(table))
    }

    /** 開啟選項後，四家首張相同風牌會觸發四風連打。 */
    @Test
    fun `enabled four winds resolves matching first wind discards`() {
        val module = TaiwanRuleModule("test:taiwan", TaiwanRuleConfig(fourWindsAbortiveDraw = true))
        val table = firstDiscards(module.config, List(4) { Tile.Honor.South })

        assertEquals(TaiwanExhaustiveDrawReason.FourWinds, module.resolveFourWinds(table))
    }

    /** 即使同一位玩家完成全部四槓，台灣麻將仍固定觸發四槓流局。 */
    @Test
    fun `four kans resolves when one player owns every kan`() {
        val module = TaiwanRuleModule("test:taiwan", TaiwanRuleConfig())
        val players = listOf(
            FakeMahjongPlayerFactory.create(hand = Hand(melds = List(4) { closedKan() })),
            FakeMahjongPlayerFactory.create(),
            FakeMahjongPlayerFactory.create(),
            FakeMahjongPlayerFactory.create(),
        )
        val table = FakeTableStateFactory.create(config = module.config, players = players)

        assertEquals(TaiwanExhaustiveDrawReason.FourKans, module.resolveFourKans(table))
    }

    /** 全桌槓數未滿四個時不會提前流局。 */
    @Test
    fun `fewer than four kans does not resolve`() {
        val module = TaiwanRuleModule("test:taiwan", TaiwanRuleConfig())
        val players = List(3) { FakeMahjongPlayerFactory.create(hand = Hand(melds = listOf(closedKan()))) } +
            FakeMahjongPlayerFactory.create()
        val table = FakeTableStateFactory.create(config = module.config, players = players)

        assertNull(module.resolveFourKans(table))
    }
}
