package com.doublemoon1119.mahjongcraft.platform.minecraft.action

/** 所有規則共用的核心動作識別碼，與規則擴充動作使用同一套命名空間慣例。 */
object BuiltInGameActionIds {
    /** 吃。 */
    const val CHI: String = "mahjongcraft:chi"

    /** 碰。 */
    const val PON: String = "mahjongcraft:pon"

    /** 明槓。 */
    const val KAN_OPEN: String = "mahjongcraft:kan_open"

    /** 暗槓。 */
    const val KAN_CLOSED: String = "mahjongcraft:kan_closed"

    /** 加槓。 */
    const val KAN_ADDED: String = "mahjongcraft:kan_added"

    /** 榮和。 */
    const val RON: String = "mahjongcraft:ron"

    /** 自摸。 */
    const val TSUMO: String = "mahjongcraft:tsumo"

    /** 跳過。 */
    const val PASS: String = "mahjongcraft:pass"

    /** 捨牌。 */
    const val DISCARD: String = "mahjongcraft:discard"

    /** 不屬於上列任何一種的動作。 */
    const val UNKNOWN: String = "mahjongcraft:action"
}
