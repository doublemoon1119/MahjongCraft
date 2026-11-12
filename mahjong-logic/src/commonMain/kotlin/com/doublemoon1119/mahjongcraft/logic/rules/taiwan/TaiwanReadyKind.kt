package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 宣告聽牌的種類，由宣告時機決定。
 *
 * @property taiType 宣告後胡牌時成立的計台項目。
 * @property indicatorId 公開標示宣告種類的 indicator ID。
 */
enum class TaiwanReadyKind(val taiType: TaiwanTaiType, val indicatorId: String) {
    /** 一般時候的宣告聽牌。 */
    GENERAL(TaiwanTaiType.DeclaredReady, MahjongCraftMetadata.id("taiwan/ready_indicator/general")),

    /** 莊家打出第一張牌時宣告的天聽。 */
    HEAVENLY(TaiwanTaiType.HeavenlyReady, MahjongCraftMetadata.id("taiwan/ready_indicator/heavenly")),

    /** 第一巡內宣告的地聽。 */
    EARTHLY(TaiwanTaiType.EarthlyReady, MahjongCraftMetadata.id("taiwan/ready_indicator/earthly")),
}
