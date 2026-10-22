package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/** 驗證內建捨牌反應 policy 不遺漏來源牌且保留完整選牌。 */
class DefaultDiscardReactionPolicyTest {
    /** 內建鳴牌效果會把來源捨牌列為公開牌。 */
    @Test
    fun `meld claim exposes source tile`() {
        val sourceTile = IdentifiedTile(Uuid.random(), Tile.Numeric(Tile.Suit.Dot, 5))
        val handTileIds = listOf(Uuid.random(), Uuid.random())
        val action = GameAction.Pon(sourceTile.id, handTileIds)
        val context = DiscardReactionPolicyContext(
            tableState = FakeTableStateFactory.create(),
            discarderId = Uuid.random(),
            responderId = Uuid.random(),
            discardedTile = sourceTile,
            legalActions = listOf(action),
        )

        val candidate = assertNotNull(DefaultDiscardReactionPolicy.resolve(context).single())
        val effect = assertNotNull(candidate.claimEffect)

        assertEquals(handTileIds, candidate.selectedTileIds)
        assertContains(effect.publiclyRevealedTileIds, sourceTile.id)
        assertEquals(emptySet(), effect.faceDownTileIds)
    }

    /** 明槓候選要求由既有規則流程補牌。 */
    @Test
    fun `open kan claim requests supplemental draw`() {
        val sourceTile = IdentifiedTile(Uuid.random(), Tile.Numeric(Tile.Suit.Dot, 5))
        val action = GameAction.Kan(GameAction.KanType.OPEN_KAN, sourceTile.id, listOf(Uuid.random(), Uuid.random(), Uuid.random()))
        val context = DiscardReactionPolicyContext(
            tableState = FakeTableStateFactory.create(),
            discarderId = Uuid.random(),
            responderId = Uuid.random(),
            discardedTile = sourceTile,
            legalActions = listOf(action),
        )

        val effect = assertNotNull(DefaultDiscardReactionPolicy.resolve(context).single().claimEffect)

        assertEquals(true, effect.requiresSupplementalDraw)
    }
}
