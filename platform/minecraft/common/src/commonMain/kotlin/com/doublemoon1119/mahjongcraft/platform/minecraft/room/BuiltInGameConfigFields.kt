package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility

/**
 * 內建規則設定畫面共用的分類、欄位建構函式與流程欄位。
 *
 * 欄位 ID 為 `mahjongcraft:<name>`；名稱與說明的 translation key 分別為 `mahjongcraft.room.config.field.<name>`
 * 與 `mahjongcraft.room.config.field.<name>.description`。
 */
internal object BuiltInGameConfigFields {
    /** 規則分類。 */
    const val RULE = "mahjongcraft:rule"

    /** 分數分類。 */
    const val SCORE = "mahjongcraft:score"

    /** 流程分類。 */
    const val FLOW = "mahjongcraft:flow"

    /** 規則、分數與流程三個分類，依畫面順序排列。 */
    fun categories(): List<GameConfigCategoryDefinition> = listOf(
        GameConfigCategoryDefinition(RULE, translationKey("category.rule")),
        GameConfigCategoryDefinition(SCORE, translationKey("category.score")),
        GameConfigCategoryDefinition(FLOW, translationKey("category.flow")),
    )

    /** 設定畫面使用的 translation key。 */
    fun translationKey(path: String): String = "mahjongcraft.room.config.$path"

    /** 可編輯的開關欄位。 */
    fun boolField(
        name: String,
        category: String,
        read: (GameConfig) -> Boolean,
        update: (GameConfig, Boolean) -> GameConfig,
    ): GameConfigFieldDefinition = GameConfigFieldDefinition(
        id(name),
        category,
        translationKey("field.$name"),
        translationKey("field.$name.description"),
        GameConfigEditorSpec.BooleanToggle,
        true,
        read = { GameConfigPresentationValue.BooleanValue(read(it)) },
        update = { config, value -> update(config, (value as GameConfigPresentationValue.BooleanValue).enabled) },
    )

    /** 可編輯的整數欄位。 */
    fun intField(
        name: String,
        category: String,
        minimum: Int,
        maximum: Int,
        step: Int = 1,
        read: (GameConfig) -> Int,
        update: (GameConfig, Int) -> GameConfig,
    ): GameConfigFieldDefinition = GameConfigFieldDefinition(
        id(name),
        category,
        translationKey("field.$name"),
        translationKey("field.$name.description"),
        GameConfigEditorSpec.IntegerInput(minimum, maximum, step),
        true,
        read = { GameConfigPresentationValue.IntegerValue(read(it)) },
        update = { config, value -> update(config, requireNotNull((value as GameConfigPresentationValue.IntegerValue).number)) },
    )

    /** 可編輯的單選欄位；選項 ID 為 `mahjongcraft:<option>`。 */
    fun choiceField(
        name: String,
        category: String,
        options: List<String>,
        read: (GameConfig) -> String,
        update: (GameConfig, String) -> GameConfig,
    ): GameConfigFieldDefinition = GameConfigFieldDefinition(
        id(name),
        category,
        translationKey("field.$name"),
        translationKey("field.$name.description"),
        GameConfigEditorSpec.SingleChoice(options.map(::option)),
        true,
        read = { GameConfigPresentationValue.ChoiceValue(option(read(it))) },
        update = { config, value -> update(config, (value as GameConfigPresentationValue.ChoiceValue).optionId.substringAfter(':')) },
    )

    /** 只顯示、不能編輯的整數欄位。 */
    fun readOnlyInt(
        name: String,
        category: String,
        read: (GameConfig) -> Int,
    ): GameConfigFieldDefinition = GameConfigFieldDefinition(
        id(name),
        category,
        translationKey("field.$name"),
        translationKey("field.$name.description"),
        GameConfigEditorSpec.IntegerInput(Int.MIN_VALUE, Int.MAX_VALUE),
        false,
        read = { GameConfigPresentationValue.IntegerValue(read(it)) },
    )

    /** 只顯示、不能編輯的開關欄位。 */
    fun readOnlyBoolean(
        name: String,
        category: String,
        read: (GameConfig) -> Boolean,
    ): GameConfigFieldDefinition = GameConfigFieldDefinition(
        id(name),
        category,
        translationKey("field.$name"),
        translationKey("field.$name.description"),
        GameConfigEditorSpec.BooleanToggle,
        false,
        read = { GameConfigPresentationValue.BooleanValue(read(it)) },
    )

    /** 所有內建規則共用的思考時間與觀戰欄位，依畫面順序排列。 */
    fun flowFields(): List<GameConfigFieldDefinition> = listOf(
        baseSecondsField(),
        reserveSecondsField(),
        spectatingField(),
        handVisibilityField(),
    )

    private fun baseSecondsField(): GameConfigFieldDefinition = intField(
        "base_seconds",
        FLOW,
        0,
        3600,
        read = { it.flowConfig.timeControl.baseSeconds },
        update = { config, number -> config.copy(flowConfig = config.flowConfig.copy(timeControl = ActionTimeControl.from(number, config.flowConfig.timeControl.reserveSeconds))) },
    )

    private fun reserveSecondsField(): GameConfigFieldDefinition = intField(
        "reserve_seconds",
        FLOW,
        0,
        3600,
        read = { it.flowConfig.timeControl.reserveSeconds },
        update = { config, number -> config.copy(flowConfig = config.flowConfig.copy(timeControl = ActionTimeControl.from(config.flowConfig.timeControl.baseSeconds, number))) },
    )

    private fun spectatingField(): GameConfigFieldDefinition = choiceField(
        "spectating_policy",
        FLOW,
        listOf("enabled", "disabled"),
        read = { it.flowConfig.spectatingPolicy.name.lowercase() },
        update = { config, option -> config.copy(flowConfig = config.flowConfig.copy(spectatingPolicy = SpectatingPolicy.valueOf(option.uppercase()))) },
    )

    private fun handVisibilityField(): GameConfigFieldDefinition = choiceField(
        "spectator_hand_visibility",
        FLOW,
        listOf("revealed", "hidden"),
        read = { it.flowConfig.spectatorHandVisibility.name.lowercase() },
        update = { config, option -> config.copy(flowConfig = config.flowConfig.copy(spectatorHandVisibility = SpectatorHandVisibility.valueOf(option.uppercase()))) },
    ).copy(isEnabled = { it.flowConfig.spectatingPolicy == SpectatingPolicy.ENABLED })

    private fun id(path: String): String = "mahjongcraft:$path"

    private fun option(path: String): String = "mahjongcraft:$path"
}
