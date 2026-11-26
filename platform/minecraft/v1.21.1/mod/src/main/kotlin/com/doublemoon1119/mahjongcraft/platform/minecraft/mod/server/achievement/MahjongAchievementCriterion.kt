package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.achievement

import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.AchievementCondition
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.PlayerAchievements
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.StatisticThreshold
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.resourceId
import com.google.gson.JsonObject
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.JsonOps
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.advancements.critereon.ContextAwarePredicate
import net.minecraft.advancements.critereon.EntityPredicate
import net.minecraft.advancements.critereon.SimpleCriterionTrigger
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.Optional

/**
 * 「麻將成果」進度條件：玩家在同一次交易中達成指定成果時成立。
 *
 * 條件 JSON：
 * - `achievements`：成果 ID 陣列，必須全部在同一次觸發中成立。
 * - `rule`：選填，限定規則模組 ID。
 * - `statistic` 與 `at_least`：選填，玩家該統計值至少要達到 `at_least`。
 */
class MahjongAchievementCriterion : SimpleCriterionTrigger<MahjongAchievementCriterion.Conditions>() {
    override fun codec(): Codec<Conditions> = CONDITIONS_CODEC

    /**
     * 以 [achievements] 檢查玩家正在追蹤的條件。
     *
     * @param player 達成成果的在線玩家。
     * @param achievements 同一次交易中這位玩家達成的成果。
     * @param statisticValue 取得玩家目前統計值。應已包含這次交易累加的部分。
     */
    fun trigger(
        player: ServerPlayer,
        achievements: PlayerAchievements,
        statisticValue: (String) -> Int,
    ) {
        trigger(player) { conditions -> conditions.condition.matches(achievements, statisticValue) }
    }

    /**
     * 解析後的條件。
     *
     * @property playerPredicate 原版進度系統的玩家條件。沒有時為空。
     * @property condition 與 Minecraft 版本無關的條件內容。
     */
    class Conditions(
        val playerPredicate: Optional<ContextAwarePredicate>,
        val condition: AchievementCondition,
    ) : SimpleCriterionTrigger.SimpleInstance {
        override fun player(): Optional<ContextAwarePredicate> = playerPredicate
    }

    /**
     * 條件 JSON 的原始欄位，檢查欄位之間的關係前的樣子。
     *
     * @property achievementIds 成果 ID。
     * @property ruleModuleId 限定的規則模組 ID。
     * @property statisticId 要檢查門檻的統計 ID。
     * @property atLeast 統計值門檻。
     */
    private data class ConditionFields(
        val achievementIds: List<String>,
        val ruleModuleId: Optional<String>,
        val statisticId: Optional<String>,
        val atLeast: Optional<Int>,
    ) {
        /** 轉成條件內容。有統計 ID 卻沒有門檻，或內容不合法時拋出例外。 */
        fun toCondition(): AchievementCondition = AchievementCondition(
            achievementIds = achievementIds.toCollection(linkedSetOf()),
            ruleModuleId = ruleModuleId.orElse(null),
            statistic = statisticId.orElse(null)?.let { id ->
                StatisticThreshold(id, atLeast.orElseThrow { IllegalArgumentException("Missing $AT_LEAST for statistic $id") })
            },
        )
    }

    /** 進度條件識別碼與 JSON 欄位解析。 */
    companion object {
        /** 進度 JSON 引用此條件的 trigger ID。 */
        val ID: ResourceLocation = resourceId(MinecraftModMetadata.MOD_ID, "mahjong_achievement")

        /** 同次觸發必須達成的成果 ID 清單欄位。 */
        private const val ACHIEVEMENTS = "achievements"

        /** 限定規則模組的欄位。 */
        private const val RULE = "rule"

        /** 要檢查門檻的統計 ID 欄位。 */
        private const val STATISTIC = "statistic"

        /** 統計值至少必須達到的門檻欄位。 */
        private const val AT_LEAST = "at_least"

        /** 玩家條件的欄位。 */
        private const val PLAYER = "player"

        /** 條件內容的欄位，與玩家條件放在同一層。 */
        private val CONDITION_CODEC: MapCodec<AchievementCondition> = RecordCodecBuilder.mapCodec<ConditionFields> { instance ->
            instance.group(
                Codec.STRING.listOf().fieldOf(ACHIEVEMENTS).forGetter(ConditionFields::achievementIds),
                Codec.STRING.optionalFieldOf(RULE).forGetter(ConditionFields::ruleModuleId),
                Codec.STRING.optionalFieldOf(STATISTIC).forGetter(ConditionFields::statisticId),
                Codec.INT.optionalFieldOf(AT_LEAST).forGetter(ConditionFields::atLeast),
            ).apply(instance, ::ConditionFields)
        }.flatXmap(
            { fields ->
                runCatching { fields.toCondition() }.fold(
                    onSuccess = { DataResult.success(it) },
                    onFailure = { error -> DataResult.error { error.message ?: error.toString() } },
                )
            },
            { condition ->
                DataResult.success(
                    ConditionFields(
                        achievementIds = condition.achievementIds.toList(),
                        ruleModuleId = Optional.ofNullable(condition.ruleModuleId),
                        statisticId = Optional.ofNullable(condition.statistic?.statisticId),
                        atLeast = Optional.ofNullable(condition.statistic?.atLeast),
                    ),
                )
            },
        )

        /** 整個條件 JSON 的格式。 */
        private val CONDITIONS_CODEC: Codec<Conditions> = RecordCodecBuilder.create { instance ->
            instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf(PLAYER).forGetter(Conditions::playerPredicate),
                CONDITION_CODEC.forGetter(Conditions::condition),
            ).apply(instance, ::Conditions)
        }

        /**
         * 解析條件 JSON。欄位缺漏或格式錯誤時拋出例外，讓原版載入流程略過該進度並記錄原因。
         *
         * @param obj 進度 JSON 的 `conditions` 物件。
         * @return 條件內容。
         */
        internal fun conditionFromJson(obj: JsonObject): AchievementCondition = CONDITION_CODEC.codec().parse(JsonOps.INSTANCE, obj).getOrThrow()
    }
}
