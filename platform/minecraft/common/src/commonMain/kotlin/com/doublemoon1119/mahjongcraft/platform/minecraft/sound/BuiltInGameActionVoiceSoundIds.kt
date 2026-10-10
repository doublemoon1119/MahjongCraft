package com.doublemoon1119.mahjongcraft.platform.minecraft.sound

/** MahjongCraft 內建日本麻將宣告語音的 Minecraft sound ID。 */
object BuiltInGameActionVoiceSoundIds {
    /** 吃牌語音。 */
    const val CHII: String = "mahjongcraft:voice.chii"

    /** 碰牌語音。 */
    const val PON: String = "mahjongcraft:voice.pon"

    /** 槓牌語音。 */
    const val KAN: String = "mahjongcraft:voice.kan"

    /** 立直語音。 */
    const val RIICHI: String = "mahjongcraft:voice.riichi"

    /** 榮和語音。 */
    const val RON: String = "mahjongcraft:voice.ron"

    /** 自摸語音。 */
    const val TSUMO: String = "mahjongcraft:voice.tsumo"

    /** 全部內建宣告語音 ID。 */
    val ALL: List<String> = listOf(CHII, PON, KAN, RIICHI, RON, TSUMO)
}
