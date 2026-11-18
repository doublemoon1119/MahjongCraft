package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.achievement

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 內建台灣麻將專屬的成果 ID。 */
object TaiwanAchievementIds {
    /** 完成一場台灣麻將。 */
    val MATCH_COMPLETED: String = MahjongCraftMetadata.id("taiwan/match_completed")

    /** 全求人。 */
    val ALL_EXPOSED: String = MahjongCraftMetadata.id("taiwan/all_exposed")

    /** 清一色。 */
    val FULL_FLUSH: String = MahjongCraftMetadata.id("taiwan/full_flush")

    /** 五暗刻。 */
    val FIVE_CONCEALED_TRIPLETS: String = MahjongCraftMetadata.id("taiwan/five_concealed_triplets")

    /** 大三元。 */
    val BIG_DRAGONS: String = MahjongCraftMetadata.id("taiwan/big_dragons")

    /** 大四喜。 */
    val BIG_WINDS: String = MahjongCraftMetadata.id("taiwan/big_winds")

    /** 字一色。 */
    val ALL_HONORS: String = MahjongCraftMetadata.id("taiwan/all_honors")

    /** 天胡。 */
    val HEAVENLY_WIN: String = MahjongCraftMetadata.id("taiwan/heavenly_win")

    /** 地胡。 */
    val EARTHLY_WIN: String = MahjongCraftMetadata.id("taiwan/earthly_win")

    /** 人胡。 */
    val HUMAN_WIN: String = MahjongCraftMetadata.id("taiwan/human_win")

    /** 八仙過海。 */
    val EIGHT_FLOWERS: String = MahjongCraftMetadata.id("taiwan/eight_flowers")

    /** 七搶一。 */
    val SEVEN_FLOWERS: String = MahjongCraftMetadata.id("taiwan/seven_flowers")

    /** 一般胡牌結算台種與對應成果。 */
    private val taiAchievements: Map<TaiwanTaiType, String> = mapOf(
        TaiwanTaiType.AllExposed to ALL_EXPOSED,
        TaiwanTaiType.FullFlush to FULL_FLUSH,
        TaiwanTaiType.FiveConcealedTriplets to FIVE_CONCEALED_TRIPLETS,
        TaiwanTaiType.BigDragons to BIG_DRAGONS,
        TaiwanTaiType.BigWinds to BIG_WINDS,
        TaiwanTaiType.AllHonors to ALL_HONORS,
        TaiwanTaiType.HeavenlyWin to HEAVENLY_WIN,
        TaiwanTaiType.EarthlyWin to EARTHLY_WIN,
        TaiwanTaiType.HumanWin to HUMAN_WIN,
    )

    /** 查詢台種對應的成果；沒有專屬成果的台種回傳 null。 */
    fun forTai(type: TaiwanTaiType): String? = taiAchievements[type]
}
