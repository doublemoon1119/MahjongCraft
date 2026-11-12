package com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementQuantity
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 台灣麻將胡牌結算詳情使用的穩定識別碼。 */
object TaiwanWinSettlementIds {
    /** 台種與台數列表欄位。 */
    val TAI_FIELD: String = MahjongCraftMetadata.id("taiwan/win_settlement/tai")

    /** 合計台數欄位。 */
    val TAI_TOTAL_FIELD: String = MahjongCraftMetadata.id("taiwan/win_settlement/tai_total")

    /** 贏家公開的花牌欄位；特殊花牌和牌不含讓和牌成立的那張。 */
    val FLOWERS_FIELD: String = MahjongCraftMetadata.id("taiwan/win_settlement/flowers")

    /** 讓特殊花牌和牌成立的那張花牌欄位。 */
    val WINNING_FLOWER_FIELD: String = MahjongCraftMetadata.id("taiwan/win_settlement/winning_flower")

    /** 閒家自摸時莊家那一份的合計台數欄位。 */
    val DEALER_TAI_TOTAL_FIELD: String = MahjongCraftMetadata.id("taiwan/win_settlement/dealer_tai_total")

    /** 初莊時的莊家台條目。 */
    val DEALER_ENTRY: String = MahjongCraftMetadata.id("taiwan/tai/dealer")

    /** 連莊時合併莊家、連莊與拉莊的條目；名稱數值是連莊次數。 */
    val DEALER_STREAK_ENTRY: String = MahjongCraftMetadata.id("taiwan/tai/dealer_streak")

    /** 連莊次數單位。 */
    val DEALER_STREAK_UNIT: String = MahjongCraftMetadata.id("taiwan/unit/dealer_streak")

    /** 台數單位。 */
    val TAI_UNIT: String = MahjongCraftMetadata.id("taiwan/unit/tai")

    /** 每個計台項目的條目 ID。 */
    private val TAI_IDS: Map<TaiwanTaiType, String> = mapOf(
        TaiwanTaiType.ConcealedHand to MahjongCraftMetadata.id("taiwan/tai/concealed_hand"),
        TaiwanTaiType.SelfDraw to MahjongCraftMetadata.id("taiwan/tai/self_draw"),
        TaiwanTaiType.FullyConcealedHand to MahjongCraftMetadata.id("taiwan/tai/fully_concealed_hand"),
        TaiwanTaiType.SeatWind to MahjongCraftMetadata.id("taiwan/tai/seat_wind"),
        TaiwanTaiType.RoundWind to MahjongCraftMetadata.id("taiwan/tai/round_wind"),
        TaiwanTaiType.Dragon to MahjongCraftMetadata.id("taiwan/tai/dragon"),
        TaiwanTaiType.SeatFlower to MahjongCraftMetadata.id("taiwan/tai/seat_flower"),
        TaiwanTaiType.FlowerSet to MahjongCraftMetadata.id("taiwan/tai/flower_set"),
        TaiwanTaiType.SingleWait to MahjongCraftMetadata.id("taiwan/tai/single_wait"),
        TaiwanTaiType.RobbingKong to MahjongCraftMetadata.id("taiwan/tai/robbing_kong"),
        TaiwanTaiType.ReplacementWin to MahjongCraftMetadata.id("taiwan/tai/replacement_win"),
        TaiwanTaiType.LastDraw to MahjongCraftMetadata.id("taiwan/tai/last_draw"),
        TaiwanTaiType.AllSequences to MahjongCraftMetadata.id("taiwan/tai/all_sequences"),
        TaiwanTaiType.ThreeConcealedTriplets to MahjongCraftMetadata.id("taiwan/tai/three_concealed_triplets"),
        TaiwanTaiType.FourConcealedTriplets to MahjongCraftMetadata.id("taiwan/tai/four_concealed_triplets"),
        TaiwanTaiType.FiveConcealedTriplets to MahjongCraftMetadata.id("taiwan/tai/five_concealed_triplets"),
        TaiwanTaiType.AllExposed to MahjongCraftMetadata.id("taiwan/tai/all_exposed"),
        TaiwanTaiType.AllTriplets to MahjongCraftMetadata.id("taiwan/tai/all_triplets"),
        TaiwanTaiType.LittleDragons to MahjongCraftMetadata.id("taiwan/tai/little_dragons"),
        TaiwanTaiType.BigDragons to MahjongCraftMetadata.id("taiwan/tai/big_dragons"),
        TaiwanTaiType.HalfFlush to MahjongCraftMetadata.id("taiwan/tai/half_flush"),
        TaiwanTaiType.FullFlush to MahjongCraftMetadata.id("taiwan/tai/full_flush"),
        TaiwanTaiType.LittleWinds to MahjongCraftMetadata.id("taiwan/tai/little_winds"),
        TaiwanTaiType.BigWinds to MahjongCraftMetadata.id("taiwan/tai/big_winds"),
        TaiwanTaiType.AllHonors to MahjongCraftMetadata.id("taiwan/tai/all_honors"),
        TaiwanTaiType.DeclaredReady to MahjongCraftMetadata.id("taiwan/tai/declared_ready"),
        TaiwanTaiType.HeavenlyReady to MahjongCraftMetadata.id("taiwan/tai/heavenly_ready"),
        TaiwanTaiType.EarthlyReady to MahjongCraftMetadata.id("taiwan/tai/earthly_ready"),
        TaiwanTaiType.HumanWin to MahjongCraftMetadata.id("taiwan/tai/human_win"),
        TaiwanTaiType.EarthlyWin to MahjongCraftMetadata.id("taiwan/tai/earthly_win"),
        TaiwanTaiType.HeavenlyWin to MahjongCraftMetadata.id("taiwan/tai/heavenly_win"),
        TaiwanTaiType.EightFlowers to MahjongCraftMetadata.id("taiwan/tai/eight_flowers"),
        TaiwanTaiType.SevenFlowers to MahjongCraftMetadata.id("taiwan/tai/seven_flowers"),
    )

    /** 由條目 ID 對回計台項目。 */
    private val TAI_BY_ID: Map<String, TaiwanTaiType> = TAI_IDS.entries.associate { (type, id) -> id to type }

    /**
     * 取得計台項目的條目 ID。
     *
     * @param type 計台項目。
     * @return 計台項目的 namespaced 條目 ID。
     */
    fun tai(type: TaiwanTaiType): String = TAI_IDS.getValue(type)

    /**
     * 由條目 ID 取得計台項目。
     *
     * @param id 條目 ID。
     * @return 對應的計台項目；不是計台項目的條目（例如莊家與連莊）時為 null。
     */
    fun taiType(id: String): TaiwanTaiType? = TAI_BY_ID[id]

    /**
     * 建立計台項目條目。
     *
     * @param type 計台項目。
     * @param tai 台數。
     * @return 附帶台數的條目。
     */
    fun taiEntry(type: TaiwanTaiType, tai: Int): WinSettlementDetailEntry = WinSettlementDetailEntry(tai(type), WinSettlementQuantity(TAI_UNIT, tai))
}
