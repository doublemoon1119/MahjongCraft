package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig

/** 登記 Minecraft 內建日麻與唯讀台麻設定 schema。 */
fun GameConfigPresentationRegistry.registerBuiltInGameConfigPresentations() {
    register(riichiPresentation())
    register(taiwanPresentation())
}

private fun riichiPresentation(): GameConfigPresentationDefinition = GameConfigPresentationDefinition(
    ruleModuleId = BuiltInRuleModuleIds.RIICHI,
    descriptionTranslationKey = key("rule.riichi.description"),
    selectable = true,
    defaultRuleConfig = ::RiichiRuleConfig,
    categories = commonCategories(),
    fields = listOf(
        choiceField(
            "game_length",
            RULE,
            listOf("one_game", "east", "two_winds"),
            read = { (it.ruleConfig as RiichiRuleConfig).gameLength.toOption() },
            update = { config, option -> config.withRiichi { copy(gameLength = option.toRiichiGameLength()) } },
        ),
        choiceField(
            "red_dora_count",
            RULE,
            listOf("red_dora_0", "red_dora_3", "red_dora_4"),
            read = { "red_dora_${(it.ruleConfig as RiichiRuleConfig).redDoraCount}" },
            update = { config, option -> config.withRiichi { copy(redDoraCount = option.substringAfterLast('_').toInt()) } },
        ),
        boolField(
            "allow_open_tanyao",
            RULE,
            read = { (it.ruleConfig as RiichiRuleConfig).allowOpenTanyao },
            update = { config, enabled -> config.withRiichi { copy(allowOpenTanyao = enabled) } },
        ),
        boolField(
            "use_local_yaku",
            RULE,
            read = { (it.ruleConfig as RiichiRuleConfig).useLocalYaku },
            update = { config, enabled -> config.withRiichi { copy(useLocalYaku = enabled) } },
        ),
        intField(
            "minimum_win_constraint",
            RULE,
            1,
            13,
            read = { (it.ruleConfig as RiichiRuleConfig).minimumWinConstraint },
            update = { config, number -> config.withRiichi { copy(minimumWinConstraint = number) } },
        ),
        intField(
            "initial_score",
            SCORE,
            0,
            10_000_000,
            step = 100,
            read = { (it.ruleConfig as RiichiRuleConfig).scoreConfig.initialScore },
            update = { config, number -> config.withRiichiScore { copy(initialScore = number) } },
        ),
        intField(
            "bust_threshold",
            SCORE,
            -1_000_000,
            10_000_000,
            100,
            read = { (it.ruleConfig as RiichiRuleConfig).scoreConfig.bustThreshold ?: 0 },
            update = { config, number -> config.withRiichiScore { copy(bustThreshold = number) } },
        ),
        intField(
            "min_points_to_win",
            SCORE,
            0,
            10_000_000,
            step = 100,
            read = { (it.ruleConfig as RiichiRuleConfig).scoreConfig.minPointsToWin },
            update = { config, number -> config.withRiichiScore { copy(minPointsToWin = number) } },
        ),
        intField(
            "noten_penalty_unit",
            SCORE,
            0,
            100_000,
            step = 100,
            read = { (it.ruleConfig as RiichiRuleConfig).scoreConfig.notenPenaltyUnit },
            update = { config, number -> config.withRiichiScore { copy(notenPenaltyUnit = number) } },
        ),
        choiceField(
            "double_ron_resolution",
            RULE,
            ronOptions(),
            read = { (it.ruleConfig as RiichiRuleConfig).multiRonPolicy.doubleRonResolution.toOption() },
            update = { config, option -> config.withRiichi { copy(multiRonPolicy = multiRonPolicy.copy(doubleRonResolution = option.toRonResolution())) } },
        ),
        choiceField(
            "triple_ron_resolution",
            RULE,
            ronOptions(),
            read = { (it.ruleConfig as RiichiRuleConfig).multiRonPolicy.tripleRonResolution.toOption() },
            update = { config, option -> config.withRiichi { copy(multiRonPolicy = multiRonPolicy.copy(tripleRonResolution = option.toRonResolution())) } },
        ),
        flowBaseSecondsField(),
        flowReserveSecondsField(),
        flowSpectatingField(),
        flowHandVisibilityField(),
    ),
)

private fun taiwanPresentation(): GameConfigPresentationDefinition = GameConfigPresentationDefinition(
    ruleModuleId = BuiltInRuleModuleIds.TAIWAN,
    descriptionTranslationKey = key("rule.taiwan.description"),
    selectable = false,
    unavailableReasonTranslationKey = key("rule.taiwan.unavailable"),
    defaultRuleConfig = ::TaiwanRuleConfig,
    categories = commonCategories(),
    fields = listOf(
        readOnlyBoolean("use_flower_tiles", RULE) { (it.ruleConfig as TaiwanRuleConfig).useFlowerTiles },
        readOnlyInt("minimum_win_constraint", RULE) { it.ruleConfig.minimumWinConstraint },
        readOnlyInt("initial_score", SCORE) { it.ruleConfig.scoreConfig.initialScore },
        flowBaseSecondsField(),
        flowReserveSecondsField(),
        flowSpectatingField(),
        flowHandVisibilityField(),
    ),
)

private fun commonCategories() = listOf(
    GameConfigCategoryDefinition(RULE, key("category.rule")),
    GameConfigCategoryDefinition(SCORE, key("category.score")),
    GameConfigCategoryDefinition(FLOW, key("category.flow")),
)

private fun boolField(
    name: String,
    category: String,
    read: (GameConfig) -> Boolean,
    update: (GameConfig, Boolean) -> GameConfig,
) = GameConfigFieldDefinition(
    id(name),
    category,
    key("field.$name"),
    key("field.$name.description"),
    GameConfigEditorSpec.BooleanToggle,
    true,
    read = { GameConfigPresentationValue.BooleanValue(read(it)) },
    update = { config, value -> update(config, (value as GameConfigPresentationValue.BooleanValue).enabled) },
)

private fun intField(
    name: String,
    category: String,
    minimum: Int,
    maximum: Int,
    step: Int = 1,
    read: (GameConfig) -> Int,
    update: (GameConfig, Int) -> GameConfig,
) = GameConfigFieldDefinition(
    id(name),
    category,
    key("field.$name"),
    key("field.$name.description"),
    GameConfigEditorSpec.IntegerInput(minimum, maximum, step),
    true,
    read = { GameConfigPresentationValue.IntegerValue(read(it)) },
    update = { config, value -> update(config, requireNotNull((value as GameConfigPresentationValue.IntegerValue).number)) },
)

private fun choiceField(
    name: String,
    category: String,
    options: List<String>,
    read: (GameConfig) -> String,
    update: (GameConfig, String) -> GameConfig,
) = GameConfigFieldDefinition(
    id(name),
    category,
    key("field.$name"),
    key("field.$name.description"),
    GameConfigEditorSpec.SingleChoice(options.map(::option)),
    true,
    read = { GameConfigPresentationValue.ChoiceValue(option(read(it))) },
    update = { config, value -> update(config, (value as GameConfigPresentationValue.ChoiceValue).optionId.substringAfter(':')) },
)

private fun readOnlyInt(name: String, category: String, read: (GameConfig) -> Int) = GameConfigFieldDefinition(
    id(name),
    category,
    key("field.$name"),
    key("field.$name.description"),
    GameConfigEditorSpec.IntegerInput(Int.MIN_VALUE, Int.MAX_VALUE),
    false,
    read = { GameConfigPresentationValue.IntegerValue(read(it)) },
)

private fun readOnlyBoolean(name: String, category: String, read: (GameConfig) -> Boolean) = GameConfigFieldDefinition(
    id(name),
    category,
    key("field.$name"),
    key("field.$name.description"),
    GameConfigEditorSpec.BooleanToggle,
    false,
    read = { GameConfigPresentationValue.BooleanValue(read(it)) },
)

private fun flowBaseSecondsField() = intField(
    "base_seconds",
    FLOW,
    0,
    3600,
    read = { it.flowConfig.timeControl.baseSeconds },
    update = { config, number -> config.copy(flowConfig = config.flowConfig.copy(timeControl = ActionTimeControl.from(number, config.flowConfig.timeControl.reserveSeconds))) },
)

private fun flowReserveSecondsField() = intField(
    "reserve_seconds",
    FLOW,
    0,
    3600,
    read = { it.flowConfig.timeControl.reserveSeconds },
    update = { config, number -> config.copy(flowConfig = config.flowConfig.copy(timeControl = ActionTimeControl.from(config.flowConfig.timeControl.baseSeconds, number))) },
)

private fun flowSpectatingField() = choiceField(
    "spectating_policy",
    FLOW,
    listOf("enabled", "disabled"),
    read = { it.flowConfig.spectatingPolicy.name.lowercase() },
    update = { config, option -> config.copy(flowConfig = config.flowConfig.copy(spectatingPolicy = SpectatingPolicy.valueOf(option.uppercase()))) },
)

private fun flowHandVisibilityField() = choiceField(
    "spectator_hand_visibility",
    FLOW,
    listOf("revealed", "hidden"),
    read = { it.flowConfig.spectatorHandVisibility.name.lowercase() },
    update = { config, option -> config.copy(flowConfig = config.flowConfig.copy(spectatorHandVisibility = SpectatorHandVisibility.valueOf(option.uppercase()))) },
).copy(isEnabled = { it.flowConfig.spectatingPolicy == SpectatingPolicy.ENABLED })

private fun GameConfig.withRiichi(transform: RiichiRuleConfig.() -> RiichiRuleConfig): GameConfig = copy(ruleConfig = (ruleConfig as RiichiRuleConfig).transform())

private fun GameConfig.withRiichiScore(transform: RiichiScoreConfig.() -> RiichiScoreConfig): GameConfig = withRiichi { copy(scoreConfig = scoreConfig.transform()) }

private fun RiichiGameLength.toOption(): String = when (this) {
    RiichiGameLength.OneGame -> "one_game"
    RiichiGameLength.East -> "east"
    RiichiGameLength.TwoWinds -> "two_winds"
}

private fun String.toRiichiGameLength(): RiichiGameLength = when (this) {
    "one_game" -> RiichiGameLength.OneGame
    "east" -> RiichiGameLength.East
    "two_winds" -> RiichiGameLength.TwoWinds
    else -> error("Unknown Riichi game length option: $this")
}

private fun RonResolution.toOption(): String = name.lowercase()
private fun String.toRonResolution(): RonResolution = RonResolution.valueOf(uppercase())
private fun ronOptions() = listOf("nearest_winner", "all_winners", "abortive_draw")
private fun id(path: String) = "mahjongcraft:$path"
private fun option(path: String) = "mahjongcraft:$path"
private fun key(path: String) = "mahjongcraft.room.config.$path"
private const val RULE = "mahjongcraft:rule"
private const val SCORE = "mahjongcraft:score"
private const val FLOW = "mahjongcraft:flow"
