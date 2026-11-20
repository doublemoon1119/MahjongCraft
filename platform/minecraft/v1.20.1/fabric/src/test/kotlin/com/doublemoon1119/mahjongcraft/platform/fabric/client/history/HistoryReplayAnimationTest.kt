package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.MahjongAnimationSounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryReplayArrangement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ArrangedTile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileRowKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證回放往後一步時，牌依對局中同一套規劃播放動畫並在落地時播放聲音。 */
class HistoryReplayAnimationTest {
    /** 打出一張牌：只有那張牌有動作，飛行途中不在終點，播完停在牌河並播放一次捨牌聲。 */
    @Test
    fun `a discard flies to the river and lands with one sound`() {
        val discarded = tile(index = 0, region = TileRegion.HAND, placement = HAND, pose = TilePose.UPRIGHT)
        val untouched = tile(index = 1, region = TileRegion.HAND, placement = OTHER, pose = TilePose.UPRIGHT)
        val landed = tile(index = 0, region = TileRegion.DISCARD, placement = RIVER, pose = TilePose.FACE_UP)
        val animation = HistoryReplayAnimation(roundNumber = 1, toStep = 3, before = mapOf(0 to discarded, 1 to untouched), after = mapOf(0 to landed, 1 to untouched))

        val firstSounds = animation.advance(animation.durationTicks / 2)
        val middle = animation.samples()
        val lastSounds = animation.advance(animation.durationTicks)

        assertEquals(MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS.toDouble(), animation.durationTicks)
        assertEquals(setOf(0), middle.keys)
        assertNotEquals(RIVER, middle.getValue(0).placement)
        assertTrue(firstSounds.isEmpty())
        assertEquals(listOf(MahjongAnimationSounds.TILE_DISCARD_LAND), lastSounds.map { it.soundId })
        assertTrue(animation.finished)
        assertEquals(RIVER, animation.samples().getValue(0).placement)
    }

    /** 前後擺放相同時沒有動作，動畫立刻結束。 */
    @Test
    fun `an unchanged table has nothing to animate`() {
        val still = mapOf(0 to tile(index = 0, region = TileRegion.HAND, placement = HAND, pose = TilePose.UPRIGHT))
        val animation = HistoryReplayAnimation(roundNumber = 1, toStep = 1, before = still, after = still)

        assertEquals(0.0, animation.durationTicks)
        assertTrue(animation.finished)
        assertTrue(animation.samples().isEmpty())
    }

    /**
     * 建立一張擺好的牌。
     *
     * @param index 局內牌索引。
     * @param region 所在區域。
     * @param placement 位置。
     * @param pose 姿態。
     * @return 擺好的牌。
     */
    private fun tile(index: Int, region: TileRegion, placement: MahjongTileWallPlacement, pose: TilePose): ArrangedTile = ArrangedTile(
        tileId = HistoryReplayArrangement.tileId(index),
        row = TileRowKey(region, PLAYER, null),
        orderInRow = index,
        placement = placement,
        pose = pose,
    )

    /** 測試共用的位置。 */
    private companion object {
        /** 手牌中的位置。 */
        val HAND = MahjongTileWallPlacement(0.2, 1.0, 1.4, 0.0f)

        /** 另一張不動的手牌。 */
        val OTHER = MahjongTileWallPlacement(0.3, 1.0, 1.4, 0.0f)

        /** 牌河中的位置。 */
        val RIVER = MahjongTileWallPlacement(0.5, 1.0, 0.8, 0.0f)

        /** 牌列的主人。 */
        val PLAYER: Uuid = HistoryReplayArrangement.playerId(0)
    }
}
