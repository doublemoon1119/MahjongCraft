package com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 內建日麻役滿的胡牌展示理由 ID；逐一明列，不由 [RiichiYakuType] 的名稱推導。 */
object RiichiWinCelebrationCueIds {
    /** 每個役滿役種的展示理由 ID。 */
    private val YAKUMAN_CUE_IDS: Map<RiichiYakuType, String> = mapOf(
        RiichiYakuType.KokushiMusou to MahjongCraftMetadata.id("riichi/yakuman/kokushi_musou"),
        RiichiYakuType.ChurenPoto to MahjongCraftMetadata.id("riichi/yakuman/churen_poto"),
        RiichiYakuType.Tsuuiisou to MahjongCraftMetadata.id("riichi/yakuman/tsuuiisou"),
        RiichiYakuType.Ryuuuiisou to MahjongCraftMetadata.id("riichi/yakuman/ryuuuiisou"),
        RiichiYakuType.Suuankou to MahjongCraftMetadata.id("riichi/yakuman/suuankou"),
        RiichiYakuType.Sukantsu to MahjongCraftMetadata.id("riichi/yakuman/sukantsu"),
        RiichiYakuType.Shousuushi to MahjongCraftMetadata.id("riichi/yakuman/shousuushi"),
        RiichiYakuType.Daisangen to MahjongCraftMetadata.id("riichi/yakuman/daisangen"),
        RiichiYakuType.Chinroutou to MahjongCraftMetadata.id("riichi/yakuman/chinroutou"),
        RiichiYakuType.Tenhou to MahjongCraftMetadata.id("riichi/yakuman/tenhou"),
        RiichiYakuType.Chiihou to MahjongCraftMetadata.id("riichi/yakuman/chiihou"),
        RiichiYakuType.KokushiMusou13 to MahjongCraftMetadata.id("riichi/yakuman/kokushi_musou_13"),
        RiichiYakuType.ChurenPoto9 to MahjongCraftMetadata.id("riichi/yakuman/churen_poto_9"),
        RiichiYakuType.SuuankouTanki to MahjongCraftMetadata.id("riichi/yakuman/suuankou_tanki"),
        RiichiYakuType.Daisuushii to MahjongCraftMetadata.id("riichi/yakuman/daisuushii"),
        RiichiYakuType.Renhou to MahjongCraftMetadata.id("riichi/yakuman/renhou"),
        RiichiYakuType.Daisharin to MahjongCraftMetadata.id("riichi/yakuman/daisharin"),
        RiichiYakuType.Daichikurin to MahjongCraftMetadata.id("riichi/yakuman/daichikurin"),
        RiichiYakuType.Daisuurin to MahjongCraftMetadata.id("riichi/yakuman/daisuurin"),
        RiichiYakuType.IshinoUeSannen to MahjongCraftMetadata.id("riichi/yakuman/ishino_ue_sannen"),
        RiichiYakuType.Daichisei to MahjongCraftMetadata.id("riichi/yakuman/daichisei"),
    )

    /** 有展示理由的役滿役種。 */
    val yakumanTypes: Set<RiichiYakuType> get() = YAKUMAN_CUE_IDS.keys

    /**
     * 取得役滿役種的展示理由 ID。
     *
     * @param type 役滿役種。
     * @return 展示理由 ID。
     */
    fun yakuman(type: RiichiYakuType): String = YAKUMAN_CUE_IDS.getValue(type)
}
