package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.RULE
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.SCORE
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.boolField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.categories
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.choiceField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.flowFields
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.intField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.translationKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition

/**
 * 三人日麻設定畫面的 schema：欄位與四人日麻相同，但赤寶牌只能選 0 或 2 張，且三人對局最多兩家同時榮和，
 * 沒有三響處理。
 */
internal fun threePlayerRiichiGameConfigPresentation(): GameConfigPresentationDefinition = GameConfigPresentationDefinition(
    ruleModuleId = BuiltInRuleModuleIds.RIICHI_THREE_PLAYER,
    descriptionTranslationKey = translationKey("rule.riichi_three_player.description"),
    selectable = true,
    defaultRuleConfig = ::ThreePlayerRiichiRuleConfig,
    categories = categories(),
    fields = listOf(
        choiceField(
            "game_length",
            RULE,
            listOf("one_game", "east", "two_winds"),
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).gameLength.toOption() },
            update = { config, option -> config.withThreePlayerRiichi { copy(gameLength = option.toRiichiGameLength()) } },
        ),
        choiceField(
            "red_dora_count",
            RULE,
            listOf("red_dora_0", "red_dora_2"),
            read = { "red_dora_${(it.ruleConfig as ThreePlayerRiichiRuleConfig).redDoraCount}" },
            update = { config, option -> config.withThreePlayerRiichi { copy(redDoraCount = option.substringAfterLast('_').toInt()) } },
        ),
        boolField(
            "allow_open_tanyao",
            RULE,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).allowOpenTanyao },
            update = { config, enabled -> config.withThreePlayerRiichi { copy(allowOpenTanyao = enabled) } },
        ),
        boolField(
            "use_local_yaku",
            RULE,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).useLocalYaku },
            update = { config, enabled -> config.withThreePlayerRiichi { copy(useLocalYaku = enabled) } },
        ),
        intField(
            "minimum_win_constraint",
            RULE,
            1,
            13,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).minimumWinConstraint },
            update = { config, number -> config.withThreePlayerRiichi { copy(minimumWinConstraint = number) } },
        ),
        intField(
            "initial_score",
            SCORE,
            0,
            10_000_000,
            step = 100,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).scoreConfig.initialScore },
            update = { config, number -> config.withThreePlayerRiichiScore { copy(initialScore = number) } },
        ),
        intField(
            "bust_threshold",
            SCORE,
            -1_000_000,
            10_000_000,
            100,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).scoreConfig.bustThreshold ?: 0 },
            update = { config, number -> config.withThreePlayerRiichiScore { copy(bustThreshold = number) } },
        ),
        intField(
            "min_points_to_win",
            SCORE,
            0,
            10_000_000,
            step = 100,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).scoreConfig.minPointsToWin },
            update = { config, number -> config.withThreePlayerRiichiScore { copy(minPointsToWin = number) } },
        ),
        intField(
            "noten_penalty_unit",
            SCORE,
            0,
            100_000,
            step = 100,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).scoreConfig.notenPenaltyUnit },
            update = { config, number -> config.withThreePlayerRiichiScore { copy(notenPenaltyUnit = number) } },
        ),
        choiceField(
            "double_ron_resolution",
            RULE,
            listOf("nearest_winner", "all_winners", "abortive_draw"),
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).multiRonPolicy.doubleRonResolution.name.lowercase() },
            update = { config, option ->
                config.withThreePlayerRiichi { copy(multiRonPolicy = multiRonPolicy.copy(doubleRonResolution = RonResolution.valueOf(option.uppercase()))) }
            },
        ),
    ) + flowFields(),
)

private fun GameConfig.withThreePlayerRiichi(transform: ThreePlayerRiichiRuleConfig.() -> ThreePlayerRiichiRuleConfig): GameConfig = copy(ruleConfig = (ruleConfig as ThreePlayerRiichiRuleConfig).transform())

private fun GameConfig.withThreePlayerRiichiScore(transform: RiichiScoreConfig.() -> RiichiScoreConfig): GameConfig = withThreePlayerRiichi { copy(scoreConfig = scoreConfig.transform()) }

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
