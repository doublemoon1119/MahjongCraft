package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig

/**
 * 多個 debug command family 共用的預覽基準值。
 *
 * 預覽牌數必須跟隨標準日麻規則的單一設定來源，避免各 family 各自寫死張數後與正式規則脫鉤。
 */
object DebugPreviewDefaults {
    /** 預覽牌數跟隨標準日麻規則的單一設定來源。 */
    val RULE_CONFIG: RiichiRuleConfig = RiichiRuleConfig()

    /** 一組吃或碰的牌數。 */
    const val MELD_TILE_COUNT: Int = 3
}
