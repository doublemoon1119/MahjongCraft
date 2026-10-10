package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import kotlinx.serialization.Serializable

/** Persistence 層保存的日麻來源槓牌種類。 */
@Serializable
enum class RiichiPendingKanTypePersistenceDto {
    /** 大明槓。 */
    OPEN_KAN,

    /** 暗槓。 */
    CLOSED_KAN,

    /** 加槓。 */
    ADDED_KAN,
}
