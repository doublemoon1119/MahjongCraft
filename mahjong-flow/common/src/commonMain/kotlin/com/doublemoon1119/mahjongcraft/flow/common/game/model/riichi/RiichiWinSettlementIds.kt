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
    val NAGASHI_MANGAN: String = MahjongCraftMetadata.id("riichi/yaku/nagashi_mangan")

    /** 每個役種的條目 ID。 */
    private val YAKU_IDS: Map<RiichiYakuType, String> = mapOf(
        RiichiYakuType.Dora to MahjongCraftMetadata.id("riichi/yaku/dora"),
        RiichiYakuType.UraDora to MahjongCraftMetadata.id("riichi/yaku/ura_dora"),
        RiichiYakuType.AkaDora to MahjongCraftMetadata.id("riichi/yaku/aka_dora"),
        RiichiYakuType.NukiDora to MahjongCraftMetadata.id("riichi/yaku/nuki_dora"),
        RiichiYakuType.Tanyao to MahjongCraftMetadata.id("riichi/yaku/tanyao"),
        RiichiYakuType.Pinfu to MahjongCraftMetadata.id("riichi/yaku/pinfu"),
        RiichiYakuType.Iipeikou to MahjongCraftMetadata.id("riichi/yaku/iipeikou"),
        RiichiYakuType.Riichi to MahjongCraftMetadata.id("riichi/yaku/riichi"),
        RiichiYakuType.DoubleRiichi to MahjongCraftMetadata.id("riichi/yaku/double_riichi"),
        RiichiYakuType.Ippatsu to MahjongCraftMetadata.id("riichi/yaku/ippatsu"),
        RiichiYakuType.RinshanKaihou to MahjongCraftMetadata.id("riichi/yaku/rinshan_kaihou"),
        RiichiYakuType.Haitei to MahjongCraftMetadata.id("riichi/yaku/haitei"),
        RiichiYakuType.Houtei to MahjongCraftMetadata.id("riichi/yaku/houtei"),
        RiichiYakuType.Chankan to MahjongCraftMetadata.id("riichi/yaku/chankan"),
        RiichiYakuType.Menzentsumo to MahjongCraftMetadata.id("riichi/yaku/menzen_tsumo"),
        RiichiYakuType.Toitoi to MahjongCraftMetadata.id("riichi/yaku/toitoi"),
        RiichiYakuType.Sanankou to MahjongCraftMetadata.id("riichi/yaku/sanankou"),
        RiichiYakuType.Sankantsu to MahjongCraftMetadata.id("riichi/yaku/sankantsu"),
        RiichiYakuType.SanshokuDokoku to MahjongCraftMetadata.id("riichi/yaku/sanshoku_doukou"),
        RiichiYakuType.SanshokuDoujun to MahjongCraftMetadata.id("riichi/yaku/sanshoku_doujun"),
        RiichiYakuType.Honchan to MahjongCraftMetadata.id("riichi/yaku/chanta"),
        RiichiYakuType.Junchan to MahjongCraftMetadata.id("riichi/yaku/junchan"),
        RiichiYakuType.Honitsu to MahjongCraftMetadata.id("riichi/yaku/honitsu"),
        RiichiYakuType.Ryanpeikou to MahjongCraftMetadata.id("riichi/yaku/ryanpeikou"),
        RiichiYakuType.Ittuitsu to MahjongCraftMetadata.id("riichi/yaku/ittsuu"),
        RiichiYakuType.Honroutou to MahjongCraftMetadata.id("riichi/yaku/honroutou"),
        RiichiYakuType.Chinitsu to MahjongCraftMetadata.id("riichi/yaku/chinitsu"),
        RiichiYakuType.Shousangen to MahjongCraftMetadata.id("riichi/yaku/shousangen"),
        RiichiYakuType.Chiitoitsu to MahjongCraftMetadata.id("riichi/yaku/chiitoitsu"),
        RiichiYakuType.RoundWind to MahjongCraftMetadata.id("riichi/yaku/round_wind"),
        RiichiYakuType.SeatWind to MahjongCraftMetadata.id("riichi/yaku/seat_wind"),
        RiichiYakuType.WhiteDragon to MahjongCraftMetadata.id("riichi/yaku/white_dragon"),
        RiichiYakuType.GreenDragon to MahjongCraftMetadata.id("riichi/yaku/green_dragon"),
        RiichiYakuType.RedDragon to MahjongCraftMetadata.id("riichi/yaku/red_dragon"),
        RiichiYakuType.KokushiMusou to MahjongCraftMetadata.id("riichi/yaku/kokushi_musou"),
        RiichiYakuType.ChurenPoto to MahjongCraftMetadata.id("riichi/yaku/churen_poto"),
        RiichiYakuType.Tsuuiisou to MahjongCraftMetadata.id("riichi/yaku/tsuuiisou"),
        RiichiYakuType.Ryuuuiisou to MahjongCraftMetadata.id("riichi/yaku/ryuuiisou"),
        RiichiYakuType.Suuankou to MahjongCraftMetadata.id("riichi/yaku/suuankou"),
        RiichiYakuType.Sukantsu to MahjongCraftMetadata.id("riichi/yaku/suukantsu"),
        RiichiYakuType.Shousuushi to MahjongCraftMetadata.id("riichi/yaku/shousuushii"),
        RiichiYakuType.Daisangen to MahjongCraftMetadata.id("riichi/yaku/daisangen"),
        RiichiYakuType.Chinroutou to MahjongCraftMetadata.id("riichi/yaku/chinroutou"),
        RiichiYakuType.Tenhou to MahjongCraftMetadata.id("riichi/yaku/tenhou"),
        RiichiYakuType.Chiihou to MahjongCraftMetadata.id("riichi/yaku/chiihou"),
        RiichiYakuType.KokushiMusou13 to MahjongCraftMetadata.id("riichi/yaku/kokushi_musou_13_wait"),
        RiichiYakuType.ChurenPoto9 to MahjongCraftMetadata.id("riichi/yaku/junsei_churen_poto"),
        RiichiYakuType.SuuankouTanki to MahjongCraftMetadata.id("riichi/yaku/suuankou_tanki"),
        RiichiYakuType.Daisuushii to MahjongCraftMetadata.id("riichi/yaku/daisuushii"),
        RiichiYakuType.TsubameGaeshi to MahjongCraftMetadata.id("riichi/yaku/tsubame_gaeshi"),
        RiichiYakuType.Kanburi to MahjongCraftMetadata.id("riichi/yaku/kanburi"),
        RiichiYakuType.ShiiaruRaotai to MahjongCraftMetadata.id("riichi/yaku/shiiaru_raotai"),
        RiichiYakuType.UumenChii to MahjongCraftMetadata.id("riichi/yaku/uumen_chii"),
        RiichiYakuType.Sanrenkou to MahjongCraftMetadata.id("riichi/yaku/sanrenkou"),
        RiichiYakuType.IsshokuSanjun to MahjongCraftMetadata.id("riichi/yaku/isshoku_sanjun"),
        RiichiYakuType.IipinMoyue to MahjongCraftMetadata.id("riichi/yaku/iipin_moyue"),
        RiichiYakuType.ChuupinRaoyui to MahjongCraftMetadata.id("riichi/yaku/chuupin_raoyui"),
        RiichiYakuType.Renhou to MahjongCraftMetadata.id("riichi/yaku/renhou"),
        RiichiYakuType.Daisharin to MahjongCraftMetadata.id("riichi/yaku/daisharin"),
        RiichiYakuType.Daichikurin to MahjongCraftMetadata.id("riichi/yaku/daichikurin"),
        RiichiYakuType.Daisuurin to MahjongCraftMetadata.id("riichi/yaku/daisuurin"),
        RiichiYakuType.IshinoUeSannen to MahjongCraftMetadata.id("riichi/yaku/ishi_no_ue_ni_mo_sannen"),
        RiichiYakuType.Daichisei to MahjongCraftMetadata.id("riichi/yaku/daichisei"),
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
}
