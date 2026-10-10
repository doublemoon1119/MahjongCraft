package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import kotlin.time.Duration

/**
 * 一次已結束的 AI 出牌決策。
 *
 * @property context 決策情境。
 * @property duration 策略思考的耗時。
 */
data class TimedAiDecision(val context: AiDecisionContext, val duration: Duration)
