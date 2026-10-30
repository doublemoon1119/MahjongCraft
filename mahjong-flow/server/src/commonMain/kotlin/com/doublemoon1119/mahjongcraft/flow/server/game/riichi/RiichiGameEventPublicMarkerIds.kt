package com.doublemoon1119.mahjongcraft.flow.server.game.riichi

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 日麻公開事件標記與數值單位的完整識別碼。 */
object RiichiGameEventPublicMarkerIds {
    /** 和牌者當時已宣告立直。 */
    val RIICHI: String = MahjongCraftMetadata.id("riichi/public_state/riichi")

    /** 和牌者當時仍保有一發資格。 */
    val IPPATSU: String = MahjongCraftMetadata.id("riichi/public_state/ippatsu")

    /** 終局所使用的擊飛分數門檻。 */
    val BUST_THRESHOLD: String = MahjongCraftMetadata.id("riichi/match/bust_threshold")

    /** 終局時因低於門檻而被擊飛的玩家。 */
    val BUSTED: String = MahjongCraftMetadata.id("riichi/match/busted")

    /** 分數數值的單位。 */
    val POINTS_UNIT: String = MahjongCraftMetadata.id("riichi/points")

    /**
     * 公開事件中場風的識別碼。
     *
     * @param wind 場風。
     * @return 該場風的完整識別碼。
     */
    fun prevalentWind(wind: Wind): String = MahjongCraftMetadata.id("wind/${wind.name.lowercase()}")
}
