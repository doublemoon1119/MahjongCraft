package com.doublemoon1119.mahjongcraft.flow.common.game.model

/**
 * 規則層胡牌結果解析出的平台無關展示提示。
 *
 * [key] 是穩定識別字串；[args] 僅攜帶解析標題等宣告式呈現資料，不包含平台物件或可執行 callback。
 */
data class WinCelebrationCue(val key: String, val args: List<String> = emptyList())

/** 批次胡牌展示中的單一贏家。 */
data class WinCelebrationWinner(val seatIndex: Int, val cue: WinCelebrationCue?)

/**
 * 一次胡牌共用的批次展示請求；多家榮和共享同一張 [winningTileId]。
 */
data class WinCelebrationRequest(
    val winningTileId: kotlin.uuid.Uuid,
    val isTsumo: Boolean,
    val winners: List<WinCelebrationWinner>,
)
