package com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 內建日麻胡牌詳情使用的欄位、條目與單位 ID。
 *
 * 這些 ID 會保存在對局歷史中；役種 ID 逐一明列，不由 [RiichiYakuType] 的名稱推導，讓 enum 改名不影響已保存的紀錄。
 */
object RiichiWinSettlementIds {
    /** 役種條目欄位；值為 [WinSettlementDetailValue.Entries]。 */
    val YAKU_FIELD: String = MahjongCraftMetadata.id("riichi/yaku_list")

    /** 一般和牌的翻符欄位；值為 [WinSettlementDetailValue.Quantities]，依序為翻與符，沒有權威符數時只有翻。 */
    val HAN_FU_FIELD: String = MahjongCraftMetadata.id("riichi/han_fu")

    /** 自然役滿的合計倍數欄位；值為 [WinSettlementDetailValue.Quantities]，只有役滿倍數。 */
    val YAKUMAN_TOTAL_FIELD: String = MahjongCraftMetadata.id("riichi/yakuman_total")

    /** 寶牌指示牌欄位；值為 [WinSettlementDetailValue.TileFaces]。 */
    val DORA_FIELD: String = MahjongCraftMetadata.id("riichi/dora_indicators")

    /** 裏寶牌指示牌欄位；只在立直和牌出現，值為 [WinSettlementDetailValue.TileFaces]。 */
    val URA_DORA_FIELD: String = MahjongCraftMetadata.id("riichi/ura_dora_indicators")

    /** 翻數單位。 */
    val HAN: String = MahjongCraftMetadata.id("riichi/han")

    /** 符數單位。 */
    val FU: String = MahjongCraftMetadata.id("riichi/fu")

    /** 役滿倍數單位；1 表示役滿，2 表示兩倍役滿。 */
    val YAKUMAN: String = MahjongCraftMetadata.id("riichi/yakuman")

    /** 流局滿貫條目；流局滿貫不經過一般役種判定，不屬於 [RiichiYakuType]。 */
    val NAGASHI_MANGAN: String = yakuId("nagashi_mangan")

    /** 每個役種的條目 ID。 */
    private val YAKU_IDS: Map<RiichiYakuType, String> = mapOf(
        RiichiYakuType.Dora to yakuId("dora"),
        RiichiYakuType.UraDora to yakuId("ura_dora"),
        RiichiYakuType.AkaDora to yakuId("aka_dora"),
        RiichiYakuType.NukiDora to yakuId("nuki_dora"),
        RiichiYakuType.Tanyao to yakuId("tanyao"),
        RiichiYakuType.Pinfu to yakuId("pinfu"),
        RiichiYakuType.Iipeikou to yakuId("iipeikou"),
        RiichiYakuType.Riichi to yakuId("riichi"),
        RiichiYakuType.DoubleRiichi to yakuId("double_riichi"),
        RiichiYakuType.Ippatsu to yakuId("ippatsu"),
        RiichiYakuType.RinshanKaihou to yakuId("rinshan_kaihou"),
        RiichiYakuType.Haitei to yakuId("haitei"),
        RiichiYakuType.Houtei to yakuId("houtei"),
        RiichiYakuType.Chankan to yakuId("chankan"),
        RiichiYakuType.Menzentsumo to yakuId("menzen_tsumo"),
        RiichiYakuType.Toitoi to yakuId("toitoi"),
        RiichiYakuType.Sanankou to yakuId("sanankou"),
        RiichiYakuType.Sankantsu to yakuId("sankantsu"),
        RiichiYakuType.SanshokuDokoku to yakuId("sanshoku_doukou"),
        RiichiYakuType.SanshokuDoujun to yakuId("sanshoku_doujun"),
        RiichiYakuType.Honchan to yakuId("chanta"),
        RiichiYakuType.Junchan to yakuId("junchan"),
        RiichiYakuType.Honitsu to yakuId("honitsu"),
        RiichiYakuType.Ryanpeikou to yakuId("ryanpeikou"),
        RiichiYakuType.Ittuitsu to yakuId("ittsuu"),
        RiichiYakuType.Honroutou to yakuId("honroutou"),
        RiichiYakuType.Chinitsu to yakuId("chinitsu"),
        RiichiYakuType.Shousangen to yakuId("shousangen"),
        RiichiYakuType.Chiitoitsu to yakuId("chiitoitsu"),
        RiichiYakuType.RoundWind to yakuId("round_wind"),
        RiichiYakuType.SeatWind to yakuId("seat_wind"),
        RiichiYakuType.WhiteDragon to yakuId("white_dragon"),
        RiichiYakuType.GreenDragon to yakuId("green_dragon"),
        RiichiYakuType.RedDragon to yakuId("red_dragon"),
        RiichiYakuType.KokushiMusou to yakuId("kokushi_musou"),
        RiichiYakuType.ChurenPoto to yakuId("churen_poto"),
        RiichiYakuType.Tsuuiisou to yakuId("tsuuiisou"),
        RiichiYakuType.Ryuuuiisou to yakuId("ryuuiisou"),
        RiichiYakuType.Suuankou to yakuId("suuankou"),
        RiichiYakuType.Sukantsu to yakuId("suukantsu"),
        RiichiYakuType.Shousuushi to yakuId("shousuushii"),
        RiichiYakuType.Daisangen to yakuId("daisangen"),
        RiichiYakuType.Chinroutou to yakuId("chinroutou"),
        RiichiYakuType.Tenhou to yakuId("tenhou"),
        RiichiYakuType.Chiihou to yakuId("chiihou"),
        RiichiYakuType.KokushiMusou13 to yakuId("kokushi_musou_13_wait"),
        RiichiYakuType.ChurenPoto9 to yakuId("junsei_churen_poto"),
        RiichiYakuType.SuuankouTanki to yakuId("suuankou_tanki"),
        RiichiYakuType.Daisuushii to yakuId("daisuushii"),
        RiichiYakuType.TsubameGaeshi to yakuId("tsubame_gaeshi"),
        RiichiYakuType.Kanburi to yakuId("kanburi"),
        RiichiYakuType.ShiiaruRaotai to yakuId("shiiaru_raotai"),
        RiichiYakuType.UumenChii to yakuId("uumen_chii"),
        RiichiYakuType.Sanrenkou to yakuId("sanrenkou"),
        RiichiYakuType.IsshokuSanjun to yakuId("isshoku_sanjun"),
        RiichiYakuType.IipinMoyue to yakuId("iipin_moyue"),
        RiichiYakuType.ChuupinRaoyui to yakuId("chuupin_raoyui"),
        RiichiYakuType.Renhou to yakuId("renhou"),
        RiichiYakuType.Daisharin to yakuId("daisharin"),
        RiichiYakuType.Daichikurin to yakuId("daichikurin"),
        RiichiYakuType.Daisuurin to yakuId("daisuurin"),
        RiichiYakuType.IshinoUeSannen to yakuId("ishi_no_ue_ni_mo_sannen"),
        RiichiYakuType.Daichisei to yakuId("daichisei"),
    )

    /** 由條目 ID 對回役種。 */
    private val YAKU_BY_ID: Map<String, RiichiYakuType> = YAKU_IDS.entries.associate { (type, id) -> id to type }

    /**
     * 取得役種的條目 ID。
     *
     * @param type 役種。
     * @return 役種的 namespaced 條目 ID。
     */
    fun yaku(type: RiichiYakuType): String = YAKU_IDS.getValue(type)

    /**
     * 由條目 ID 取得役種。
     *
     * @param id 條目 ID。
     * @return 對應的役種；不是役種條目時為 null。
     */
    fun yakuType(id: String): RiichiYakuType? = YAKU_BY_ID[id]

    /**
     * 建立一般役種條目。
     *
     * @param type 役種。
     * @param han 翻數。
     * @return 附帶翻數的條目。
     */
    fun yakuEntry(type: RiichiYakuType, han: Int): WinSettlementDetailEntry = WinSettlementDetailEntry(yaku(type), WinSettlementQuantity(HAN, han))

    /**
     * 建立役滿條目。
     *
     * @param type 役種。
     * @param multiplier 役滿倍數。
     * @return 附帶役滿倍數的條目。
     */
    fun yakumanEntry(type: RiichiYakuType, multiplier: Int): WinSettlementDetailEntry = WinSettlementDetailEntry(yaku(type), WinSettlementQuantity(YAKUMAN, multiplier))

    /**
     * 以役種識別片段建立完整的役種條目 ID。
     *
     * @param slug 役種的穩定識別片段。
     * @return 日麻役種條目的完整 namespaced ID。
     */
    private fun yakuId(slug: String): String = MahjongCraftMetadata.id("riichi/yaku/$slug")
}
