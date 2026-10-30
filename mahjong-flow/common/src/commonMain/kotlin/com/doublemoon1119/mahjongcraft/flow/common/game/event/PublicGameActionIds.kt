package com.doublemoon1119.mahjongcraft.flow.common.game.event

/** 共用動作在公開事件中的完整識別碼；不作為保存格式的種類鍵。 */
object PublicGameActionIds {
    /** 捨牌。 */
    const val DISCARD = "mahjongcraft:discard"

    /** 吃。 */
    const val CHI = "mahjongcraft:chi"

    /** 碰。 */
    const val PON = "mahjongcraft:pon"

    /** 明槓。 */
    const val KAN_OPEN = "mahjongcraft:kan_open"

    /** 暗槓。 */
    const val KAN_CLOSED = "mahjongcraft:kan_closed"

    /** 加槓。 */
    const val KAN_ADDED = "mahjongcraft:kan_added"

    /** 自摸。 */
    const val TSUMO = "mahjongcraft:tsumo"

    /** 榮和。 */
    const val RON = "mahjongcraft:ron"

    /** 成立動作之後的補牌。 */
    const val SUPPLEMENTAL_DRAW = "mahjongcraft:supplemental_draw"
}
