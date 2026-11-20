package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongBotArmAnimation
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.MahjongAnimationSounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HandReveal
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryReplayArrangement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ArrangedTile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.HandRevealSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileRowKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.rotationDegrees
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

        // 這一步打出牌的玩家會揮手，動畫至少持續到揮手做完。
        assertEquals(maxOf(MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS, MahjongBotArmAnimation.DURATION_TICKS).toDouble(), animation.durationTicks)
        assertEquals(setOf(0), middle.keys)
        assertEquals(setOf(0), animation.movingTiles)
        assertNotEquals(RIVER, middle.getValue(0).placement)
        assertTrue(firstSounds.isEmpty())
        assertEquals(listOf(MahjongAnimationSounds.TILE_DISCARD_LAND), lastSounds.map { it.soundId })
        assertTrue(animation.finished)
        assertEquals(RIVER, animation.samples().getValue(0).placement)
    }

    /** 打出牌的玩家在動畫開頭揮一次手，做完回到 0；其他玩家不揮手。 */
    @Test
    fun `the discarding player swings once at the start`() {
        val discarded = tile(index = 0, region = TileRegion.HAND, placement = HAND, pose = TilePose.UPRIGHT)
        val landed = tile(index = 0, region = TileRegion.DISCARD, placement = RIVER, pose = TilePose.FACE_UP)
        val animation = HistoryReplayAnimation(roundNumber = 1, toStep = 3, before = mapOf(0 to discarded), after = mapOf(0 to landed))

        assertEquals(0f, animation.swingProgress(0))
        animation.advance(MahjongBotArmAnimation.DURATION_TICKS / 2.0)
        assertEquals(0.5f, animation.swingProgress(0))
        assertEquals(0f, animation.swingProgress(1))
        animation.advance(MahjongBotArmAnimation.DURATION_TICKS.toDouble())
        assertEquals(0f, animation.swingProgress(0))
    }

    /** 牌不是從手上進牌河時沒有人揮手，例如摸牌。 */
    @Test
    fun `a draw has no swing`() {
        val wall = ArrangedTile(
            tileId = HistoryReplayArrangement.tileId(0),
            row = TileRowKey(TileRegion.WALL, null, null),
            orderInRow = 0,
            placement = RIVER,
            pose = TilePose.FACE_DOWN,
        )
        val drawn = tile(index = 0, region = TileRegion.DRAWN, placement = HAND, pose = TilePose.UPRIGHT)
        val animation = HistoryReplayAnimation(roundNumber = 1, toStep = 2, before = mapOf(0 to wall), after = mapOf(0 to drawn))

        animation.advance(1.0)
        assertEquals(0f, animation.swingProgress(0))
    }

    /** 榮和時贏家的手牌照對局中的倒牌步驟一起倒下，整組只播一次翻牌聲，動畫等倒完才結束。 */
    @Test
    fun `a ron win lays the hand down like the live table`() {
        val first = tile(index = 0, region = TileRegion.HAND, placement = HAND, pose = TilePose.UPRIGHT)
        val second = tile(index = 1, region = TileRegion.HAND, placement = OTHER, pose = TilePose.UPRIGHT)
        val before = mapOf(0 to first, 1 to second)
        val after = mapOf(0 to first.copy(pose = TilePose.FACE_UP), 1 to second.copy(pose = TilePose.FACE_UP))
        val animation = HistoryReplayAnimation(
            roundNumber = 1,
            toStep = 5,
            before = before,
            after = after,
            reveals = listOf(HistoryReplayHandRevealMotion(tiles = listOf(0, 1), reveal = HandReveal.Win(winningTile = null))),
        )

        assertEquals(HandRevealSteps.winTimings(isTsumo = false).end.toDouble(), animation.durationTicks)
        val sounds = animation.advance(animation.durationTicks)
        assertEquals(listOf(MahjongAnimationSounds.TILE_HAND_TURN), sounds.map { it.soundId })
        assertEquals(TilePose.FACE_UP.rotationDegrees, animation.samples().getValue(0).poseDegrees)
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
