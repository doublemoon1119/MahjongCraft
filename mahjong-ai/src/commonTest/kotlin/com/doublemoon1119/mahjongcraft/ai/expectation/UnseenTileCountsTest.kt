package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.hand
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.m
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.module
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.p
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.player
import com.doublemoon1119.mahjongcraft.ai.expectation.ExpectationFixtures.table
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileComponentViews
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileComponents
import com.doublemoon1119.mahjongcraft.logic.table.PlayerTileAreas
import com.doublemoon1119.mahjongcraft.logic.table.TileWallSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiDeclarationComponents
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiTableComponents
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.toRiichiSnapshotForTests
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.withRedFives
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證未見牌計數依資訊範圍扣除可見牌。 */
class UnseenTileCountsTest {
    /** 自己手上兩張、摸到一張、對手牌河一張五萬的桌況。 */
    private val self = player(Wind.SOUTH, hand = hand(listOf(m(5), m(5)), drawn = m(5)))
    private val opponent = player(Wind.EAST, discards = listOf(m(5), Tile.Honor.North))
    private val table = table(listOf(opponent, self))

    /** 只扣除自己的手牌時，對手牌河中的牌仍算未見；剛摸到的牌只算一次。 */
    @Test
    fun `without counting visible tiles only the own hand is deducted`() {
        val counts = UnseenTileCounts.from(table.toRiichiSnapshotForTests(self.id), self.id, module, countsVisibleTiles = false)

        assertEquals(1, counts[m(5)])
        assertEquals(4, counts[Tile.Honor.North])
        assertEquals(136 - 3, counts.total)
    }

    /** 扣除所有可見牌時，對手牌河中的牌也一併扣除；看到的普通五萬多於牌山中的張數時，改從赤五萬扣除。 */
    @Test
    fun `counting visible tiles also deducts discards`() {
        val counts = UnseenTileCounts.from(table.toRiichiSnapshotForTests(self.id), self.id, module, countsVisibleTiles = true)

        assertEquals(0, counts[m(5)])
        assertEquals(3, counts[Tile.Honor.North])
    }

    /** 赤五與普通五算同一種牌。 */
    @Test
    fun `a red five is counted as a five`() {
        val withRed = player(Wind.SOUTH, hand = hand(listOf(p(5), p(5))).withRedFives(Tile.Suit.Dot))
        val counts = UnseenTileCounts.from(table(listOf(opponent, withRed)).toRiichiSnapshotForTests(withRed.id), withRed.id, module, countsVisibleTiles = false)

        assertEquals(2, counts[p(5)])
        assertEquals(34, counts.kinds.size)
    }

    /** 赤五與普通五另外記錄各自的未見張數：手上一張普通五筒時，剩下普通五筒 2 張、赤五筒 1 張。 */
    @Test
    fun `red and plain fives are tracked separately`() {
        val withPlain = player(Wind.SOUTH, hand = hand(listOf(p(5))))
        val counts = UnseenTileCounts.from(table(listOf(opponent, withPlain)).toRiichiSnapshotForTests(withPlain.id), withPlain.id, module, countsVisibleTiles = false)

        assertEquals(setOf(TileValue(p(5)) to 2, RED_FIVE_DOT to 1), counts.faces(p(5)).toSet())
        assertEquals(listOf(TileValue(Tile.Honor.North) to 4), counts.faces(Tile.Honor.North))
    }

    /** 假設摸進赤五筒後，只少掉赤五筒；張數為 0 的牌面不列出。 */
    @Test
    fun `drawing a red five removes only the red copy`() {
        val withPlain = player(Wind.SOUTH, hand = hand(listOf(p(5))))
        val counts = UnseenTileCounts.from(table(listOf(opponent, withPlain)).toRiichiSnapshotForTests(withPlain.id), withPlain.id, module, countsVisibleTiles = false)
            .without(RED_FIVE_DOT)

        assertEquals(listOf(TileValue(p(5)) to 2), counts.faces(p(5)))
        assertEquals(2, counts[p(5)])
    }

    /** 假設摸進一張後，該種牌的未見張數減一。 */
    @Test
    fun `drawing a kind removes one unseen copy`() {
        val counts = UnseenTileCounts.from(table.toRiichiSnapshotForTests(self.id), self.id, module, countsVisibleTiles = false)

        assertEquals(3, counts.without(TileValue(Tile.Honor.North))[Tile.Honor.North])
        assertEquals(counts.total - 1, counts.without(TileValue(Tile.Honor.North)).total)
    }

    /** 自己拔出的北一律扣除；扣除所有可見牌時，他家拔出的北也一併扣除。 */
    @Test
    fun `pulled north tiles are deducted`() {
        val threePlayerModule = ThreePlayerRiichiRuleModule(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, ThreePlayerRiichiRuleConfig())
        val puller = player(Wind.EAST, hand = hand(listOf(p(1))))
            .copy(components = riichiDeclarationComponents(), tileAreas = PlayerTileAreas.EMPTY.withArea(RiichiPlayerTileAreaIds.NORTH, listOf(FakeIdentifiedTileFactory.create(Tile.Honor.North))))
        val otherPuller = player(Wind.SOUTH)
            .copy(components = riichiDeclarationComponents(), tileAreas = PlayerTileAreas.EMPTY.withArea(RiichiPlayerTileAreaIds.NORTH, List(2) { FakeIdentifiedTileFactory.create(Tile.Honor.North) }))
        val threePlayerTable = FakeTableStateFactory.create(
            players = listOf(puller, otherPuller, player(Wind.WEST)),
            config = ThreePlayerRiichiRuleConfig(),
            components = riichiTableComponents(potPoints = 0),
        )
        val snapshot = threePlayerTable.toRiichiSnapshotForTests(puller.id)

        val ownOnly = UnseenTileCounts.from(snapshot, puller.id, threePlayerModule, countsVisibleTiles = false)
        val allVisible = UnseenTileCounts.from(snapshot, puller.id, threePlayerModule, countsVisibleTiles = true)

        assertEquals(3, ownOnly[Tile.Honor.North])
        assertEquals(1, allVisible[Tile.Honor.North])
    }

    /** 公開仍可能摸到的牌牆牌不減少候選張數，只有指示牌等已公開用途才扣除。 */
    @Test
    fun `visible wall faces remain candidates unless their public role excludes drawing`() {
        val base = table.toRiichiSnapshotForTests(self.id).copy(tileWall = TileWallSnapshot(emptyList()))
        val face = TileFaceView(p(1))
        val liveTile = IdentifiedTileSnapshot(Uuid.random(), face)
        val withKnownWall = base.copy(tileWall = TileWallSnapshot(listOf(liveTile)))
        val withIndicator = base.copy(
            tileWall = TileWallSnapshot(
                listOf(
                    liveTile.copy(
                        face = face.copy(views = ComponentViewSet.EMPTY.with(RiichiTileComponentViews.DORA_INDICATOR, Unit)),
                    ),
                ),
            ),
        )
        val baseline = UnseenTileCounts.from(base, self.id, module, countsVisibleTiles = true)
        val known = UnseenTileCounts.from(withKnownWall, self.id, module, countsVisibleTiles = true)
        val indicator = UnseenTileCounts.from(withIndicator, self.id, module, countsVisibleTiles = true)

        assertEquals(baseline[p(1)], known[p(1)])
        assertEquals(baseline.total, known.total)
        assertEquals(baseline[p(1)] - 1, indicator[p(1)])
        assertEquals(baseline.total - 1, indicator.total)
    }

    private companion object {
        /** 計算用的赤五筒牌值：只帶赤寶牌特性，不帶外觀。 */
        val RED_FIVE_DOT: TileValue = TileValue(p(5), ComponentSet.EMPTY.with(RiichiTileComponents.RED_DORA, Unit))
    }
}
