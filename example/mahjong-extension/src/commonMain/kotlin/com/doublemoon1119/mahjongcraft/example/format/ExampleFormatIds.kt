package com.doublemoon1119.mahjongcraft.example.format

/** 範例資料在網路與保存格式中的完整型別識別碼。 */
object ExampleFormatIds {
    /** 簡易配對牌山耗盡的原因。 */
    const val EXHAUSTIVE_DRAW = "mahjongcraft_example:pairing/exhaustive_draw"

    /** 簡易配對規則設定。 */
    const val RULE_CONFIG = "mahjongcraft_example:pairing/rule_config"

    /** 固定計分設定。 */
    const val SCORE_CONFIG = "mahjongcraft_example:pairing/score_config"

    /** 固定局數。 */
    const val GAME_LENGTH = "mahjongcraft_example:pairing/game_length"

    /** 玩家已宣告標記。 */
    const val PLAYER_STATE = "mahjongcraft_example:pairing/player_state"

    /** 依打出順序保存的牌河。 */
    const val DISCARD_PILE = "mahjongcraft_example:pairing/discard_pile"

    /** 已成立的示範宣告。 */
    const val DECLARATION_ACTION = "mahjongcraft_example:pairing/declaration_action"

    /** 玩家送出的示範宣告命令。 */
    const val DECLARATION_COMMAND = "mahjongcraft_example:pairing/declaration_command"
}
