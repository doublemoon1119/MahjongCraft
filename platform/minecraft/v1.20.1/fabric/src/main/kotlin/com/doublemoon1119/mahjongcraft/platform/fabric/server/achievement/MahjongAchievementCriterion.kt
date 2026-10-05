package com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement

import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.AchievementCondition
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.PlayerAchievements
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.StatisticThreshold
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import net.minecraft.advancement.criterion.AbstractCriterion
import net.minecraft.advancement.criterion.AbstractCriterionConditions
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer
import net.minecraft.predicate.entity.LootContextPredicate
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.JsonHelper

/**
 * 「麻將成果」進度條件：玩家在同一次交易中達成指定成果時成立。
 *
 * 條件 JSON：
 * - `achievements`：成果 ID 陣列，必須全部在同一次觸發中成立。
 * - `rule`：選填，限定規則模組 ID。
 * - `statistic` 與 `at_least`：選填，玩家該統計值至少要達到 `at_least`。
 */
class MahjongAchievementCriterion : AbstractCriterion<MahjongAchievementCriterion.Conditions>() {
    override fun getId(): Identifier = ID

    override fun conditionsFromJson(
        obj: JsonObject,
        playerPredicate: LootContextPredicate,
        predicateDeserializer: AdvancementEntityPredicateDeserializer,
    ): Conditions = Conditions(playerPredicate, conditionFromJson(obj))

    /**
     * 以 [achievements] 檢查玩家正在追蹤的條件。
     *
     * @param player 達成成果的在線玩家。
     * @param achievements 同一次交易中這位玩家達成的成果。
     * @param statisticValue 取得玩家目前統計值；應已包含這次交易累加的部分。
     */
    fun trigger(
        player: ServerPlayerEntity,
        achievements: PlayerAchievements,
        statisticValue: (String) -> Int,
    ) {
        trigger(player) { conditions -> conditions.condition.matches(achievements, statisticValue) }
    }

    /**
     * 解析後的條件。
     *
     * @property condition 與 Minecraft 版本無關的條件內容。
     */
    class Conditions(
        playerPredicate: LootContextPredicate,
        val condition: AchievementCondition,
    ) : AbstractCriterionConditions(ID, playerPredicate) {
        override fun toJson(predicateSerializer: AdvancementEntityPredicateSerializer): JsonObject = super.toJson(predicateSerializer).apply {
            add(ACHIEVEMENTS, JsonArray().apply { condition.achievementIds.forEach { add(JsonPrimitive(it)) } })
            condition.ruleModuleId?.let { addProperty(RULE, it) }
            condition.statistic?.let { threshold ->
                addProperty(STATISTIC, threshold.statisticId)
                addProperty(AT_LEAST, threshold.atLeast)
            }
        }
    }

    companion object {
        /** 進度 JSON 引用此條件的 trigger ID。 */
        val ID: Identifier = Identifier(MinecraftModMetadata.MOD_ID, "mahjong_achievement")

        private const val ACHIEVEMENTS = "achievements"
        private const val RULE = "rule"
        private const val STATISTIC = "statistic"
        private const val AT_LEAST = "at_least"

        /**
         * 解析條件 JSON；欄位缺漏或格式錯誤時拋出例外，讓原版載入流程略過該進度並記錄原因。
         *
         * @param obj 進度 JSON 的 `conditions` 物件。
         * @return 條件內容。
         */
        internal fun conditionFromJson(obj: JsonObject): AchievementCondition = AchievementCondition(
            achievementIds = JsonHelper.getArray(obj, ACHIEVEMENTS)
                .mapTo(linkedSetOf()) { element -> JsonHelper.asString(element, ACHIEVEMENTS) },
            ruleModuleId = JsonHelper.getString(obj, RULE, null),
            statistic = JsonHelper.getString(obj, STATISTIC, null)?.let { statisticId ->
                StatisticThreshold(statisticId, JsonHelper.getInt(obj, AT_LEAST))
            },
        )
    }
}
