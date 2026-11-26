package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable

/**
 * 最終決策提交的伺服器權威處理結果。
 *
 * @property gameId 對局 UUID 字串。
 * @property decisionKey 對應的決策 key。
 * @property submissionId 對應的提交識別碼。
 * @property result 權威處理結果種類。
 */
@Serializable
data class PlayerDecisionSubmissionResultDto(
    val gameId: String,
    val decisionKey: String,
    val submissionId: String,
    val result: PlayerDecisionSubmissionResultKindDto,
)
