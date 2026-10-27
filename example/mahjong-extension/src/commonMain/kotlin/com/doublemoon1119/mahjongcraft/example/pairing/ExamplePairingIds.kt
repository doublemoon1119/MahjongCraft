package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.example.ExampleExtensionIds
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/** 簡易配對麻將使用的固定識別碼。 */
object ExamplePairingIds {
    /** 簡易配對麻將規則模組識別碼。 */
    const val RULE_MODULE = "${ExampleExtensionIds.MOD_ID}:pairing"

    /** 一萬倒置牌的牌種識別碼。 */
    val INVERTED_ONE_MAN = TileTypeId(ExampleExtensionIds.MOD_ID, "inverted_one_man")

    /** 玩家已完成示範宣告的公開狀態識別碼。 */
    const val DECLARATION_INDICATOR = "${ExampleExtensionIds.MOD_ID}:pairing/declaration"

    /** 簡易配對麻將示範宣告動作的識別碼。 */
    const val DECLARE_ACTION = "${ExampleExtensionIds.MOD_ID}:pairing/declare"

    /** 一般牌山耗盡流局的原因。 */
    const val EXHAUSTIVE_DRAW = "${ExampleExtensionIds.MOD_ID}:pairing/exhaustive_draw"

    /** 局前選兩張牌交給下一位玩家的選擇步驟。 */
    const val EXCHANGE_STEP = "${ExampleExtensionIds.MOD_ID}:pairing/exchange"

    /** 可選加入牌山的八種牌面點數。 */
    val WALL_TILE_VALUE_OPTIONS: List<Pair<Int, String>> = (1..8).map { it to tileValue(it) }

    /**
     * 取得可加入簡易配對牌山的牌面點數識別碼。
     *
     * @param value 一萬至八萬的點數。
     * @return 範例規則持有的完整選項 ID。
     */
    fun tileValue(value: Int): String {
        require(value in 1..8) { "Pairing tile option value must be between 1 and 8" }
        return "${ExampleExtensionIds.MOD_ID}:pairing/tile_$value"
    }
}
