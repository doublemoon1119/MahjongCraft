package com.doublemoon1119.mahjongcraft.flow.network.dto.command

import kotlinx.serialization.Serializable

/** 槓牌動作所使用的槓牌形式。 */
@Serializable
enum class KanTypeDto {
    /** 從其他玩家取得牌完成的明槓。 */
    OPEN_KAN,

    /** 以手牌完成的暗槓。 */
    CLOSED_KAN,

    /** 將既有碰子加牌升級為槓。 */
    ADDED_KAN,
}
