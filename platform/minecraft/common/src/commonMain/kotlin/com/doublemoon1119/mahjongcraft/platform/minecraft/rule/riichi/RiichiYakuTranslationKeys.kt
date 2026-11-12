package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/** 內建日麻役種顯示名稱的 translation key 單一來源，放在 `mahjongcraft.riichi.yaku.` 底下。 */
object RiichiYakuTranslationKeys {
    /** 每個內建役種對應的 translation key。 */
    private val KEYS: Map<YakuType, String> = mapOf(
        YakuType.Dora to "mahjongcraft.riichi.yaku.dora",
        YakuType.UraDora to "mahjongcraft.riichi.yaku.uradora",
        YakuType.AkaDora to "mahjongcraft.riichi.yaku.red_five",
        YakuType.NukiDora to "mahjongcraft.riichi.yaku.nuki_dora",
        YakuType.Tanyao to "mahjongcraft.riichi.yaku.tanyao",
        YakuType.Pinfu to "mahjongcraft.riichi.yaku.pinfu",
        YakuType.Iipeikou to "mahjongcraft.riichi.yaku.ipeiko",
        YakuType.Riichi to "mahjongcraft.riichi.yaku.reach",
        YakuType.DoubleRiichi to "mahjongcraft.riichi.yaku.double_reach",
        YakuType.Ippatsu to "mahjongcraft.riichi.yaku.ippatsu",
        YakuType.RinshanKaihou to "mahjongcraft.riichi.yaku.rinshankaihoh",
        YakuType.Haitei to "mahjongcraft.riichi.yaku.haitei",
        YakuType.Houtei to "mahjongcraft.riichi.yaku.houtei",
        YakuType.Chankan to "mahjongcraft.riichi.yaku.chankan",
        YakuType.Menzentsumo to "mahjongcraft.riichi.yaku.tsumo",
        YakuType.Toitoi to "mahjongcraft.riichi.yaku.toitoiho",
        YakuType.Sanankou to "mahjongcraft.riichi.yaku.sananko",
        YakuType.Sankantsu to "mahjongcraft.riichi.yaku.sankantsu",
        YakuType.SanshokuDokoku to "mahjongcraft.riichi.yaku.sanshokudohko",
        YakuType.SanshokuDoujun to "mahjongcraft.riichi.yaku.sanshokudohjun",
        YakuType.Honchan to "mahjongcraft.riichi.yaku.chanta",
        YakuType.Junchan to "mahjongcraft.riichi.yaku.junchan",
        YakuType.Honitsu to "mahjongcraft.riichi.yaku.honitsu",
        YakuType.Ryanpeikou to "mahjongcraft.riichi.yaku.ryanpeiko",
        YakuType.Ittuitsu to "mahjongcraft.riichi.yaku.ikkitsukan",
        YakuType.Honroutou to "mahjongcraft.riichi.yaku.honrohtoh",
        YakuType.Chinitsu to "mahjongcraft.riichi.yaku.chinitsu",
        YakuType.Shousangen to "mahjongcraft.riichi.yaku.shosangen",
        YakuType.Chiitoitsu to "mahjongcraft.riichi.yaku.chitoitsu",
        YakuType.RoundWind to "mahjongcraft.riichi.yaku.bakaze",
        YakuType.SeatWind to "mahjongcraft.riichi.yaku.jikaze",
        YakuType.WhiteDragon to "mahjongcraft.riichi.yaku.haku",
        YakuType.GreenDragon to "mahjongcraft.riichi.yaku.hatsu",
        YakuType.RedDragon to "mahjongcraft.riichi.yaku.chun",
        YakuType.KokushiMusou to "mahjongcraft.riichi.yaku.kokushimuso",
        YakuType.ChurenPoto to "mahjongcraft.riichi.yaku.churenpohto",
        YakuType.Tsuuiisou to "mahjongcraft.riichi.yaku.tsuiso",
        YakuType.Ryuuuiisou to "mahjongcraft.riichi.yaku.ryuiso",
        YakuType.Suuankou to "mahjongcraft.riichi.yaku.suanko",
        YakuType.Sukantsu to "mahjongcraft.riichi.yaku.sukantsu",
        YakuType.Shousuushi to "mahjongcraft.riichi.yaku.shosushi",
        YakuType.Daisangen to "mahjongcraft.riichi.yaku.daisangen",
        YakuType.Chinroutou to "mahjongcraft.riichi.yaku.chinroto",
        YakuType.Tenhou to "mahjongcraft.riichi.yaku.tenho",
        YakuType.Chiihou to "mahjongcraft.riichi.yaku.chiho",
        YakuType.KokushiMusou13 to "mahjongcraft.riichi.yaku.kokushimuso_jusanmenmachi",
        YakuType.ChurenPoto9 to "mahjongcraft.riichi.yaku.junsei_churenpohto",
        YakuType.SuuankouTanki to "mahjongcraft.riichi.yaku.suanko_tanki",
        YakuType.Daisuushii to "mahjongcraft.riichi.yaku.daisushi",
        YakuType.TsubameGaeshi to "mahjongcraft.riichi.yaku.tsubame_gaeshi",
        YakuType.Kanburi to "mahjongcraft.riichi.yaku.kanburi",
        YakuType.ShiiaruRaotai to "mahjongcraft.riichi.yaku.shiiaru_raotai",
        YakuType.UumenChii to "mahjongcraft.riichi.yaku.uumen_chii",
        YakuType.Sanrenkou to "mahjongcraft.riichi.yaku.sanrenkou",
        YakuType.IsshokuSanjun to "mahjongcraft.riichi.yaku.isshoku_sanjun",
        YakuType.IipinMoyue to "mahjongcraft.riichi.yaku.iipin_moyue",
        YakuType.ChuupinRaoyui to "mahjongcraft.riichi.yaku.chuupin_raoyui",
        YakuType.Renhou to "mahjongcraft.riichi.yaku.renhou",
        YakuType.Daisharin to "mahjongcraft.riichi.yaku.daisharin",
        YakuType.Daichikurin to "mahjongcraft.riichi.yaku.daichikurin",
        YakuType.Daisuurin to "mahjongcraft.riichi.yaku.daisuurin",
        YakuType.IshinoUeSannen to "mahjongcraft.riichi.yaku.ishi_no_ue_ni_mo_sannen",
        YakuType.Daichisei to "mahjongcraft.riichi.yaku.daichisei",
    )

    /** 取得指定役種的完整 translation key。 */
    fun keyFor(type: YakuType): String = KEYS.getValue(type)

    /** 流局滿貫的役種名稱 key；流局滿貫不經過一般役種偵測流程，不屬於 [YakuType]。 */
    const val NAGASHI_MANGAN: String = "mahjongcraft.riichi.yaku.nagashi_mangan"

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
