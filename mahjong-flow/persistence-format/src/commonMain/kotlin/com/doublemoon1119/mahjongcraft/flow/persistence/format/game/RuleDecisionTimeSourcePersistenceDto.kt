package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/** 規則選擇步驟思考時間來源的持久化值。 */
@Serializable
enum class RuleDecisionTimeSourcePersistenceDto {
    /** 使用規則選擇專用時間。 */
    RULE_DECISION_TIME,

    /** 使用一般動作時間與共用保留時間。 */
    ACTION_TIME,
}
