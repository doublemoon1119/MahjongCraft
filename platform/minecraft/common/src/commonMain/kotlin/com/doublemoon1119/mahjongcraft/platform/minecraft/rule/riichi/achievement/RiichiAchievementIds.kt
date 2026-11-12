package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 內建日麻專屬的成果 ID。 */
object RiichiAchievementIds {
    /** 完成一場日麻對局。 */
    val MATCH_COMPLETED: String = MahjongCraftMetadata.id("riichi/match_completed")

    /** 立直後和牌。 */
    val RIICHI_WIN: String = MahjongCraftMetadata.id("riichi/riichi_win")

    /** 立直後一巡內和牌（一發）。 */
    val IPPATSU: String = MahjongCraftMetadata.id("riichi/ippatsu")

    /** 流局滿貫。 */
    val NAGASHI_MANGAN: String = MahjongCraftMetadata.id("riichi/nagashi_mangan")

    /** 點數低於擊飛門檻而結束對局。 */
    val BUSTED: String = MahjongCraftMetadata.id("riichi/busted")

    /** 被擊飛，且最終點數在 [FAR_BUST_SCORE] 以下。 */
    val BUSTED_FAR: String = MahjongCraftMetadata.id("riichi/busted_far")

    /** 在東一局（含連莊本場）被擊飛。 */
    val BUSTED_IN_EAST_ONE: String = MahjongCraftMetadata.id("riichi/busted_in_east_one")

    /** 累計役滿，總番數剛好 13 番。 */
    val COUNTED_YAKUMAN_EXACT: String = MahjongCraftMetadata.id("riichi/counted_yakuman_exact")

    /** 累計役滿，總番數 14 番以上。 */
    val COUNTED_YAKUMAN_OVER: String = MahjongCraftMetadata.id("riichi/counted_yakuman_over")

    /** 和出任一自然役滿；不含累計役滿。 */
    val YAKUMAN: String = MahjongCraftMetadata.id("riichi/yakuman")

    /** 和出任一雙倍役滿役種；兩個一般役滿疊加不算。 */
    val DOUBLE_YAKUMAN: String = MahjongCraftMetadata.id("riichi/double_yakuman")

    /** 同一次和牌有兩個以上役滿役種；雙倍役滿役種算一種。 */
    val MULTIPLE_YAKUMAN: String = MahjongCraftMetadata.id("riichi/multiple_yakuman")

    /** 「被大差距擊飛」的點數門檻：最終點數不高於此值。 */
    const val FAR_BUST_SCORE: Int = -30_000

    /** 一般役滿役種的成果 ID 尾段。 */
    private val YAKUMAN_PATHS: Map<RiichiYakuType, String> = mapOf(
        RiichiYakuType.KokushiMusou to "kokushi_musou",
        RiichiYakuType.ChurenPoto to "churen_poto",
        RiichiYakuType.Tsuuiisou to "tsuuiisou",
        RiichiYakuType.Ryuuuiisou to "ryuuiisou",
        RiichiYakuType.Suuankou to "suuankou",
        RiichiYakuType.Sukantsu to "suukantsu",
        RiichiYakuType.Shousuushi to "shousuushii",
        RiichiYakuType.Daisangen to "daisangen",
        RiichiYakuType.Chinroutou to "chinroutou",
        RiichiYakuType.Tenhou to "tenhou",
        RiichiYakuType.Chiihou to "chiihou",
    )

    /** 雙倍役滿役種的成果 ID 尾段。 */
    private val DOUBLE_YAKUMAN_PATHS: Map<RiichiYakuType, String> = mapOf(
        RiichiYakuType.KokushiMusou13 to "kokushi_musou_13_wait",
        RiichiYakuType.ChurenPoto9 to "junsei_churen_poto",
        RiichiYakuType.SuuankouTanki to "suuankou_tanki",
        RiichiYakuType.Daisuushii to "daisuushii",
    )

    /** 一般役滿役種。 */
    val yakumanTypes: Set<RiichiYakuType> get() = YAKUMAN_PATHS.keys

    /** 雙倍役滿役種。 */
    val doubleYakumanTypes: Set<RiichiYakuType> get() = DOUBLE_YAKUMAN_PATHS.keys

    /**
     * 取得 [type] 的役滿成果 ID；不是役滿役種時回傳 null。
     *
     * @param type 日麻役種。
     * @return 成果 ID，或 null。
     */
    fun yakuman(type: RiichiYakuType): String? = (YAKUMAN_PATHS[type] ?: DOUBLE_YAKUMAN_PATHS[type])
        ?.let { path -> MahjongCraftMetadata.id("riichi/yakuman/$path") }
}
