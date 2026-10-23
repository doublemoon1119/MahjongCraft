package com.doublemoon1119.mahjongcraft.extension

/** Core extension registry 診斷分類的穩定識別字串。 */
internal object CoreExtensionRegistrationCategoryIds {
    /** 規則模組分類。 */
    const val RULE_MODULE: String = "mahjongcraft:rule_module"

    /** 牌種類型分類。 */
    const val TILE_TYPE: String = "mahjongcraft:tile_type"

    /** 網路 DTO 分類。 */
    const val NETWORK_DTO: String = "mahjongcraft:network_dto"

    /** 持久化 DTO 分類。 */
    const val PERSISTENCE_DTO: String = "mahjongcraft:persistence_dto"

    /** 胡牌展示提示解析器分類。 */
    const val WIN_CELEBRATION_CUE_RESOLVER: String = "mahjongcraft:win_celebration_cue_resolver"

    /** 動作 AI 分類。 */
    const val GAME_ACTION_AI: String = "mahjongcraft:game_action_ai"

    /** AI 策略分類。 */
    const val AI_STRATEGY: String = "mahjongcraft:ai_strategy"

    /** 對手模型分類。 */
    const val OPPONENT_MODEL: String = "mahjongcraft:opponent_model"

    /** 動作命令 factory 分類。 */
    const val GAME_ACTION_COMMAND_FACTORY: String = "mahjongcraft:game_action_command_factory"

    /** 遊戲命令分類。 */
    const val GAME_COMMAND: String = "mahjongcraft:game_command"

    /** 反應階段局結果解析器分類。 */
    const val POST_REACTION_ROUND_OUTCOME_RESOLVER: String = "mahjongcraft:post_reaction_round_outcome_resolver"

    /** 動作完成後途中流局解析器分類。 */
    const val POST_ACTION_EXHAUSTIVE_DRAW_RESOLVER: String = "mahjongcraft:post_action_exhaustive_draw_resolver"

    /** 規則選擇解析器分類。 */
    const val RULE_DECISION_RESOLVER: String = "mahjongcraft:rule_decision_resolver"

    /** 胡牌後續流程解析器分類。 */
    const val WIN_ROUND_CONTINUATION_RESOLVER: String = "mahjongcraft:win_round_continuation_resolver"

    /** 胡牌結算詳情解析器分類。 */
    const val WIN_SETTLEMENT_DETAIL_RESOLVER: String = "mahjongcraft:win_settlement_detail_resolver"
}
