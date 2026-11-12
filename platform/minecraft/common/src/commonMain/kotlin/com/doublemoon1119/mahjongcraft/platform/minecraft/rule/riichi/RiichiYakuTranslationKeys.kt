package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType

/** 內建日麻役種顯示名稱的 translation key 單一來源，放在 `mahjongcraft.riichi.yaku.` 底下。 */
object RiichiYakuTranslationKeys {
    /** 每個內建役種對應的 translation key。 */
    private val KEYS: Map<RiichiYakuType, String> = mapOf(
        RiichiYakuType.Dora to "mahjongcraft.riichi.yaku.dora",
        RiichiYakuType.UraDora to "mahjongcraft.riichi.yaku.uradora",
        RiichiYakuType.AkaDora to "mahjongcraft.riichi.yaku.red_five",
        RiichiYakuType.NukiDora to "mahjongcraft.riichi.yaku.nuki_dora",
        RiichiYakuType.Tanyao to "mahjongcraft.riichi.yaku.tanyao",
        RiichiYakuType.Pinfu to "mahjongcraft.riichi.yaku.pinfu",
        RiichiYakuType.Iipeikou to "mahjongcraft.riichi.yaku.ipeiko",
        RiichiYakuType.Riichi to "mahjongcraft.riichi.yaku.reach",
        RiichiYakuType.DoubleRiichi to "mahjongcraft.riichi.yaku.double_reach",
        RiichiYakuType.Ippatsu to "mahjongcraft.riichi.yaku.ippatsu",
        RiichiYakuType.RinshanKaihou to "mahjongcraft.riichi.yaku.rinshankaihoh",
        RiichiYakuType.Haitei to "mahjongcraft.riichi.yaku.haitei",
        RiichiYakuType.Houtei to "mahjongcraft.riichi.yaku.houtei",
        RiichiYakuType.Chankan to "mahjongcraft.riichi.yaku.chankan",
        RiichiYakuType.Menzentsumo to "mahjongcraft.riichi.yaku.tsumo",
        RiichiYakuType.Toitoi to "mahjongcraft.riichi.yaku.toitoiho",
        RiichiYakuType.Sanankou to "mahjongcraft.riichi.yaku.sananko",
        RiichiYakuType.Sankantsu to "mahjongcraft.riichi.yaku.sankantsu",
        RiichiYakuType.SanshokuDokoku to "mahjongcraft.riichi.yaku.sanshokudohko",
        RiichiYakuType.SanshokuDoujun to "mahjongcraft.riichi.yaku.sanshokudohjun",
        RiichiYakuType.Honchan to "mahjongcraft.riichi.yaku.chanta",
        RiichiYakuType.Junchan to "mahjongcraft.riichi.yaku.junchan",
        RiichiYakuType.Honitsu to "mahjongcraft.riichi.yaku.honitsu",
        RiichiYakuType.Ryanpeikou to "mahjongcraft.riichi.yaku.ryanpeiko",
        RiichiYakuType.Ittuitsu to "mahjongcraft.riichi.yaku.ikkitsukan",
        RiichiYakuType.Honroutou to "mahjongcraft.riichi.yaku.honrohtoh",
        RiichiYakuType.Chinitsu to "mahjongcraft.riichi.yaku.chinitsu",
        RiichiYakuType.Shousangen to "mahjongcraft.riichi.yaku.shosangen",
        RiichiYakuType.Chiitoitsu to "mahjongcraft.riichi.yaku.chitoitsu",
        RiichiYakuType.RoundWind to "mahjongcraft.riichi.yaku.bakaze",
        RiichiYakuType.SeatWind to "mahjongcraft.riichi.yaku.jikaze",
        RiichiYakuType.WhiteDragon to "mahjongcraft.riichi.yaku.haku",
        RiichiYakuType.GreenDragon to "mahjongcraft.riichi.yaku.hatsu",
        RiichiYakuType.RedDragon to "mahjongcraft.riichi.yaku.chun",
        RiichiYakuType.KokushiMusou to "mahjongcraft.riichi.yaku.kokushimuso",
        RiichiYakuType.ChurenPoto to "mahjongcraft.riichi.yaku.churenpohto",
        RiichiYakuType.Tsuuiisou to "mahjongcraft.riichi.yaku.tsuiso",
        RiichiYakuType.Ryuuuiisou to "mahjongcraft.riichi.yaku.ryuiso",
        RiichiYakuType.Suuankou to "mahjongcraft.riichi.yaku.suanko",
        RiichiYakuType.Sukantsu to "mahjongcraft.riichi.yaku.sukantsu",
        RiichiYakuType.Shousuushi to "mahjongcraft.riichi.yaku.shosushi",
        RiichiYakuType.Daisangen to "mahjongcraft.riichi.yaku.daisangen",
        RiichiYakuType.Chinroutou to "mahjongcraft.riichi.yaku.chinroto",
        RiichiYakuType.Tenhou to "mahjongcraft.riichi.yaku.tenho",
        RiichiYakuType.Chiihou to "mahjongcraft.riichi.yaku.chiho",
        RiichiYakuType.KokushiMusou13 to "mahjongcraft.riichi.yaku.kokushimuso_jusanmenmachi",
        RiichiYakuType.ChurenPoto9 to "mahjongcraft.riichi.yaku.junsei_churenpohto",
        RiichiYakuType.SuuankouTanki to "mahjongcraft.riichi.yaku.suanko_tanki",
        RiichiYakuType.Daisuushii to "mahjongcraft.riichi.yaku.daisushi",
        RiichiYakuType.TsubameGaeshi to "mahjongcraft.riichi.yaku.tsubame_gaeshi",
        RiichiYakuType.Kanburi to "mahjongcraft.riichi.yaku.kanburi",
        RiichiYakuType.ShiiaruRaotai to "mahjongcraft.riichi.yaku.shiiaru_raotai",
        RiichiYakuType.UumenChii to "mahjongcraft.riichi.yaku.uumen_chii",
        RiichiYakuType.Sanrenkou to "mahjongcraft.riichi.yaku.sanrenkou",
        RiichiYakuType.IsshokuSanjun to "mahjongcraft.riichi.yaku.isshoku_sanjun",
        RiichiYakuType.IipinMoyue to "mahjongcraft.riichi.yaku.iipin_moyue",
        RiichiYakuType.ChuupinRaoyui to "mahjongcraft.riichi.yaku.chuupin_raoyui",
        RiichiYakuType.Renhou to "mahjongcraft.riichi.yaku.renhou",
        RiichiYakuType.Daisharin to "mahjongcraft.riichi.yaku.daisharin",
        RiichiYakuType.Daichikurin to "mahjongcraft.riichi.yaku.daichikurin",
        RiichiYakuType.Daisuurin to "mahjongcraft.riichi.yaku.daisuurin",
        RiichiYakuType.IshinoUeSannen to "mahjongcraft.riichi.yaku.ishi_no_ue_ni_mo_sannen",
        RiichiYakuType.Daichisei to "mahjongcraft.riichi.yaku.daichisei",
    )

    /** 取得指定役種的完整 translation key。 */
    fun keyFor(type: RiichiYakuType): String = KEYS.getValue(type)

    /** 流局滿貫的役種名稱 key；流局滿貫不經過一般役種偵測流程，不屬於 [RiichiYakuType]。 */
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
    val ALL: Set<String> = RiichiYakuType.entries.mapTo(mutableSetOf(), ::keyFor) + NAGASHI_MANGAN
}
