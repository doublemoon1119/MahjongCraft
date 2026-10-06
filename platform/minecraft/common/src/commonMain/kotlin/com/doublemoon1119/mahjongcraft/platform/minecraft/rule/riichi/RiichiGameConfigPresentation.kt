package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.RULE
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.SCORE
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.boolField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.categories
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.choiceField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.flowFields
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.intField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.translationKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition

/** 日麻設定畫面的 schema：規則與分數欄位可編輯，最後接上共用的流程欄位。 */
internal fun riichiGameConfigPresentation(): GameConfigPresentationDefinition = GameConfigPresentationDefinition(
    ruleModuleId = BuiltInRuleModuleIds.RIICHI,
    descriptionTranslationKey = translationKey("rule.riichi.description"),
    selectable = true,
    defaultRuleConfig = ::RiichiRuleConfig,
    categories = categories(),
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
    ) + flowFields(),
)

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
