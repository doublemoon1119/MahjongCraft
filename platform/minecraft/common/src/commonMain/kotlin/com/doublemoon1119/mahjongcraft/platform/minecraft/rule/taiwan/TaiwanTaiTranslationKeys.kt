package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType

/** 台灣麻將計台項目顯示名稱的 translation key 單一來源，放在 `mahjongcraft.taiwan.tai.` 底下。 */
object TaiwanTaiTranslationKeys {
    /** 每個計台項目對應的 translation key。 */
    private val KEYS: Map<TaiwanTaiType, String> = mapOf(
        TaiwanTaiType.ConcealedHand to "mahjongcraft.taiwan.tai.concealed_hand",
        TaiwanTaiType.SelfDraw to "mahjongcraft.taiwan.tai.self_draw",
        TaiwanTaiType.FullyConcealedHand to "mahjongcraft.taiwan.tai.fully_concealed_hand",
        TaiwanTaiType.SeatWind to "mahjongcraft.taiwan.tai.seat_wind",
        TaiwanTaiType.RoundWind to "mahjongcraft.taiwan.tai.round_wind",
        TaiwanTaiType.Dragon to "mahjongcraft.taiwan.tai.dragon",
        TaiwanTaiType.SeatFlower to "mahjongcraft.taiwan.tai.seat_flower",
        TaiwanTaiType.FlowerSet to "mahjongcraft.taiwan.tai.flower_set",
        TaiwanTaiType.SingleWait to "mahjongcraft.taiwan.tai.single_wait",
        TaiwanTaiType.RobbingKong to "mahjongcraft.taiwan.tai.robbing_kong",
        TaiwanTaiType.ReplacementWin to "mahjongcraft.taiwan.tai.replacement_win",
        TaiwanTaiType.LastDraw to "mahjongcraft.taiwan.tai.last_draw",
        TaiwanTaiType.AllSequences to "mahjongcraft.taiwan.tai.all_sequences",
        TaiwanTaiType.ThreeConcealedTriplets to "mahjongcraft.taiwan.tai.three_concealed_triplets",
        TaiwanTaiType.FourConcealedTriplets to "mahjongcraft.taiwan.tai.four_concealed_triplets",
        TaiwanTaiType.FiveConcealedTriplets to "mahjongcraft.taiwan.tai.five_concealed_triplets",
        TaiwanTaiType.AllExposed to "mahjongcraft.taiwan.tai.all_exposed",
        TaiwanTaiType.AllTriplets to "mahjongcraft.taiwan.tai.all_triplets",
        TaiwanTaiType.LittleDragons to "mahjongcraft.taiwan.tai.little_dragons",
        TaiwanTaiType.BigDragons to "mahjongcraft.taiwan.tai.big_dragons",
        TaiwanTaiType.HalfFlush to "mahjongcraft.taiwan.tai.half_flush",
        TaiwanTaiType.FullFlush to "mahjongcraft.taiwan.tai.full_flush",
        TaiwanTaiType.LittleWinds to "mahjongcraft.taiwan.tai.little_winds",
        TaiwanTaiType.BigWinds to "mahjongcraft.taiwan.tai.big_winds",
        TaiwanTaiType.AllHonors to "mahjongcraft.taiwan.tai.all_honors",
        TaiwanTaiType.DeclaredReady to "mahjongcraft.taiwan.tai.declared_ready",
        TaiwanTaiType.HeavenlyReady to "mahjongcraft.taiwan.tai.heavenly_ready",
        TaiwanTaiType.EarthlyReady to "mahjongcraft.taiwan.tai.earthly_ready",
        TaiwanTaiType.HumanWin to "mahjongcraft.taiwan.tai.human_win",
        TaiwanTaiType.EarthlyWin to "mahjongcraft.taiwan.tai.earthly_win",
        TaiwanTaiType.HeavenlyWin to "mahjongcraft.taiwan.tai.heavenly_win",
        TaiwanTaiType.EightFlowers to "mahjongcraft.taiwan.tai.eight_flowers",
        TaiwanTaiType.SevenFlowers to "mahjongcraft.taiwan.tai.seven_flowers",
    )

    /** 初莊時的莊家台名稱。 */
    const val DEALER: String = "mahjongcraft.taiwan.tai.dealer"

    /** 連莊時合併莊家、連莊與拉莊的條目名稱，帶連莊次數。 */
    const val DEALER_STREAK: String = "mahjongcraft.taiwan.tai.dealer_streak"

    /**
     * 取得計台項目的名稱 key。
     *
     * @param type 計台項目。
     * @return 名稱 translation key。
     */
    fun keyFor(type: TaiwanTaiType): String = KEYS.getValue(type)

    /**
     * 取得胡牌詳情台種條目的名稱 key。
     *
     * @param entryId [TaiwanWinSettlementIds] 定義的計台項目、莊家或連莊條目 ID。
     * @return 名稱 key；不是台灣麻將台種條目時為 null。
     */
    fun keyForEntry(entryId: String): String? = when (entryId) {
        TaiwanWinSettlementIds.DEALER_ENTRY -> DEALER
        TaiwanWinSettlementIds.DEALER_STREAK_ENTRY -> DEALER_STREAK
        else -> TaiwanWinSettlementIds.taiType(entryId)?.let(::keyFor)
    }

    /** 平台語系資源必須提供的全部台種 translation key。 */
    val ALL: Set<String> = buildSet {
        addAll(KEYS.values)
        add(DEALER)
        add(DEALER_STREAK)
    }
}
