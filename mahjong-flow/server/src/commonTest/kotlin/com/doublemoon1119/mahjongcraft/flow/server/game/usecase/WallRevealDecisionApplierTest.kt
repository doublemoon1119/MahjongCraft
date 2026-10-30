package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPendingKanDoraReveal
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiWallProgress
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiPotPoints
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiWallProgress
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.toTableComponentPatch
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealDecision
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileFaceAccess
import com.doublemoon1119.mahjongcraft.logic.tile.component.publicFaceUpWallTileIds
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiTableComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證牌牆公開使用共同姿態及局部組件更新，不把可讀取牌面等同於翻牌。 */
class WallRevealDecisionApplierTest {
    /** 全部測試沿用同一份正式組件定義。 */
    private val components = bundledCoreExtensionRegistries().componentRegistry

    /** 受測的日麻規則。 */
    private val module = RiichiRuleModule("test:wall_reveal", RiichiRuleConfig())

    /** 正確的新公開牌集合可成立，供託及其他既有資料不被覆蓋。 */
    @Test
    fun `valid reveal updates progress while preserving pot points`() {
        val table = fixture()
        val original = table.riichiWallProgress()
        val updated = original.copy(revealedKanDoraCount = 1, pendingKanDoraReveals = emptyList())
        val expectedIds = updated.getDoraIndicators(table).first.map { it.id }.toSet() -
            original.getDoraIndicators(table).first.map { it.id }.toSet()
        val result = assertIs<WallRevealDecisionApplier.Result.Applied>(
            WallRevealDecisionApplier.applyUpdatedDecision(
                tableState = table,
                decision = WallRevealDecision.Updated(updated.toTableComponentPatch(original), expectedIds),
                module = module,
                componentRegistry = components,
            ),
        )
        assertEquals(updated, result.tableState.riichiWallProgress())
        assertEquals(2000, result.tableState.riichiPotPoints())
        assertEquals(expectedIds, result.newlyRevealedTileIds)
        assertTrue(result.tableState.publicTileEvidenceIds.containsAll(expectedIds))
        assertEquals(original, table.riichiWallProgress())
    }

    /** 不能把不存在於當前牌牆的 UUID 宣告為新公開牌。 */
    @Test
    fun `unknown newly revealed tile id is rejected`() {
        val table = fixture()
        val result = WallRevealDecisionApplier.applyUpdatedDecision(
            tableState = table,
            decision = WallRevealDecision.Updated(table.riichiWallProgress().toTableComponentPatch(table.riichiWallProgress()), setOf(Uuid.random())),
            module = module,
            componentRegistry = components,
        )
        assertEquals(WallRevealDecisionApplier.Result.Rejected(WallRevealDecisionApplier.INVALID_RESULT_REASON_ID), result)
    }

    /** 可讀取但朝下的牌山牌不會混入共同翻牌集合。 */
    @Test
    fun `public face down wall tile is excluded from face up presentation`() {
        val readable = IdentifiedTile(
            id = Uuid.random(),
            tile = Tile.Honor.Red,
            components = ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public),
        )
        val table = fixture().copy(tileWall = TileWall(listOf(readable)))
        val faceUpIds = module.publicFaceUpWallTileIds(table, components)
        assertFalse(readable.id in faceUpIds)
        assertEquals(table.riichiWallProgress().getDoraIndicators(table).first.map { it.id }.toSet(), faceUpIds)
    }

    /** 建立一筆待公開追加指示牌，同時保留兩千點供託。 */
    private fun fixture(): TableState {
        val player = FakeMahjongPlayerFactory.create()
        return FakeTableStateFactory.create(
            players = listOf(player),
            config = module.config,
            reservedWallTiles = List(14) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 3)) },
            components = riichiTableComponents(
                potPoints = 2000,
                wallProgress = RiichiWallProgress(
                    completedSupplementalDrawCount = 1,
                    revealedKanDoraCount = 0,
                    pendingKanDoraReveals = listOf(RiichiPendingKanDoraReveal(player.id, GameAction.KanType.OPEN_KAN, 1)),
                ),
            ),
        )
    }
}
