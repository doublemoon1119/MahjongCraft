package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.uuid.Uuid

/**
 * 依序呈現的一步牌區動畫，開始時刻已依 [PlayerAreaSequenceTimeline] 排定。
 *
 * @property setAsideTileIds 這一步從手牌飛到移出區的牌。
 * @property drawnTileIds 這一步依序從牌牆摸入的牌。
 * @property startTicks 這一步相對於呈現開始的開始時刻。
 */
data class SequencedAreaAnimationStep(
    val setAsideTileIds: List<Uuid>,
    val drawnTileIds: List<Uuid>,
    val startTicks: Long,
) {
    init {
        require(startTicks >= 0L) { "Sequenced step start must not be negative" }
    }

    /**
     * 第 [index] 張摸入的牌開始離開牌牆的時刻：移出的牌落地後，依序一張接一張。
     *
     * @param index 摸入牌在 [drawnTileIds] 中的索引。
     * @return 相對於呈現開始的 tick 數。
     */
    fun drawStartTicks(index: Int): Long = PlayerAreaSequenceTimeline.drawStartTicks(startTicks, hasSetAside = setAsideTileIds.isNotEmpty(), index = index)
}
