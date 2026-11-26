package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable

/** 最終決策提交可能得到的權威結果種類。 */
@Serializable
enum class PlayerDecisionSubmissionResultKindDto {
    /** 命令已由對局流程接受。 */
    ACCEPTED,

    /** 提交內容無效或當下無法執行。 */
    REJECTED,

    /** Decision key 或對局已過期。 */
    STALE,
}
