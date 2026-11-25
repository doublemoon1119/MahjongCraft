package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.MahjongAnimationSounds

/**
 * 胡牌倒牌與流局攤牌的牌動作，對局中的演出與牌譜回放共用，時間以遊戲 tick 計。
 *
 * - 胡牌：手牌先瞬間理好。
 *   - 自摸時自摸那張先單獨倒下，停一下再讓其餘的牌一起原地倒下。
 *   - 榮和與搶槓時理好後停一下，全部一起倒下。
 * - 流局：公開的手牌先理好，再一起倒下。不公開的手牌原地蓋下。
 */
object HandRevealSteps {
    /** 流局時公開的手牌理好位置的相對時刻。 */
    const val DRAW_REORDER_TICK: Long = 5L

    /** 流局時手牌倒下或蓋下的相對時刻。 */
    const val DRAW_REVEAL_TICK: Long = 30L

    /**
     * 胡牌倒牌各段開始的相對時刻。
     *
     * @param isTsumo 是否為自摸。自摸時自摸那張先單獨倒下。
     * @return 各段的開始與結束時刻。
     */
    fun winTimings(isTsumo: Boolean): WinRevealTimings {
        val reorderEnd = MahjongTileTableLayout.WIN_POST_REORDER_DELAY_TICKS.toLong()
        val winningTileLaydown = reorderEnd.takeIf { isTsumo }
        val handLaydown = if (winningTileLaydown != null) {
            winningTileLaydown + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS + MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS
        } else {
            reorderEnd + MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS
        }
        return WinRevealTimings(
            reorder = 0L,
            winningTileLaydown = winningTileLaydown,
            handLaydown = handLaydown,
            end = handLaydown + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS,
        )
    }

    /**
     * 在指定時刻把牌瞬間移到理好的位置。
     *
     * @param placement 理好後的位置與朝向。
     * @param startTick 開始的遊戲 tick。
     * @return 動作步驟。
     */
    fun reorder(placement: MahjongTileWallPlacement, startTick: Long): List<AnimationStep<TilePose>> = listOf(
        AnimationStep.WaitUntil(startTick),
        AnimationStep.Teleport(placement.x, placement.y, placement.z, placement.yaw),
    )

    /**
     * 在原地倒下成牌面朝上。已經牌面朝上時沒有動作。
     *
     * @param startPose 倒下前的姿態。
     * @param startTick 開始的遊戲 tick。
     * @param playSound 是否播放整組的翻牌聲。
     * @return 動作步驟。
     */
    fun laydown(startPose: TilePose, startTick: Long, playSound: Boolean): List<AnimationStep<TilePose>> = turn(startPose, TilePose.FACE_UP, startTick, playSound, easeRotation = false)

    /**
     * 在原地蓋下成牌背朝上。已經牌背朝上時沒有動作。
     *
     * @param startPose 蓋下前的姿態。
     * @param startTick 開始的遊戲 tick。
     * @param playSound 是否播放整組的翻牌聲。
     * @return 動作步驟。
     */
    fun conceal(startPose: TilePose, startTick: Long, playSound: Boolean): List<AnimationStep<TilePose>> = turn(startPose, TilePose.FACE_DOWN, startTick, playSound, easeRotation = true)

    /**
     * 選出負責播放整組翻牌聲的牌：在這次真的要倒下的牌中取中間那張，已平放的牌不參與。
     *
     * @param startPoses 各張牌倒下前的姿態，依呈現順序排列。
     * @return 負責播放聲音的牌在清單中的位置。全部已平放時為 null。
     */
    fun groupSoundIndex(startPoses: List<TilePose>): Int? {
        val turning = startPoses.indices.filter { startPoses[it] != TilePose.FACE_UP }
        return turning.getOrNull(turning.size / 2)
    }

    /**
     * 原地轉成指定姿態。
     *
     * @param startPose 轉動前的姿態。
     * @param endPose 轉動後的姿態。
     * @param startTick 開始的遊戲 tick。
     * @param playSound 是否播放整組的翻牌聲。
     * @param easeRotation 轉動是否加減速。
     * @return 動作步驟。姿態已相同時為空。
     */
    private fun turn(startPose: TilePose, endPose: TilePose, startTick: Long, playSound: Boolean, easeRotation: Boolean): List<AnimationStep<TilePose>> {
        if (startPose == endPose) return emptyList()
        return buildList {
            if (playSound) add(handTurnSound(startTick + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS))
            add(AnimationStep.WaitUntil(startTick))
            add(AnimationStep.Custom(endPose))
            add(
                element = AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = 0.0,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = startPose.rotationDegrees,
                    endPoseRotationDegrees = endPose.rotationDegrees,
                    easeRotation = easeRotation,
                ),
            )
        }
    }

    /**
     * 整組翻牌聲，只在預定時刻附近有效。
     *
     * @param tick 預定播放的遊戲 tick。
     * @return 聲音步驟。
     */
    private fun handTurnSound(tick: Long): AnimationStep.PlaySound = AnimationStep.PlaySound(
        soundId = MahjongAnimationSounds.TILE_HAND_TURN,
        volume = HAND_TURN_VOLUME,
        pitch = 1.0f,
        playAtGameTime = tick,
        expiresAtGameTime = tick + MahjongAnimationSounds.EVENT_GRACE_TICKS,
    )

    /** 整組翻牌聲的音量。 */
    private const val HAND_TURN_VOLUME: Float = 1.2f
}
