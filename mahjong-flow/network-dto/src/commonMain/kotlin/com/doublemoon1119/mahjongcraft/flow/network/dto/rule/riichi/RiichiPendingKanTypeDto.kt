package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import kotlinx.serialization.Serializable

/** 等待公開項目保存的來源槓牌種類。 */
@Serializable
enum class RiichiPendingKanTypeDto {
    /** 大明槓。 */
    OPEN_KAN,

    /** 暗槓。 */
    CLOSED_KAN,

    /** 加槓。 */
    ADDED_KAN,
}
