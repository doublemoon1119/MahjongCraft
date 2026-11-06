package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

/** 台灣麻將宣告語音的 Minecraft sound ID。 */
object TaiwanGameActionVoiceSoundIds {
    /** 吃牌語音。 */
    const val CHI: String = "mahjongcraft:voice.taiwan.chi"

    /** 碰牌語音。 */
    const val PON: String = "mahjongcraft:voice.taiwan.pon"

    /** 槓牌語音。 */
    const val KAN: String = "mahjongcraft:voice.taiwan.kan"

    /** 胡別人打出的牌的語音。 */
    const val HU: String = "mahjongcraft:voice.taiwan.hu"

    /** 自摸語音。 */
    const val TSUMO: String = "mahjongcraft:voice.taiwan.tsumo"

    /** 全部台灣麻將宣告語音 ID。 */
    val ALL: List<String> = listOf(CHI, PON, KAN, HU, TSUMO)
}
