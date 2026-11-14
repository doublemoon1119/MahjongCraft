package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.choiceField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.intField
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 各規則共用的出牌時間、觀戰設定，以及由規則選擇流程提供的選用規則選擇時間。
 *
 * 登記規則時統一加入出牌時間與觀戰欄位；只有規則選擇流程提供預設時間的規則，才加入規則選擇時間欄位。
 * 規則作者不需要另寫共用欄位，也不會因為設定欄位而自動產生遊戲操作。
 */
internal object SharedGameFlowConfigPresentation {
    /** 共用流程設定分類。 */
    const val FLOW = "mahjongcraft:flow"

    /** 玩家規則選擇時間的可設定範圍，供欄位與既有設定檢查共用。 */
    val ruleDecisionTimeRange: ClosedRange<Duration> = 1.seconds..3600.seconds

    /**
     * 將保留的流程分類與欄位附加到規則呈現定義。
     *
     * @param definition 規則提供的原始設定呈現定義。
     * @param ruleDecisionConfig 規則選擇流程提供的預設設定；沒有時不加入規則選擇時間欄位。
     * @return 包含共用流程分類與欄位的標準化定義。
     */
    fun normalize(
        definition: GameConfigPresentationDefinition,
        ruleDecisionConfig: RuleDecisionConfig?,
    ): GameConfigPresentationDefinition {
        require(ruleDecisionConfig == null || ruleDecisionConfig.base in ruleDecisionTimeRange) {
            "Rule decision base time for ${definition.ruleModuleId} must be between 1 and 3600 seconds"
        }
        require(definition.categories.none { it.id == FLOW }) {
            "Config presentation ${definition.ruleModuleId} must not declare reserved category $FLOW"
        }
        val fields = flowFields()
        val ruleDecisionTime = ruleDecisionConfig?.let(::ruleDecisionBaseSecondsField)
        val flowFieldIds = (fields.map { it.id } + RULE_DECISION_BASE_SECONDS_ID).toSet()
        val conflictingFieldId = definition.fields.firstOrNull { it.id in flowFieldIds }?.id
        require(conflictingFieldId == null) {
            "Config presentation ${definition.ruleModuleId} must not declare reserved flow field $conflictingFieldId"
        }
        return definition.copy(
            categories = definition.categories + GameConfigCategoryDefinition(FLOW, SharedGameFlowConfigTranslationKeys.CATEGORY_FLOW),
            fields = buildList {
                addAll(definition.fields)
                addAll(fields)
                ruleDecisionTime?.let(::add)
            },
        )
    }

    /** @return 依畫面順序排列的共用流程欄位。 */
    private fun flowFields(): List<GameConfigFieldDefinition> = listOf(
        baseSecondsField(),
        reserveSecondsField(),
        spectatingField(),
        handVisibilityField(),
    )

    /** @return 主要思考時間欄位定義。 */
    private fun baseSecondsField(): GameConfigFieldDefinition = intField(
        id = "mahjongcraft:base_seconds",
        category = FLOW,
        nameKey = SharedGameFlowConfigTranslationKeys.BASE_SECONDS,
        descriptionKey = SharedGameFlowConfigTranslationKeys.BASE_SECONDS_DESCRIPTION,
        minimum = 0,
        maximum = 3600,
        read = { it.flowConfig.timeControl.base.inWholeSeconds.toInt() },
        update = { config, number -> config.copy(flowConfig = config.flowConfig.copy(timeControl = ActionTimeControl.from(number.seconds, config.flowConfig.timeControl.reserve))) },
    )

    /** @return 保留思考時間欄位定義。 */
    private fun reserveSecondsField(): GameConfigFieldDefinition = intField(
        id = "mahjongcraft:reserve_seconds",
        category = FLOW,
        nameKey = SharedGameFlowConfigTranslationKeys.RESERVE_SECONDS,
        descriptionKey = SharedGameFlowConfigTranslationKeys.RESERVE_SECONDS_DESCRIPTION,
        minimum = 0,
        maximum = 3600,
        read = { it.flowConfig.timeControl.reserve.inWholeSeconds.toInt() },
        update = { config, number -> config.copy(flowConfig = config.flowConfig.copy(timeControl = ActionTimeControl.from(config.flowConfig.timeControl.base, number.seconds))) },
    )

    /**
     * 建立玩家規則選擇的基本思考秒數欄位。
     *
     * @param config 規則登記的預設規則選擇設定。
     * @return 玩家規則選擇時間的編輯欄位。
     */
    private fun ruleDecisionBaseSecondsField(config: RuleDecisionConfig): GameConfigFieldDefinition = intField(
        id = RULE_DECISION_BASE_SECONDS_ID,
        category = FLOW,
        nameKey = SharedGameFlowConfigTranslationKeys.RULE_DECISION_BASE_SECONDS,
        descriptionKey = SharedGameFlowConfigTranslationKeys.RULE_DECISION_BASE_SECONDS_DESCRIPTION,
        minimum = ruleDecisionTimeRange.start.inWholeSeconds.toInt(),
        maximum = ruleDecisionTimeRange.endInclusive.inWholeSeconds.toInt(),
        read = { (it.flowConfig.ruleDecision?.base ?: config.base).inWholeSeconds.toInt() },
        update = { gameConfig, number ->
            gameConfig.copy(
                flowConfig = gameConfig.flowConfig.copy(
                    ruleDecision = RuleDecisionConfig(number.seconds),
                ),
            )
        },
    )

    /** 即使規則沒有選擇流程，也不可由規則自行宣告的規則選擇時間欄位 ID。 */
    private const val RULE_DECISION_BASE_SECONDS_ID = "mahjongcraft:rule_decision_base_seconds"

    /** @return 觀戰政策單選欄位定義。 */
    private fun spectatingField(): GameConfigFieldDefinition = choiceField(
        id = "mahjongcraft:spectating_policy",
        category = FLOW,
        nameKey = SharedGameFlowConfigTranslationKeys.SPECTATING_POLICY,
        descriptionKey = SharedGameFlowConfigTranslationKeys.SPECTATING_POLICY_DESCRIPTION,
        options = listOf(
            BuiltInGameConfigOption("mahjongcraft:enabled", SharedGameFlowConfigTranslationKeys.OPTION_ENABLED, SpectatingPolicy.ENABLED),
            BuiltInGameConfigOption("mahjongcraft:disabled", SharedGameFlowConfigTranslationKeys.OPTION_DISABLED, SpectatingPolicy.DISABLED),
        ),
        read = { it.flowConfig.spectatingPolicy },
        update = { config, policy -> config.copy(flowConfig = config.flowConfig.copy(spectatingPolicy = policy)) },
    )

    /** @return 觀戰時手牌可見度單選欄位定義。 */
    private fun handVisibilityField(): GameConfigFieldDefinition = choiceField(
        id = "mahjongcraft:spectator_hand_visibility",
        category = FLOW,
        nameKey = SharedGameFlowConfigTranslationKeys.SPECTATOR_HAND_VISIBILITY,
        descriptionKey = SharedGameFlowConfigTranslationKeys.SPECTATOR_HAND_VISIBILITY_DESCRIPTION,
        options = listOf(
            BuiltInGameConfigOption("mahjongcraft:revealed", SharedGameFlowConfigTranslationKeys.OPTION_REVEALED, SpectatorHandVisibility.REVEALED),
            BuiltInGameConfigOption("mahjongcraft:hidden", SharedGameFlowConfigTranslationKeys.OPTION_HIDDEN, SpectatorHandVisibility.HIDDEN),
        ),
        read = { it.flowConfig.spectatorHandVisibility },
        update = { config, visibility -> config.copy(flowConfig = config.flowConfig.copy(spectatorHandVisibility = visibility)) },
    ).copy(isEnabled = { it.flowConfig.spectatingPolicy == SpectatingPolicy.ENABLED })
}
