package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 玩家資訊上短暫顯示的一次分數增減，例如動作成立當下的收付。
 *
 * @property seatIndex 增減的玩家座位。
 * @property delta 點數增減，不可為 0。
 * @property startGameTime 開始顯示的遊戲時間。
 * @property endGameTime 停止顯示的遊戲時間，不含這個時刻。
 */
data class MahjongPlayerScoreChange(
    val seatIndex: Int,
    val delta: Int,
    val startGameTime: Long,
    val endGameTime: Long,
) {
    init {
        require(delta != 0) { "Score change must not be zero" }
        require(endGameTime > startGameTime) { "Score change must be shown for a positive duration" }
    }

    /**
     * 指定時刻是否正在顯示。
     *
     * @param gameTime 目前遊戲時間。
     * @return 在顯示期間內時為 true。
     */
    fun isShownAt(gameTime: Long): Boolean = gameTime in startGameTime until endGameTime

    companion object {
        /** 每次增減顯示的時間。 */
        const val DISPLAY_DURATION_TICKS: Long = 60L
    }
}

/**
 * 指定座位在某時刻正在顯示的增減合計；同時有多筆時加總。
 *
 * @param seatIndex 玩家座位。
 * @param gameTime 目前遊戲時間。
 * @return 正在顯示的增減合計；沒有或合計為 0 時為 null。
 */
fun List<MahjongPlayerScoreChange>.shownDeltaFor(seatIndex: Int, gameTime: Long): Int? = filter { it.seatIndex == seatIndex && it.isShownAt(gameTime) }
    .sumOf { it.delta.toLong() }
    .takeIf { it != 0L }
    ?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
    ?.toInt()
