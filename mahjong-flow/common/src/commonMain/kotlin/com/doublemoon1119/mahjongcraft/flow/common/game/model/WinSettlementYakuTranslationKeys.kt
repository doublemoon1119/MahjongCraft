package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 內建日麻役種顯示名稱的 translation key 單一來源。 */
object WinSettlementYakuTranslationKeys {
    /** 所有 MahjongCraft 役種 translation key 的共用前綴。 */
    private const val PREFIX = MahjongCraftMetadata.PROJECT_ID + ".game.yaku."

    /** 每個內建役種對應的 key 尾段。 */
    private val SUFFIXES: Map<YakuType, String> = mapOf(
        YakuType.Dora to "dora",
        YakuType.UraDora to "uradora",
        YakuType.AkaDora to "red_five",
        YakuType.Tanyao to "tanyao",
        YakuType.Pinfu to "pinfu",
        YakuType.Iipeikou to "ipeiko",
        YakuType.Riichi to "reach",
        YakuType.DoubleRiichi to "double_reach",
        YakuType.Ippatsu to "ippatsu",
        YakuType.RinshanKaihou to "rinshankaihoh",
        YakuType.Haitei to "haitei",
        YakuType.Houtei to "houtei",
        YakuType.Chankan to "chankan",
        YakuType.Menzentsumo to "tsumo",
        YakuType.Toitoi to "toitoiho",
        YakuType.Sanankou to "sananko",
        YakuType.Sankantsu to "sankantsu",
        YakuType.SanshokuDokoku to "sanshokudohko",
        YakuType.SanshokuDoujun to "sanshokudohjun",
        YakuType.Honchan to "chanta",
        YakuType.Junchan to "junchan",
        YakuType.Honitsu to "honitsu",
        YakuType.Ryanpeikou to "ryanpeiko",
        YakuType.Ittuitsu to "ikkitsukan",
        YakuType.Honroutou to "honrohtoh",
        YakuType.Chinitsu to "chinitsu",
        YakuType.Shousangen to "shosangen",
        YakuType.Chiitoitsu to "chitoitsu",
        YakuType.RoundWind to "bakaze",
        YakuType.SeatWind to "jikaze",
        YakuType.Dragon to "chun",
        YakuType.KokushiMusou to "kokushimuso",
        YakuType.ChurenPoto to "churenpohto",
        YakuType.Tsuuiisou to "tsuiso",
        YakuType.Ryuuuiisou to "ryuiso",
        YakuType.Suuankou to "suanko",
        YakuType.Sukantsu to "sukantsu",
        YakuType.Shousuushi to "shosushi",
        YakuType.Daisangen to "daisangen",
        YakuType.Chinroutou to "chinroto",
        YakuType.Tenhou to "tenho",
        YakuType.Chiihou to "chiho",
        YakuType.KokushiMusou13 to "kokushimuso_jusanmenmachi",
        YakuType.ChurenPoto9 to "junsei_churenpohto",
        YakuType.SuuankouTanki to "suanko_tanki",
        YakuType.Daisuushii to "daisushi",
    )

    /** 取得指定役種的完整 translation key。 */
    fun keyFor(type: YakuType): String = PREFIX + SUFFIXES.getValue(type)

    /** 流局滿貫結算面板役種條目使用的 key；流局滿貫不經過一般役種偵測流程，不屬於 [YakuType]。 */
    const val NAGASHI_MANGAN = PREFIX + "nagashi_mangan"

    /** Minecraft 語系資源必須提供的全部役種 translation key。 */
    val ALL: Set<String> = YakuType.entries.mapTo(mutableSetOf(), ::keyFor) + NAGASHI_MANGAN
}
