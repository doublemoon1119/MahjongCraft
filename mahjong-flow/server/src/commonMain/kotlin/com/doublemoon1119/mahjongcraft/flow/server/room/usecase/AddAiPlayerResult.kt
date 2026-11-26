package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import kotlin.uuid.Uuid

/**
 * [AddAiPlayerUseCase] 成功新增 AI 玩家後的結果。
 *
 * @property aiId 新產生的 AI 玩家 Uuid。
 * @property strategyKey 該 AI 實際使用的策略登記 key（已套用預設值解析，不會是 `null`）。
 */
data class AddAiPlayerResult(
    val aiId: Uuid,
    val strategyKey: String,
)
