package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證內建日麻模擬的終局原因與必要條件，擊飛可提早結束但不能掩蓋不完整流程。
 * @param config 該場模擬使用的實際日麻設定。
 * @param roundCount 已記錄的實際局數，包含連莊局。
 * @param matchEndReasonId 正式流程記錄的終局原因。
 * @param finalScoresByPlayer 最後一次權威結算的玩家分數。
 */
internal fun assertLegalRiichiMatchCompletion(
    config: RiichiFamilyRuleConfig,
    roundCount: Int,
    matchEndReasonId: String?,
    finalScoresByPlayer: Map<Uuid, Int>,
) {
    val diagnostic = "rounds played: $roundCount; end reason: $matchEndReasonId; final scores: $finalScoresByPlayer"
    assertTrue(roundCount > 0, "No completed round; $diagnostic")
    assertTrue(finalScoresByPlayer.isNotEmpty(), "Missing final scores; $diagnostic")
    when (matchEndReasonId) {
        BuiltInMatchEndReasonIds.PLAYER_BUSTED -> {
            val threshold = assertNotNull(config.scoreConfig.bustThreshold, "Bust ending without a configured threshold; $diagnostic")
            assertTrue(finalScoresByPlayer.values.any { it < threshold }, "Bust ending without a busted player; $diagnostic")
        }
        BuiltInMatchEndReasonIds.SCHEDULE_COMPLETED,
        BuiltInMatchEndReasonIds.TARGET_SCORE_REACHED,
        BuiltInMatchEndReasonIds.EXTRA_ROUND_LIMIT_REACHED,
        BuiltInMatchEndReasonIds.DEALER_TOP_FINISH,
        -> assertTrue(roundCount >= config.scheduledRoundCount, "Schedule ending before the minimum rounds; $diagnostic")
        else -> throw AssertionError("Missing or unexpected match end reason; $diagnostic")
    }
}
