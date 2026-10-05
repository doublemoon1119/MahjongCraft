package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 各規則通用的玩家統計 ID，以及每個通用成果累加哪些統計。
 *
 * 規則不能登記自己的統計；統計名稱的語系鍵為 `stat.<namespace>.<path>`。
 */
object AchievementStatisticIds {
    /** 完成對局數。 */
    val MATCHES_COMPLETED: String = MahjongCraftMetadata.id("matches_completed")

    /** 第一名次數。 */
    val FIRST_PLACES: String = MahjongCraftMetadata.id("first_places")

    /** 胡牌次數，自摸與胡別人打出的牌都算。 */
    val WINS: String = MahjongCraftMetadata.id("wins")

    /** 自摸次數。 */
    val SELF_DRAW_WINS: String = MahjongCraftMetadata.id("self_draw_wins")

    /** 胡別人打出的牌次數。 */
    val DISCARD_WINS: String = MahjongCraftMetadata.id("discard_wins")

    /** 放槍次數。 */
    val DEAL_INS: String = MahjongCraftMetadata.id("deal_ins")

    /** 荒牌流局次數。 */
    val EXHAUSTIVE_DRAWS: String = MahjongCraftMetadata.id("exhaustive_draws")

    /** 通用成果與它累加的統計。 */
    private val STATISTIC_BY_ACHIEVEMENT: Map<String, String> = mapOf(
        BuiltInAchievementIds.MATCH_COMPLETED to MATCHES_COMPLETED,
        BuiltInAchievementIds.FIRST_PLACE to FIRST_PLACES,
        BuiltInAchievementIds.WIN to WINS,
        BuiltInAchievementIds.SELF_DRAW_WIN to SELF_DRAW_WINS,
        BuiltInAchievementIds.DISCARD_WIN to DISCARD_WINS,
        BuiltInAchievementIds.DEAL_IN to DEAL_INS,
        BuiltInAchievementIds.EXHAUSTIVE_DRAW to EXHAUSTIVE_DRAWS,
    )

    /** 全部通用統計，依統計頁面的顯示順序排列。 */
    val ALL: List<String> = listOf(MATCHES_COMPLETED, FIRST_PLACES, WINS, SELF_DRAW_WINS, DISCARD_WINS, DEAL_INS, EXHAUSTIVE_DRAWS)

    /**
     * 取得 [achievementIds] 中各成果要累加的統計；同一個統計在一次交易中只累加一次。
     *
     * @param achievementIds 同一次交易達成的成果。
     * @return 要累加的統計 ID。
     */
    fun statisticsFor(achievementIds: Set<String>): Set<String> = achievementIds.mapNotNullTo(linkedSetOf(), STATISTIC_BY_ACHIEVEMENT::get)
}
