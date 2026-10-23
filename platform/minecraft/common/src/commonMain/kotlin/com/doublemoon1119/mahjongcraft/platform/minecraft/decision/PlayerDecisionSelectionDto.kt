package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable

/**
 * 客戶端提交的權威候選 token。
 *
 * @property gameId 對局 UUID 字串。
 * @property decisionKey 目前 prompt 的決策 key。
 * @property kind 提交的操作種類。
 * @property token 動作候選 token；選擇步驟可為 null。
 * @property tileIds 額外選取的實體牌 ID。
 * @property submissionId 客戶端產生的提交識別碼。
 */
@Serializable
data class PlayerDecisionSelectionDto(
    val gameId: String,
    val decisionKey: String,
    val kind: PlayerDecisionSelectionKindDto,
    val token: String?,
    val tileIds: List<String>,
    val submissionId: String,
)
