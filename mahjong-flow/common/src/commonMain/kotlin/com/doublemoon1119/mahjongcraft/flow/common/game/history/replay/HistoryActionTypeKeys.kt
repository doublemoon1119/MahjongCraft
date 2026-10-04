package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/** 歷史動作的種類識別碼；與規則的 namespaced 動作 ID 分開管理。 */
object HistoryActionTypeKeys {
    /** 對局開始的種類識別碼。 */
    const val GAME_STARTED: String = "game_started"

    /** 單局開始的種類識別碼。 */
    const val ROUND_STARTED: String = "round_started"

    /** 對局結束的種類識別碼。 */
    const val MATCH_ENDED: String = "match_ended"

    /** 擲骰的種類識別碼。 */
    const val DICE_ROLLED: String = "dice_rolled"

    /** 摸牌的種類識別碼。 */
    const val DRAW: String = "draw"

    /** 捨牌的種類識別碼。 */
    const val DISCARD: String = "discard"

    /** 吃牌的種類識別碼。 */
    const val CHI: String = "chi"

    /** 碰牌的種類識別碼。 */
    const val PON: String = "pon"

    /** 槓牌的種類識別碼；槓的種類由獨立欄位識別。 */
    const val KAN: String = "kan"

    /** 榮和的種類識別碼。 */
    const val RON: String = "ron"

    /** 自摸的種類識別碼。 */
    const val TSUMO: String = "tsumo"

    /** 擴充動作的種類識別碼；實際動作 ID 由獨立欄位識別。 */
    const val EXTENSION: String = "extension"

    /** 略過反應的種類識別碼。 */
    const val PASS: String = "pass"

    /** 流局的種類識別碼。 */
    const val EXHAUSTIVE_DRAW: String = "exhaustive_draw"
}
