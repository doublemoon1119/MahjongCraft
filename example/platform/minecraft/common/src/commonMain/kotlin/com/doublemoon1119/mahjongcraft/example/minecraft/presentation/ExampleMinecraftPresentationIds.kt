package com.doublemoon1119.mahjongcraft.example.minecraft.presentation

import com.doublemoon1119.mahjongcraft.example.ExampleExtensionIds

/** 範例 Minecraft 呈現使用的完整識別碼。 */
object ExampleMinecraftPresentationIds {
    /** 規則設定分類。 */
    const val RULE_CATEGORY = "${ExampleExtensionIds.MOD_ID}:pairing/rule"

    /** 換牌開關設定欄位。 */
    const val EXCHANGE_FIELD = "${ExampleExtensionIds.MOD_ID}:pairing/exchange_enabled"

    /** 每位玩家付款點數設定欄位。 */
    const val PAYMENT_FIELD = "${ExampleExtensionIds.MOD_ID}:pairing/payment_per_player"

    /** 對局局數設定欄位。 */
    const val ROUND_COUNT_FIELD = "${ExampleExtensionIds.MOD_ID}:pairing/round_count"

    /** 規則目錄中的簡易配對條目。 */
    const val CATALOGUE_ENTRY = "${ExampleExtensionIds.MOD_ID}:pairing/overview"

    /** 局況資訊行：剩餘牌山。 */
    const val WALL_REMAINING_INFO = "${ExampleExtensionIds.MOD_ID}:pairing/wall_remaining"

    /** 局況資訊行：目前局數。 */
    const val ROUND_COUNT_INFO = "${ExampleExtensionIds.MOD_ID}:pairing/round_count"

    /** 結算模板。 */
    const val SETTLEMENT_TEMPLATE = "${ExampleExtensionIds.MOD_ID}:pairing/settlement"
}
