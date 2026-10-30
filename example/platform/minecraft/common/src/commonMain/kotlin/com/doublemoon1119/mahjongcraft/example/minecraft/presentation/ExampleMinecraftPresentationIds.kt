package com.doublemoon1119.mahjongcraft.example.minecraft.presentation

import com.doublemoon1119.mahjongcraft.example.ExampleExtensionIds

/** 範例 Minecraft 呈現使用的完整識別碼。 */
object ExampleMinecraftPresentationIds {
    /** 規則設定分類。 */
    const val RULE_CATEGORY = "${ExampleExtensionIds.MOD_ID}:pairing/rule"

    /** 換牌開關設定欄位。 */
    const val EXCHANGE_FIELD = "${ExampleExtensionIds.MOD_ID}:pairing/exchange_enabled"

    /** 公開牌面開關設定欄位。 */
    const val PUBLIC_FACES_FIELD = "${ExampleExtensionIds.MOD_ID}:pairing/public_faces_enabled"

    /** 每位玩家付款點數設定欄位。 */
    const val PAYMENT_FIELD = "${ExampleExtensionIds.MOD_ID}:pairing/payment_per_player"

    /** 對局局數設定欄位。 */
    const val ROUND_COUNT_FIELD = "${ExampleExtensionIds.MOD_ID}:pairing/round_count"

    /** 加入牌山的點數設定欄位。 */
    const val WALL_TILE_VALUES_FIELD = "${ExampleExtensionIds.MOD_ID}:pairing/wall_tile_values"

    /** 規則目錄中的簡易配對條目。 */
    const val CATALOGUE_ENTRY = "${ExampleExtensionIds.MOD_ID}:pairing/overview"

    /** 局況資訊行：剩餘牌山。 */
    const val WALL_REMAINING_INFO = "${ExampleExtensionIds.MOD_ID}:pairing/wall_remaining"

    /** 局況資訊行：目前局數。 */
    const val ROUND_COUNT_INFO = "${ExampleExtensionIds.MOD_ID}:pairing/round_count"

    /** 局況資訊行：最近打出的牌。 */
    const val LATEST_DISCARD_INFO = "${ExampleExtensionIds.MOD_ID}:pairing/latest_discard"

    /** 局況資訊行：換牌功能狀態。 */
    const val EXCHANGE_STATUS_INFO = "${ExampleExtensionIds.MOD_ID}:pairing/exchange_status"

    /** 換牌功能已開啟時顯示的規則訊息 ID。 */
    const val EXCHANGE_ENABLED_MESSAGE = "${ExampleExtensionIds.MOD_ID}:pairing/exchange_enabled"

    /** 換牌功能已關閉時顯示的規則訊息 ID。 */
    const val EXCHANGE_DISABLED_MESSAGE = "${ExampleExtensionIds.MOD_ID}:pairing/exchange_disabled"

    /** 結算模板。 */
    const val SETTLEMENT_TEMPLATE = "${ExampleExtensionIds.MOD_ID}:pairing/settlement"

    /** 已攤牌玩家使用的桌面晶片種類。 */
    const val HAND_REVEAL_CHIP_KIND = "${ExampleExtensionIds.MOD_ID}:hand_reveal_chip"

    /** 已攤牌玩家使用的桌面晶片變體。 */
    const val HAND_REVEAL_CHIP_VARIANT = "hand_revealed"

    /** 結算面板加分牌列的標題欄位。 */
    const val BONUS_TILES_LABEL = "mahjongcraft_example:pairing/bonus_tiles_label"
}
