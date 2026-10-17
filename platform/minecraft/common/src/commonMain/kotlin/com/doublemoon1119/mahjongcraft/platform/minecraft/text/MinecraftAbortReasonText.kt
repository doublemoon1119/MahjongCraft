package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MinecraftMatchAbortReasonIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys

/**
 * 將已知的中止原因轉成玩家用語，未知原因不顯示內部識別碼或診斷。
 *
 * @param reasonId 對局中止的完整原因識別碼。
 * @return 訊息與歷史呈現共用的翻譯 key。
 */
fun minecraftAbortReasonTranslationKey(reasonId: String): String = when (reasonId) {
    MinecraftMatchAbortReasonIds.TABLE_MISSING -> MinecraftHistoryScreenKeys.ROUND_ABORT_REASON_TABLE_MISSING
    MinecraftMatchAbortReasonIds.TABLE_BROKEN_BY_PLAYER -> MinecraftHistoryScreenKeys.ROUND_ABORT_REASON_TABLE_BROKEN_BY_PLAYER
    GameAbortReasonIds.MISSING_WIN_SETTLEMENT, GameAbortReasonIds.MISSING_DRAW_SETTLEMENT -> MinecraftMessageKeys.GAME_ABORT_REASON_SETTLEMENT
    GameAbortReasonIds.INVALID_PROGRESSION -> MinecraftMessageKeys.GAME_ABORT_REASON_PROGRESSION
    GameAbortReasonIds.AUTOMATIC_DECISION_FAILED -> MinecraftMessageKeys.GAME_ABORT_REASON_AUTOMATIC
    GameAbortReasonIds.RULE_EXECUTION_FAILED -> MinecraftMessageKeys.GAME_ABORT_REASON_RULE
    else -> MinecraftMessageKeys.GAME_ABORT_REASON_UNKNOWN
}
