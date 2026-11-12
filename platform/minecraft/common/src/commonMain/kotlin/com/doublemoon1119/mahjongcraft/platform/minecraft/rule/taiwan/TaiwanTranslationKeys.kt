package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

/** 台灣麻將專屬文字的 translation key，統一放在 `mahjongcraft.taiwan.` 底下；台種名稱另見 [TaiwanTaiTranslationKeys]。 */
object TaiwanTranslationKeys {
    /** 台灣麻將的規則名稱。 */
    const val RULE_NAME: String = "mahjongcraft.taiwan.rule_name"

    /** 胡牌的操作卡名稱。 */
    const val ACTION_HU: String = "mahjongcraft.taiwan.action.hu"

    /** 胡牌的聊天訊息，帶胡的牌。 */
    const val ACTION_HU_MESSAGE: String = "mahjongcraft.taiwan.action.hu.message"

    /** 公開花牌的動作名稱。 */
    const val ACTION_REVEAL_FLOWER: String = "mahjongcraft.taiwan.action.reveal_flower"

    /** 公開花牌在操作說明中的文字。 */
    const val ACTION_REVEAL_FLOWER_DESCRIPTION: String = "mahjongcraft.taiwan.action.reveal_flower.description"

    /** 宣告聽牌的操作卡名稱。 */
    const val ACTION_DECLARE_READY: String = "mahjongcraft.taiwan.action.declare_ready"

    /** 宣告聽牌在操作說明中的文字。 */
    const val ACTION_DECLARE_READY_DESCRIPTION: String = "mahjongcraft.taiwan.action.declare_ready.description"

    /** 花牌「春」的名稱。 */
    const val TILE_SPRING: String = "mahjongcraft.taiwan.tile.spring"

    /** 花牌「夏」的名稱。 */
    const val TILE_SUMMER: String = "mahjongcraft.taiwan.tile.summer"

    /** 花牌「秋」的名稱。 */
    const val TILE_AUTUMN: String = "mahjongcraft.taiwan.tile.autumn"

    /** 花牌「冬」的名稱。 */
    const val TILE_WINTER: String = "mahjongcraft.taiwan.tile.winter"

    /** 花牌「梅」的名稱。 */
    const val TILE_PLUM: String = "mahjongcraft.taiwan.tile.plum"

    /** 花牌「蘭」的名稱。 */
    const val TILE_ORCHID: String = "mahjongcraft.taiwan.tile.orchid"

    /** 花牌「竹」的名稱。 */
    const val TILE_BAMBOO: String = "mahjongcraft.taiwan.tile.bamboo"

    /** 花牌「菊」的名稱。 */
    const val TILE_CHRYSANTHEMUM: String = "mahjongcraft.taiwan.tile.chrysanthemum"

    /** 局況資訊的圈風與局數行。 */
    const val ROUND_INFO_TITLE: String = "mahjongcraft.taiwan.round_info.title"

    /** 局況資訊的連莊次數行。 */
    const val ROUND_INFO_DEALER_STREAK: String = "mahjongcraft.taiwan.round_info.dealer_streak"

    /** 過水狀態的提示文字。 */
    const val HUD_PASSED_WIN: String = "mahjongcraft.taiwan.hud.passed_win"

    /** 只能自摸的等待牌提示文字。 */
    const val HUD_WIN_TSUMO_ONLY: String = "mahjongcraft.taiwan.hud.win_availability.tsumo_only"

    /** 未達最低台數的等待牌提示文字。 */
    const val HUD_WIN_BELOW_MINIMUM: String = "mahjongcraft.taiwan.hud.win_availability.below_minimum"

    /** 目前不可胡牌的等待牌提示文字。 */
    const val HUD_WIN_NOT_AVAILABLE: String = "mahjongcraft.taiwan.hud.win_availability.not_available"

    /** 一般宣告聽牌的公開標示。 */
    const val INDICATOR_READY: String = "mahjongcraft.taiwan.indicator.ready"

    /** 天聽的公開標示。 */
    const val INDICATOR_HEAVENLY_READY: String = "mahjongcraft.taiwan.indicator.heavenly_ready"

    /** 地聽的公開標示。 */
    const val INDICATOR_EARTHLY_READY: String = "mahjongcraft.taiwan.indicator.earthly_ready"

    /** 連莊標示，帶連莊次數。 */
    const val INDICATOR_DEALER_STREAK: String = "mahjongcraft.taiwan.indicator.dealer_streak"

    /** 八仙過海的本局結果名稱。 */
    const val OUTCOME_EIGHT_FLOWERS: String = "mahjongcraft.taiwan.outcome.eight_flowers"

    /** 七搶一的本局結果名稱。 */
    const val OUTCOME_SEVEN_FLOWERS: String = "mahjongcraft.taiwan.outcome.seven_flowers"

    /** 結算面板花牌列的標題。 */
    const val SETTLEMENT_FLOWERS: String = "mahjongcraft.taiwan.settlement.flowers"

    /** 結算面板的台數，帶台數。 */
    const val SETTLEMENT_TAI_COUNT: String = "mahjongcraft.taiwan.settlement.tai_count"

    /** 閒家自摸時莊家那一份的合計台數，帶台數。 */
    const val SETTLEMENT_DEALER_TAI_TOTAL: String = "mahjongcraft.taiwan.settlement.dealer_tai_total"

    /** 牌譜上花牌區的標題。 */
    const val HISTORY_TILE_AREA_FLOWERS: String = "mahjongcraft.taiwan.history_screen.tile_area.flowers"

    /** 牌譜上補花紀錄的用語。 */
    const val HISTORY_REPLACEMENT: String = "mahjongcraft.taiwan.history_screen.replacement"

    /** 本物件列出的全部 key，語系檔必須提供。 */
    val ALL: Set<String> = setOf(
        RULE_NAME,
        ACTION_HU,
        ACTION_HU_MESSAGE,
        ACTION_REVEAL_FLOWER,
        ACTION_REVEAL_FLOWER_DESCRIPTION,
        ACTION_DECLARE_READY,
        ACTION_DECLARE_READY_DESCRIPTION,
        TILE_SPRING,
        TILE_SUMMER,
        TILE_AUTUMN,
        TILE_WINTER,
        TILE_PLUM,
        TILE_ORCHID,
        TILE_BAMBOO,
        TILE_CHRYSANTHEMUM,
        ROUND_INFO_TITLE,
        ROUND_INFO_DEALER_STREAK,
        HUD_PASSED_WIN,
        HUD_WIN_TSUMO_ONLY,
        HUD_WIN_BELOW_MINIMUM,
        HUD_WIN_NOT_AVAILABLE,
        INDICATOR_READY,
        INDICATOR_HEAVENLY_READY,
        INDICATOR_EARTHLY_READY,
        INDICATOR_DEALER_STREAK,
        OUTCOME_EIGHT_FLOWERS,
        OUTCOME_SEVEN_FLOWERS,
        SETTLEMENT_FLOWERS,
        SETTLEMENT_TAI_COUNT,
        SETTLEMENT_DEALER_TAI_TOTAL,
        HISTORY_TILE_AREA_FLOWERS,
        HISTORY_REPLACEMENT,
    )
}
