package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/** 日本立直麻將（含三人）專屬文字的 translation key，統一放在 `mahjongcraft.riichi.` 底下。 */
object RiichiTranslationKeys {
    /** 日麻專屬文字的共用前綴。 */
    private const val PREFIX = MinecraftModMetadata.MOD_ID + ".riichi."

    /** 立直的動作名稱。 */
    const val ACTION_RIICHI: String = PREFIX + "action.riichi"

    /** 立直的操作說明。 */
    const val ACTION_RIICHI_DESCRIPTION: String = PREFIX + "action.riichi.description"

    /** 九種九牌的動作名稱，也是九種九牌流局的名稱。 */
    const val ACTION_KYUUSHU_KYUUHAI: String = PREFIX + "action.kyuushu_kyuuhai"

    /** 九種九牌的操作說明。 */
    const val ACTION_KYUUSHU_KYUUHAI_DESCRIPTION: String = PREFIX + "action.kyuushu_kyuuhai.description"

    /** 三人日麻拔北的動作名稱。 */
    const val ACTION_PULL_NORTH: String = PREFIX + "action.pull_north"

    /** 三人日麻拔北的操作說明。 */
    const val ACTION_PULL_NORTH_DESCRIPTION: String = PREFIX + "action.pull_north.description"

    /** 四家立直流局的名稱。 */
    const val EXHAUSTIVE_DRAW_SUUCHA_RIICHI: String = PREFIX + "exhaustive_draw.suucha_riichi"

    /** 三家和流局的名稱。 */
    const val EXHAUSTIVE_DRAW_SANCHA_HOU: String = PREFIX + "exhaustive_draw.sancha_hou"

    /** 四風連打流局的名稱。 */
    const val EXHAUSTIVE_DRAW_SUUFON_RENDA: String = PREFIX + "exhaustive_draw.suufon_renda"

    /** 四槓散了流局的名稱。 */
    const val EXHAUSTIVE_DRAW_SUUKAN_NAGARE: String = PREFIX + "exhaustive_draw.suukan_nagare"

    /** 荒牌流局時聽牌玩家的結算身分。 */
    const val EXHAUSTIVE_DRAW_TENPAI: String = PREFIX + "exhaustive_draw.tenpai"

    /** 荒牌流局時未聽玩家的結算身分。 */
    const val EXHAUSTIVE_DRAW_NOTEN: String = PREFIX + "exhaustive_draw.noten"

    /** 玩家已立直的標示。 */
    const val INDICATOR_RIICHI: String = PREFIX + "indicator.riichi"

    /** 捨牌振聽的狀態名稱。 */
    const val HUD_FURITEN_DISCARD: String = PREFIX + "hud.furiten.discard"

    /** 同巡振聽的狀態名稱。 */
    const val HUD_FURITEN_TEMPORARY: String = PREFIX + "hud.furiten.temporary"

    /** 立直後永久振聽的狀態名稱。 */
    const val HUD_FURITEN_PERMANENT: String = PREFIX + "hud.furiten.permanent"

    /** 只能自摸和牌的狀態名稱。 */
    const val HUD_WIN_TSUMO_ONLY: String = PREFIX + "hud.win_availability.tsumo_only"

    /** 無役不能和牌的狀態名稱。 */
    const val HUD_WIN_NO_YAKU: String = PREFIX + "hud.win_availability.no_yaku"

    /** 未達起胡翻數的狀態名稱。 */
    const val HUD_WIN_BELOW_MINIMUM: String = PREFIX + "hud.win_availability.below_minimum"

    /** 赤五萬的牌名。 */
    const val TILE_RED_FIVE_CHARACTER: String = PREFIX + "tile.red_five_character"

    /** 赤五筒的牌名。 */
    const val TILE_RED_FIVE_DOT: String = PREFIX + "tile.red_five_dot"

    /** 赤五條的牌名。 */
    const val TILE_RED_FIVE_BAMBOO: String = PREFIX + "tile.red_five_bamboo"

    /** 日本立直麻將的規則名稱。 */
    const val RULE_NAME: String = PREFIX + "rule_name"

    /** 三人日本立直麻將的規則名稱。 */
    const val RULE_NAME_THREE_PLAYER: String = PREFIX + "rule_name.three_player"

    /** 流局滿貫的本局結果名稱。 */
    const val OUTCOME_NAGASHI_MANGAN: String = PREFIX + "outcome.nagashi_mangan"

    /** 牌譜上三人日麻北牌區的標題。 */
    const val HISTORY_TILE_AREA_NORTH: String = PREFIX + "history_screen.tile_area.north"

    /** 局況資訊的標題行，帶場風、局數與本場數。 */
    const val ROUND_INFO_TITLE: String = PREFIX + "round_info.title"

    /** 局況資訊的供託行，帶供託點數。 */
    const val ROUND_INFO_STICK_POT: String = PREFIX + "round_info.stick_pot"

    /** 結算面板寶牌指示牌列的標題。 */
    const val SETTLEMENT_DORA: String = PREFIX + "settlement.dora"

    /** 結算面板裏寶牌指示牌列的標題。 */
    const val SETTLEMENT_URA_DORA: String = PREFIX + "settlement.ura_dora"

    /** 結算面板的寶牌指示牌標題。 */
    const val SETTLEMENT_DORA_INDICATOR: String = PREFIX + "settlement.dora_indicator"

    /** 結算面板的裏寶牌指示牌標題。 */
    const val SETTLEMENT_URA_DORA_INDICATOR: String = PREFIX + "settlement.ura_dora_indicator"

    /** 結算面板的翻數。 */
    const val SETTLEMENT_HAN: String = PREFIX + "settlement.han"

    /** 結算面板的翻符。 */
    const val SETTLEMENT_HAN_FU: String = PREFIX + "settlement.han_fu"

    /** 結算面板的流局滿貫標題。 */
    const val SETTLEMENT_NAGASHI_MANGAN: String = PREFIX + "settlement.nagashi_mangan"

    /** 結算面板役滿倍數文字的前綴，後接倍數，例如 `yakuman_2x`；超過有專屬名稱的倍數時接 `nx`。 */
    const val SETTLEMENT_YAKUMAN_PREFIX: String = PREFIX + "settlement.yakuman_"

    /** 本物件列出的全部固定 key，語系檔必須提供；役滿倍數文字另由 [SETTLEMENT_YAKUMAN_PREFIX] 組成。 */
    val ALL: Set<String> = setOf(
        ACTION_RIICHI,
        ACTION_RIICHI_DESCRIPTION,
        ACTION_KYUUSHU_KYUUHAI,
        ACTION_KYUUSHU_KYUUHAI_DESCRIPTION,
        ACTION_PULL_NORTH,
        ACTION_PULL_NORTH_DESCRIPTION,
        EXHAUSTIVE_DRAW_SUUCHA_RIICHI,
        EXHAUSTIVE_DRAW_SANCHA_HOU,
        EXHAUSTIVE_DRAW_SUUFON_RENDA,
        EXHAUSTIVE_DRAW_SUUKAN_NAGARE,
        EXHAUSTIVE_DRAW_TENPAI,
        EXHAUSTIVE_DRAW_NOTEN,
        INDICATOR_RIICHI,
        HUD_FURITEN_DISCARD,
        HUD_FURITEN_TEMPORARY,
        HUD_FURITEN_PERMANENT,
        HUD_WIN_TSUMO_ONLY,
        HUD_WIN_NO_YAKU,
        HUD_WIN_BELOW_MINIMUM,
        TILE_RED_FIVE_CHARACTER,
        TILE_RED_FIVE_DOT,
        TILE_RED_FIVE_BAMBOO,
        RULE_NAME,
        RULE_NAME_THREE_PLAYER,
        OUTCOME_NAGASHI_MANGAN,
        HISTORY_TILE_AREA_NORTH,
        ROUND_INFO_TITLE,
        ROUND_INFO_STICK_POT,
        SETTLEMENT_DORA,
        SETTLEMENT_URA_DORA,
        SETTLEMENT_DORA_INDICATOR,
        SETTLEMENT_URA_DORA_INDICATOR,
        SETTLEMENT_HAN,
        SETTLEMENT_HAN_FU,
        SETTLEMENT_NAGASHI_MANGAN,
    )
}
