package com.doublemoon1119.mahjongcraft.flow.common.game.history

/** 歷史事實在跨層傳遞時使用的穩定種類識別碼。
 *
 * 這些值同時是歷史讀模型與序列化邊界的契約；讀到未登記的種類時一律拒絕解碼。
 */
object HistoryFactTypeKeys {
    /** 對局建立事實的種類識別碼。 */
    const val MATCH_STARTED: String = "match_started"

    /** 新局建立事實的種類識別碼。 */
    const val ROUND_STARTED: String = "round_started"

    /** 開局準備步驟開始事實的種類識別碼。 */
    const val ROUND_PREPARATION_STARTED: String = "round_preparation_started"

    /** 玩家提交開局準備事實的種類識別碼。 */
    const val ROUND_PREPARATION_SUBMITTED: String = "round_preparation_submitted"

    /** 自動完成開局準備事實的種類識別碼。 */
    const val ROUND_PREPARATION_AUTOMATIC_RESOLVED: String = "round_preparation_automatic_resolved"

    /** 玩家動作接受事實的種類識別碼。 */
    const val ACTION_ACCEPTED: String = "action_accepted"

    /** 多方反應裁定事實的種類識別碼。 */
    const val REACTION_RESOLVED: String = "reaction_resolved"

    /** 本局完成事實的種類識別碼。 */
    const val ROUND_COMPLETED: String = "round_completed"

    /** 和牌結算事實的種類識別碼。 */
    const val WIN_SETTLED: String = "win_settled"

    /** 整場對局完成事實的種類識別碼。 */
    const val MATCH_COMPLETED: String = "match_completed"

    /** 和牌後續流程裁定事實的種類識別碼。 */
    const val WIN_CONTINUATION_RESOLVED: String = "win_continuation_resolved"

    /** 規則效果完成事實的種類識別碼。 */
    const val RULE_EFFECT_RESOLVED: String = "rule_effect_resolved"

    /** 桌況變更結果事實的種類識別碼。 */
    const val TABLE_CHANGED: String = "table_changed"

    /** 對局返回房間事實的種類識別碼。 */
    const val RETURNED_TO_ROOM: String = "returned_to_room"
}
