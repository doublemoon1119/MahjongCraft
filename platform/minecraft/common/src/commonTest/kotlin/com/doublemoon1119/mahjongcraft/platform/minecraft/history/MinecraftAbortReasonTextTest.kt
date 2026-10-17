package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MinecraftMatchAbortReasonIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.minecraftAbortReasonTranslationKey
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證中止原因只會轉成玩家可見的本地化 key，不會把管理診斷帶入訊息。 */
class MinecraftAbortReasonTextTest {
    /** 已知桌子缺失原因應使用對應翻譯。 */
    @Test
    fun `known missing table reason uses localized key`() {
        assertEquals(
            MinecraftHistoryScreenKeys.ROUND_ABORT_REASON_TABLE_MISSING,
            minecraftAbortReasonTranslationKey(MinecraftMatchAbortReasonIds.TABLE_MISSING),
        )
    }

    /** 未知原因應使用通用翻譯，而不是把原因內容當成原始文字。 */
    @Test
    fun `unknown reason uses generic localized key`() {
        assertEquals(
            MinecraftMessageKeys.GAME_ABORT_REASON_UNKNOWN,
            minecraftAbortReasonTranslationKey("test:unknown"),
        )
    }

    /** 規則與代打失敗都必須使用對應原因，不能顯示成其他結算。 */
    @Test
    fun `rule and automatic failures have specific localized wording`() {
        val expected = mapOf(
            GameAbortReasonIds.MISSING_WIN_SETTLEMENT to MinecraftMessageKeys.GAME_ABORT_REASON_SETTLEMENT,
            GameAbortReasonIds.MISSING_DRAW_SETTLEMENT to MinecraftMessageKeys.GAME_ABORT_REASON_SETTLEMENT,
            GameAbortReasonIds.INVALID_PROGRESSION to MinecraftMessageKeys.GAME_ABORT_REASON_PROGRESSION,
            GameAbortReasonIds.AUTOMATIC_DECISION_FAILED to MinecraftMessageKeys.GAME_ABORT_REASON_AUTOMATIC,
            GameAbortReasonIds.RULE_EXECUTION_FAILED to MinecraftMessageKeys.GAME_ABORT_REASON_RULE,
        )
        expected.forEach { (id, key) -> assertEquals(key, minecraftAbortReasonTranslationKey(id)) }
    }

    /** 玩家回饋模型只攜帶公開原因，不攜帶管理診斷。 */
    @Test
    fun `abort feedback does not expose diagnostic`() {
        val feedback = MinecraftPlayerFeedback.MatchAborted(MinecraftMatchAbortReasonIds.TABLE_MISSING)

        assertEquals(MinecraftMatchAbortReasonIds.TABLE_MISSING, feedback.reasonId)
    }
}
