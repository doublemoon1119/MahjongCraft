package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/** 內建日麻役種顯示名稱的 translation key 單一來源。 */
object RiichiYakuTranslationKeys {
    /** 所有 MahjongCraft 役種 translation key 的共用前綴。 */
    private const val PREFIX = MinecraftModMetadata.MOD_ID + ".game.yaku."

    /** 每個內建役種對應的 key 尾段。 */
    private val SUFFIXES: Map<YakuType, String> = mapOf(
        YakuType.Dora to "dora",
        YakuType.UraDora to "uradora",
        YakuType.AkaDora to "red_five",
        YakuType.NukiDora to "nuki_dora",
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
        YakuType.TsubameGaeshi to "tsubame_gaeshi",
        YakuType.Kanburi to "kanburi",
        YakuType.ShiiaruRaotai to "shiiaru_raotai",
        YakuType.UumenChii to "uumen_chii",
        YakuType.Sanrenkou to "sanrenkou",
        YakuType.IsshokuSanjun to "isshoku_sanjun",
        YakuType.IipinMoyue to "iipin_moyue",
        YakuType.ChuupinRaoyui to "chuupin_raoyui",
        YakuType.Renhou to "renhou",
        YakuType.Daisharin to "daisharin",
        YakuType.Daichikurin to "daichikurin",
        YakuType.Daisuurin to "daisuurin",
        YakuType.IshinoUeSannen to "ishi_no_ue_ni_mo_sannen",
        YakuType.Daichisei to "daichisei",
    )

    /** 取得指定役種的完整 translation key。 */
    fun keyFor(type: YakuType): String = PREFIX + SUFFIXES.getValue(type)

    /** 流局滿貫的役種名稱 key；流局滿貫不經過一般役種偵測流程，不屬於 [YakuType]。 */
    const val NAGASHI_MANGAN = PREFIX + "nagashi_mangan"

    /**
     * 取得胡牌詳情役種條目的名稱 key。
     *
     * @param entryId [RiichiWinSettlementIds] 定義的役種或流局滿貫條目 ID。
     * @return 名稱 key；不是日麻役種條目時為 null。
     */
    fun keyForEntry(entryId: String): String? = when (entryId) {
        RiichiWinSettlementIds.NAGASHI_MANGAN -> NAGASHI_MANGAN
        else -> RiichiWinSettlementIds.yakuType(entryId)?.let(::keyFor)
    }

    /** 平台語系資源必須提供的全部役種 translation key。 */
    val ALL: Set<String> = YakuType.entries.mapTo(mutableSetOf(), ::keyFor) + NAGASHI_MANGAN
}
