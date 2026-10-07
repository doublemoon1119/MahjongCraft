package com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 內建日麻胡牌詳情使用的欄位、條目與單位 ID。
 *
 * 這些 ID 會保存在對局歷史中；役種 ID 逐一明列，不由 [YakuType] 的名稱推導，讓 enum 改名不影響已保存的紀錄。
 */
object RiichiWinSettlementIds {
    /** 役種條目欄位；值為 [WinSettlementDetailValue.Entries]。 */
    val YAKU_FIELD: String = MahjongCraftMetadata.id("riichi/yaku_list")

    /** 一般和牌的翻符欄位；值為 [WinSettlementDetailValue.Quantities]，依序為翻與符，沒有權威符數時只有翻。 */
    val HAN_FU_FIELD: String = MahjongCraftMetadata.id("riichi/han_fu")

    /** 自然役滿的合計倍數欄位；值為 [WinSettlementDetailValue.Quantities]，只有役滿倍數。 */
    val YAKUMAN_TOTAL_FIELD: String = MahjongCraftMetadata.id("riichi/yakuman_total")

    /** 寶牌指示牌欄位；值為 [WinSettlementDetailValue.Tiles]。 */
    val DORA_FIELD: String = MahjongCraftMetadata.id("riichi/dora_indicators")

    /** 裏寶牌指示牌欄位；只在立直和牌出現，值為 [WinSettlementDetailValue.Tiles]。 */
    val URA_DORA_FIELD: String = MahjongCraftMetadata.id("riichi/ura_dora_indicators")

    /** 翻數單位。 */
    val HAN: String = MahjongCraftMetadata.id("riichi/han")

    /** 符數單位。 */
    val FU: String = MahjongCraftMetadata.id("riichi/fu")

    /** 役滿倍數單位；1 表示役滿，2 表示兩倍役滿。 */
    val YAKUMAN: String = MahjongCraftMetadata.id("riichi/yakuman")

    /** 流局滿貫條目；流局滿貫不經過一般役種判定，不屬於 [YakuType]。 */
    val NAGASHI_MANGAN: String = yakuId("nagashi_mangan")

    /** 每個役種的條目 ID。 */
    private val YAKU_IDS: Map<YakuType, String> = mapOf(
        YakuType.Dora to yakuId("dora"),
        YakuType.UraDora to yakuId("ura_dora"),
        YakuType.AkaDora to yakuId("aka_dora"),
        YakuType.Tanyao to yakuId("tanyao"),
        YakuType.Pinfu to yakuId("pinfu"),
        YakuType.Iipeikou to yakuId("iipeikou"),
        YakuType.Riichi to yakuId("riichi"),
        YakuType.DoubleRiichi to yakuId("double_riichi"),
        YakuType.Ippatsu to yakuId("ippatsu"),
        YakuType.RinshanKaihou to yakuId("rinshan_kaihou"),
        YakuType.Haitei to yakuId("haitei"),
        YakuType.Houtei to yakuId("houtei"),
        YakuType.Chankan to yakuId("chankan"),
        YakuType.Menzentsumo to yakuId("menzen_tsumo"),
        YakuType.Toitoi to yakuId("toitoi"),
        YakuType.Sanankou to yakuId("sanankou"),
        YakuType.Sankantsu to yakuId("sankantsu"),
        YakuType.SanshokuDokoku to yakuId("sanshoku_doukou"),
        YakuType.SanshokuDoujun to yakuId("sanshoku_doujun"),
        YakuType.Honchan to yakuId("chanta"),
        YakuType.Junchan to yakuId("junchan"),
        YakuType.Honitsu to yakuId("honitsu"),
        YakuType.Ryanpeikou to yakuId("ryanpeikou"),
        YakuType.Ittuitsu to yakuId("ittsuu"),
        YakuType.Honroutou to yakuId("honroutou"),
        YakuType.Chinitsu to yakuId("chinitsu"),
        YakuType.Shousangen to yakuId("shousangen"),
        YakuType.Chiitoitsu to yakuId("chiitoitsu"),
        YakuType.RoundWind to yakuId("round_wind"),
        YakuType.SeatWind to yakuId("seat_wind"),
        YakuType.Dragon to yakuId("dragon"),
        YakuType.KokushiMusou to yakuId("kokushi_musou"),
        YakuType.ChurenPoto to yakuId("churen_poto"),
        YakuType.Tsuuiisou to yakuId("tsuuiisou"),
        YakuType.Ryuuuiisou to yakuId("ryuuiisou"),
        YakuType.Suuankou to yakuId("suuankou"),
        YakuType.Sukantsu to yakuId("suukantsu"),
        YakuType.Shousuushi to yakuId("shousuushii"),
        YakuType.Daisangen to yakuId("daisangen"),
        YakuType.Chinroutou to yakuId("chinroutou"),
        YakuType.Tenhou to yakuId("tenhou"),
        YakuType.Chiihou to yakuId("chiihou"),
        YakuType.KokushiMusou13 to yakuId("kokushi_musou_13_wait"),
        YakuType.ChurenPoto9 to yakuId("junsei_churen_poto"),
        YakuType.SuuankouTanki to yakuId("suuankou_tanki"),
        YakuType.Daisuushii to yakuId("daisuushii"),
        YakuType.TsubameGaeshi to yakuId("tsubame_gaeshi"),
        YakuType.Kanburi to yakuId("kanburi"),
        YakuType.ShiiaruRaotai to yakuId("shiiaru_raotai"),
        YakuType.UumenChii to yakuId("uumen_chii"),
        YakuType.Sanrenkou to yakuId("sanrenkou"),
        YakuType.IsshokuSanjun to yakuId("isshoku_sanjun"),
        YakuType.IipinMoyue to yakuId("iipin_moyue"),
        YakuType.ChuupinRaoyui to yakuId("chuupin_raoyui"),
        YakuType.Renhou to yakuId("renhou"),
        YakuType.Daisharin to yakuId("daisharin"),
        YakuType.Daichikurin to yakuId("daichikurin"),
        YakuType.Daisuurin to yakuId("daisuurin"),
        YakuType.IshinoUeSannen to yakuId("ishi_no_ue_ni_mo_sannen"),
        YakuType.Daichisei to yakuId("daichisei"),
    )

    /** 由條目 ID 對回役種。 */
    private val YAKU_BY_ID: Map<String, YakuType> = YAKU_IDS.entries.associate { (type, id) -> id to type }

    /**
     * 取得役種的條目 ID。
     *
     * @param type 役種。
     * @return 役種的 namespaced 條目 ID。
     */
    fun yaku(type: YakuType): String = YAKU_IDS.getValue(type)

    /**
     * 由條目 ID 取得役種。
     *
     * @param id 條目 ID。
     * @return 對應的役種；不是役種條目時為 null。
     */
    fun yakuType(id: String): YakuType? = YAKU_BY_ID[id]

    /**
     * 建立一般役種條目。
     *
     * @param type 役種。
     * @param han 翻數。
     * @return 附帶翻數的條目。
     */
    fun yakuEntry(type: YakuType, han: Int): WinSettlementDetailEntry = WinSettlementDetailEntry(yaku(type), WinSettlementQuantity(HAN, han))

    /**
     * 建立役滿條目。
     *
     * @param type 役種。
     * @param multiplier 役滿倍數。
     * @return 附帶役滿倍數的條目。
     */
    fun yakumanEntry(type: YakuType, multiplier: Int): WinSettlementDetailEntry = WinSettlementDetailEntry(yaku(type), WinSettlementQuantity(YAKUMAN, multiplier))

    private fun yakuId(slug: String): String = MahjongCraftMetadata.id("riichi/yaku/$slug")
}
