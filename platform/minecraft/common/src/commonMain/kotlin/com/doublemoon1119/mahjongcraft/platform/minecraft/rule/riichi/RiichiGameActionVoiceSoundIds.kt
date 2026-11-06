package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

/** 日本麻將宣告語音的 Minecraft sound ID；三人日麻沿用同一組。 */
object RiichiGameActionVoiceSoundIds {
    /** 吃牌語音。 */
    const val CHII: String = "mahjongcraft:voice.riichi.chii"

    /** 碰牌語音。 */
    const val PON: String = "mahjongcraft:voice.riichi.pon"

    /** 槓牌語音。 */
    const val KAN: String = "mahjongcraft:voice.riichi.kan"

    /** 立直語音。 */
    const val RIICHI: String = "mahjongcraft:voice.riichi.riichi"

    /** 榮和語音。 */
    const val RON: String = "mahjongcraft:voice.riichi.ron"

    /** 自摸語音。 */
    const val TSUMO: String = "mahjongcraft:voice.riichi.tsumo"

    /** 全部日本麻將宣告語音 ID。 */
    val ALL: List<String> = listOf(CHII, PON, KAN, RIICHI, RON, TSUMO)
}
