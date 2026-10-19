package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.choiceField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.intField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.translationKey

/**
 * 各規則共用的出牌時間、觀戰設定，以及由核心準備解析器提供的選用準備時間。
 *
 * 登記規則時統一加入出牌時間與觀戰欄位；只有核心解析器提供預設準備時間的規則，才加入準備時間欄位。
 * 規則作者不需要另寫共用欄位，也不會因為設定欄位而自動產生遊戲操作。
 */
internal object SharedGameFlowConfigPresentation {
    /** 共用流程設定分類。 */
    const val FLOW = "mahjongcraft:flow"

    /** 玩家局前準備時間的可設定秒數範圍，供欄位與既有設定檢查共用。 */
    val preparationTimeRange: IntRange = 1..3600

    /**
     * 將保留的流程分類與欄位附加到規則呈現定義。
     *
     * @param definition 規則提供的原始設定呈現定義。
     * @param roundPreparationConfig 核心解析器提供的準備預設設定；沒有時不加入準備時間欄位。
     * @return 包含共用流程分類與欄位的標準化定義。
     */
    fun normalize(
        definition: GameConfigPresentationDefinition,
        roundPreparationConfig: RoundPreparationConfig?,
    ): GameConfigPresentationDefinition {
        require(roundPreparationConfig == null || roundPreparationConfig.baseSeconds in preparationTimeRange) {
            "Round preparation base time for ${definition.ruleModuleId} must be between 1 and 3600 seconds"
        }
        require(definition.categories.none { it.id == FLOW }) {
            "Config presentation ${definition.ruleModuleId} must not declare reserved category $FLOW"
        }
        val fields = flowFields()
        val preparationTime = roundPreparationConfig?.let(::preparationBaseSecondsField)
        val flowFieldIds = (fields.map { it.id } + PREPARATION_BASE_SECONDS_ID).toSet()
        val conflictingFieldId = definition.fields.firstOrNull { it.id in flowFieldIds }?.id
        require(conflictingFieldId == null) {
            "Config presentation ${definition.ruleModuleId} must not declare reserved flow field $conflictingFieldId"
        }
        return definition.copy(
            categories = definition.categories + GameConfigCategoryDefinition(FLOW, translationKey("category.flow")),
            fields = definition.fields + fields + listOfNotNull(preparationTime),
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
        "base_seconds",
        FLOW,
        0,
        3600,
        read = { it.flowConfig.timeControl.baseSeconds },
        update = { config, number -> config.copy(flowConfig = config.flowConfig.copy(timeControl = ActionTimeControl.from(number, config.flowConfig.timeControl.reserveSeconds))) },
    )

    /** @return 保留思考時間欄位定義。 */
    private fun reserveSecondsField(): GameConfigFieldDefinition = intField(
        "reserve_seconds",
        FLOW,
        0,
        3600,
        read = { it.flowConfig.timeControl.reserveSeconds },
        update = { config, number -> config.copy(flowConfig = config.flowConfig.copy(timeControl = ActionTimeControl.from(config.flowConfig.timeControl.baseSeconds, number))) },
    )

    /**
     * 建立玩家準備操作的基本思考秒數欄位。
     *
     * @param config 規則登記的預設準備設定。
     * @return 玩家準備時間的編輯欄位。
     */
    private fun preparationBaseSecondsField(config: RoundPreparationConfig): GameConfigFieldDefinition = intField(
        "preparation_base_seconds",
        FLOW,
        preparationTimeRange.first,
        preparationTimeRange.last,
        read = { it.flowConfig.roundPreparation?.baseSeconds ?: config.baseSeconds },
        update = { gameConfig, number ->
            gameConfig.copy(
                flowConfig = gameConfig.flowConfig.copy(
                    roundPreparation = RoundPreparationConfig(number),
                ),
            )
        },
    )

    /** 即使規則沒有準備流程，也不可由規則自行宣告的準備欄位 ID。 */
    private const val PREPARATION_BASE_SECONDS_ID = "mahjongcraft:preparation_base_seconds"

    /** @return 觀戰政策單選欄位定義。 */
    private fun spectatingField(): GameConfigFieldDefinition = choiceField(
        "spectating_policy",
        FLOW,
        listOf("enabled", "disabled"),
        read = { it.flowConfig.spectatingPolicy.name.lowercase() },
        update = { config, option -> config.copy(flowConfig = config.flowConfig.copy(spectatingPolicy = SpectatingPolicy.valueOf(option.uppercase()))) },
    )

    /** @return 觀戰時手牌可見度單選欄位定義。 */
    private fun handVisibilityField(): GameConfigFieldDefinition = choiceField(
        "spectator_hand_visibility",
        FLOW,
        listOf("revealed", "hidden"),
        read = { it.flowConfig.spectatorHandVisibility.name.lowercase() },
        update = { config, option -> config.copy(flowConfig = config.flowConfig.copy(spectatorHandVisibility = SpectatorHandVisibility.valueOf(option.uppercase()))) },
    ).copy(isEnabled = { it.flowConfig.spectatingPolicy == SpectatingPolicy.ENABLED })
}
