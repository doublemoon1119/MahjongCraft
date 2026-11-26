package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiComponentRegistryForTests
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證透明牌在還沒摸到時不公開，離開摸牌序列與保留牌後對所有人公開。 */
class TransparentTileComponentsTest {
    /** 牌山與保留牌中的透明牌不公開。手牌中的透明牌對所有人公開，同一手的普通牌仍只有自己知道。 */
    @Test
    fun `transparent tiles are public only once drawn`() {
        val inWall = transparent(Tile.Numeric(Tile.Suit.Dot, 5))
        val inReserve = transparent(Tile.Numeric(Tile.Suit.Dot, 6))
        val inHand = transparent(Tile.Numeric(Tile.Suit.Dot, 7))
        val plain = IdentifiedTile(Uuid.random(), Tile.Numeric(Tile.Suit.Dot, 8))
        val config = RiichiRuleConfig(transparentTiles = true, buildsWall = false, redDoraCount = 0)
        val owner = FakeMahjongPlayerFactory.create(hand = Hand(tiles = listOf(inHand, plain)))
        val other = FakeMahjongPlayerFactory.create(hand = Hand())
        val table = FakeTableStateFactory.create(players = listOf(owner, other), config = config)
            .copy(tileWall = TileWall(listOf(inWall)), reservedWallTiles = listOf(inReserve))
        val module = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, config)

        val resolved = module.resolveTileComponents(table, riichiComponentRegistryForTests()).associateBy { it.area.tile.id }

        assertFalse(resolved.getValue(inWall.id).canRead(TileReadContext.Public()))
        assertFalse(resolved.getValue(inReserve.id).canRead(TileReadContext.Public()))
        assertTrue(resolved.getValue(inHand.id).canRead(TileReadContext.Public()))
        assertTrue(resolved.getValue(inHand.id).canRead(TileReadContext.Player(other.id)))
        assertFalse(resolved.getValue(plain.id).canRead(TileReadContext.Player(other.id)))
        assertTrue(resolved.getValue(plain.id).canRead(TileReadContext.Player(owner.id)))
    }

    /**
     * 建立帶透明牌組件的牌張。
     *
     * @param tile 牌面。
     * @return 透明牌。
     */
    private fun transparent(tile: Tile) = IdentifiedTile(Uuid.random(), tile, ComponentSet.EMPTY.with(BuiltInTileComponents.TRANSPARENT, Unit))
}
