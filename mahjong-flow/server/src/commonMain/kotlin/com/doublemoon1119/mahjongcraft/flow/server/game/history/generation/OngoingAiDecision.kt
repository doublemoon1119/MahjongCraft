package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import kotlin.time.TimeMark

/**
 * 一次進行中的 AI 出牌決策。
 *
 * @property context 決策情境。
 * @property startedAt 開始的時刻。
 */
class OngoingAiDecision(val context: AiDecisionContext, val startedAt: TimeMark)
