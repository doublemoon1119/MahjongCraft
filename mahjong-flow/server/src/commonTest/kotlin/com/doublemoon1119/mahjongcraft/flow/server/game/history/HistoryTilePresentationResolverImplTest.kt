package com.doublemoon1119.mahjongcraft.flow.server.game.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTileAudience
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileComponentViews
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeMeldFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiComponentRegistryForTests
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiTableComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證牌譜呈現使用與觀察者快照相同的解算：外觀、公開特徵、姿態與知道牌面的讀取者。 */
class HistoryTilePresentationResolverImplTest {
    /** 手牌中的赤五只有持有者知道，帶赤五外觀與赤寶牌特徵；捨牌全體公開；暗槓兩端朝下。 */
    @Test
    fun `presentation follows the resolved components of every area`() {
        val owner = FakeMahjongPlayerFactory.create().id
        val redFive = FakeIdentifiedTileFactory.create(RiichiTileValues.redFive(Tile.Suit.Dot))
        val discarded = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val kanTiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }
        val closedKan = FakeMeldFactory.create(type = MeldType.ClosedKan, tiles = kanTiles)
        val holder = FakeMahjongPlayerFactory.create(
            id = owner,
            hand = Hand(tiles = listOf(redFive), melds = listOf(closedKan)),
            discardPile = RiichiDiscardPile().discardTile(discarded),
        )
        val other = FakeMahjongPlayerFactory.create()
        val state = FakeTableStateFactory.create(
            players = listOf(holder, other),
            config = RiichiRuleConfig(),
            components = riichiTableComponents(potPoints = 0),
        )

        val presentations = resolver().resolve(state)

        val red = presentations.getValue(redFive.id)
        assertEquals(RiichiTileValues.RED_FIVE_APPEARANCE_ID, red.appearanceId)
        assertEquals(Unit, red.views[RiichiTileComponentViews.RED_DORA])
        assertEquals(TilePose.UPRIGHT, red.pose)
        assertEquals(HistoryTileAudience.Players(setOf(owner)), red.audience)
        assertEquals(HistoryTileAudience.Public, presentations.getValue(discarded.id).audience)
        assertEquals(TilePose.FACE_DOWN, presentations.getValue(kanTiles.first().id).pose)
        assertEquals(TilePose.FACE_DOWN, presentations.getValue(kanTiles.last().id).pose)
    }

    /**
     * 建立使用日麻規則與完整組件登記的解算器。
     *
     * @return 測試用解算器。
     */
    private fun resolver(): HistoryTilePresentationResolverImpl = HistoryTilePresentationResolverImpl(
        moduleRegistry = MahjongModuleRegistryImpl().apply {
            register(MahjongModuleDefinition(RiichiRuleConfig::class, "test:riichi") { config, id -> RiichiRuleModule(id, config) })
            freeze()
        },
        componentRegistry = riichiComponentRegistryForTests(),
    )
}
