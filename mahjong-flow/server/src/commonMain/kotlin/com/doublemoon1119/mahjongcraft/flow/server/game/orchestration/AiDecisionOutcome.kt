package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

/** 一次有結果的 AI 決策是怎麼得到結果的。 */
enum class AiDecisionOutcome {
    /** 策略在等待上限內給出結果。 */
    DECIDED,

    /** 等待超過上限，使用固定結果。 */
    TIMED_OUT,

    /** 這一局先前已逾時或已取消等待的策略呼叫尚未結束，直接使用固定結果。 */
    PREVIOUS_STILL_RUNNING,
}
