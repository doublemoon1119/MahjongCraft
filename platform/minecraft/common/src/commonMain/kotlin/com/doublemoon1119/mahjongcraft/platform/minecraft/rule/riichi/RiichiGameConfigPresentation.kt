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
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.intField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigOption
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition

/** 日麻設定畫面的 schema：規則與分數欄位可編輯；共用流程欄位由 registry 登記時附加。 */
internal fun riichiGameConfigPresentation(): GameConfigPresentationDefinition = GameConfigPresentationDefinition(
    ruleModuleId = BuiltInRuleModuleIds.RIICHI,
    descriptionTranslationKey = RiichiGameConfigTranslationKeys.DESCRIPTION,
    selectable = true,
    defaultRuleConfig = ::RiichiRuleConfig,
    categories = categories(),
    fields = listOf(
        choiceField(
            id = RiichiGameConfigIds.GAME_LENGTH,
            category = RULE,
            nameKey = RiichiGameConfigTranslationKeys.GAME_LENGTH,
            descriptionKey = RiichiGameConfigTranslationKeys.GAME_LENGTH_DESCRIPTION,
            options = RIICHI_GAME_LENGTH_OPTIONS,
            read = { (it.ruleConfig as RiichiRuleConfig).gameLength },
            update = { config, gameLength -> config.withRiichi { copy(gameLength = gameLength) } },
        ),
        choiceField(
            id = RiichiGameConfigIds.RED_DORA_COUNT,
            category = RULE,
            nameKey = RiichiGameConfigTranslationKeys.RED_DORA_COUNT,
            descriptionKey = RiichiGameConfigTranslationKeys.RED_DORA_COUNT_DESCRIPTION,
            options = listOf(
                BuiltInGameConfigOption(RiichiGameConfigIds.RED_DORA_COUNT_0, RiichiGameConfigTranslationKeys.OPTION_RED_DORA_0, 0),
                BuiltInGameConfigOption(RiichiGameConfigIds.RED_DORA_COUNT_3, RiichiGameConfigTranslationKeys.OPTION_RED_DORA_3, 3),
                BuiltInGameConfigOption(RiichiGameConfigIds.RED_DORA_COUNT_4, RiichiGameConfigTranslationKeys.OPTION_RED_DORA_4, 4),
            ),
            read = { (it.ruleConfig as RiichiRuleConfig).redDoraCount },
            update = { config, count -> config.withRiichi { copy(redDoraCount = count) } },
        ),
        boolField(
            id = RiichiGameConfigIds.ALLOW_OPEN_TANYAO,
            category = RULE,
            nameKey = RiichiGameConfigTranslationKeys.ALLOW_OPEN_TANYAO,
            descriptionKey = RiichiGameConfigTranslationKeys.ALLOW_OPEN_TANYAO_DESCRIPTION,
            read = { (it.ruleConfig as RiichiRuleConfig).allowOpenTanyao },
            update = { config, enabled -> config.withRiichi { copy(allowOpenTanyao = enabled) } },
        ),
        boolField(
            id = RiichiGameConfigIds.USE_LOCAL_YAKU,
            category = RULE,
            nameKey = RiichiGameConfigTranslationKeys.USE_LOCAL_YAKU,
            descriptionKey = RiichiGameConfigTranslationKeys.USE_LOCAL_YAKU_DESCRIPTION,
            read = { (it.ruleConfig as RiichiRuleConfig).useLocalYaku },
            update = { config, enabled -> config.withRiichi { copy(useLocalYaku = enabled) } },
        ),
        intField(
            id = RiichiGameConfigIds.MINIMUM_WIN_CONSTRAINT,
            category = RULE,
            nameKey = RiichiGameConfigTranslationKeys.MINIMUM_WIN_CONSTRAINT,
            descriptionKey = RiichiGameConfigTranslationKeys.MINIMUM_WIN_CONSTRAINT_DESCRIPTION,
            minimum = 1,
            maximum = 13,
            read = { (it.ruleConfig as RiichiRuleConfig).minimumWinConstraint },
            update = { config, number -> config.withRiichi { copy(minimumWinConstraint = number) } },
        ),
        intField(
            id = RiichiGameConfigIds.INITIAL_SCORE,
            category = SCORE,
            nameKey = RiichiGameConfigTranslationKeys.INITIAL_SCORE,
            descriptionKey = RiichiGameConfigTranslationKeys.INITIAL_SCORE_DESCRIPTION,
            minimum = 0,
            maximum = 10_000_000,
            step = 100,
            read = { (it.ruleConfig as RiichiRuleConfig).scoreConfig.initialScore },
            update = { config, number -> config.withRiichiScore { copy(initialScore = number) } },
        ),
        intField(
            id = RiichiGameConfigIds.BUST_THRESHOLD,
            category = SCORE,
            nameKey = RiichiGameConfigTranslationKeys.BUST_THRESHOLD,
            descriptionKey = RiichiGameConfigTranslationKeys.BUST_THRESHOLD_DESCRIPTION,
            minimum = -1_000_000,
            maximum = 10_000_000,
            step = 100,
            read = { (it.ruleConfig as RiichiRuleConfig).scoreConfig.bustThreshold ?: 0 },
            update = { config, number -> config.withRiichiScore { copy(bustThreshold = number) } },
        ),
        intField(
            id = RiichiGameConfigIds.MIN_POINTS_TO_WIN,
            category = SCORE,
            nameKey = RiichiGameConfigTranslationKeys.MIN_POINTS_TO_WIN,
            descriptionKey = RiichiGameConfigTranslationKeys.MIN_POINTS_TO_WIN_DESCRIPTION,
            minimum = 0,
            maximum = 10_000_000,
            step = 100,
            read = { (it.ruleConfig as RiichiRuleConfig).scoreConfig.minPointsToWin },
            update = { config, number -> config.withRiichiScore { copy(minPointsToWin = number) } },
        ),
        intField(
            id = RiichiGameConfigIds.NOTEN_PENALTY_UNIT,
            category = SCORE,
            nameKey = RiichiGameConfigTranslationKeys.NOTEN_PENALTY_UNIT,
            descriptionKey = RiichiGameConfigTranslationKeys.NOTEN_PENALTY_UNIT_DESCRIPTION,
            minimum = 0,
            maximum = 100_000,
            step = 100,
            read = { (it.ruleConfig as RiichiRuleConfig).scoreConfig.notenPenaltyUnit },
            update = { config, number -> config.withRiichiScore { copy(notenPenaltyUnit = number) } },
        ),
        choiceField(
            id = RiichiGameConfigIds.DOUBLE_RON_RESOLUTION,
            category = RULE,
            nameKey = RiichiGameConfigTranslationKeys.DOUBLE_RON_RESOLUTION,
            descriptionKey = RiichiGameConfigTranslationKeys.DOUBLE_RON_RESOLUTION_DESCRIPTION,
            options = RIICHI_DOUBLE_RON_OPTIONS,
            read = { (it.ruleConfig as RiichiRuleConfig).multiRonPolicy.doubleRonResolution },
            update = { config, resolution -> config.withRiichi { copy(multiRonPolicy = multiRonPolicy.copy(doubleRonResolution = resolution)) } },
        ),
        choiceField(
            id = RiichiGameConfigIds.TRIPLE_RON_RESOLUTION,
            category = RULE,
            nameKey = RiichiGameConfigTranslationKeys.TRIPLE_RON_RESOLUTION,
            descriptionKey = RiichiGameConfigTranslationKeys.TRIPLE_RON_RESOLUTION_DESCRIPTION,
            options = listOf(
                BuiltInGameConfigOption(
                    id = RiichiGameConfigIds.TRIPLE_RON_RESOLUTION_NEAREST_WINNER,
                    translationKey = RiichiGameConfigTranslationKeys.OPTION_NEAREST_WINNER,
                    value = RonResolution.NEAREST_WINNER,
                ),
                BuiltInGameConfigOption(
                    id = RiichiGameConfigIds.TRIPLE_RON_RESOLUTION_ALL_WINNERS,
                    translationKey = RiichiGameConfigTranslationKeys.OPTION_ALL_WINNERS,
                    value = RonResolution.ALL_WINNERS,
                ),
                BuiltInGameConfigOption(
                    id = RiichiGameConfigIds.TRIPLE_RON_RESOLUTION_ABORTIVE_DRAW,
                    translationKey = RiichiGameConfigTranslationKeys.OPTION_ABORTIVE_DRAW,
                    value = RonResolution.ABORTIVE_DRAW,
                ),
            ),
            read = { (it.ruleConfig as RiichiRuleConfig).multiRonPolicy.tripleRonResolution },
            update = { config, resolution -> config.withRiichi { copy(multiRonPolicy = multiRonPolicy.copy(tripleRonResolution = resolution)) } },
        ),
    ),
)

/** 四人與三人日麻共用的局長選項。 */
internal val RIICHI_GAME_LENGTH_OPTIONS: List<BuiltInGameConfigOption<RiichiGameLength>> = listOf(
    BuiltInGameConfigOption(RiichiGameConfigIds.GAME_LENGTH_ONE_GAME, RiichiGameConfigTranslationKeys.OPTION_ONE_GAME, RiichiGameLength.OneGame),
    BuiltInGameConfigOption(RiichiGameConfigIds.GAME_LENGTH_EAST, RiichiGameConfigTranslationKeys.OPTION_EAST, RiichiGameLength.East),
    BuiltInGameConfigOption(RiichiGameConfigIds.GAME_LENGTH_TWO_WINDS, RiichiGameConfigTranslationKeys.OPTION_TWO_WINDS, RiichiGameLength.TwoWinds),
)

/** 四人與三人日麻共用的雙響處理選項。 */
internal val RIICHI_DOUBLE_RON_OPTIONS: List<BuiltInGameConfigOption<RonResolution>> = listOf(
    BuiltInGameConfigOption(
        id = RiichiGameConfigIds.DOUBLE_RON_RESOLUTION_NEAREST_WINNER,
        translationKey = RiichiGameConfigTranslationKeys.OPTION_NEAREST_WINNER,
        value = RonResolution.NEAREST_WINNER,
    ),
    BuiltInGameConfigOption(
        id = RiichiGameConfigIds.DOUBLE_RON_RESOLUTION_ALL_WINNERS,
        translationKey = RiichiGameConfigTranslationKeys.OPTION_ALL_WINNERS,
        value = RonResolution.ALL_WINNERS,
    ),
    BuiltInGameConfigOption(
        id = RiichiGameConfigIds.DOUBLE_RON_RESOLUTION_ABORTIVE_DRAW,
        translationKey = RiichiGameConfigTranslationKeys.OPTION_ABORTIVE_DRAW,
        value = RonResolution.ABORTIVE_DRAW,
    ),
)

/** 將 [transform] 套用到日麻規則配置並回傳新的遊戲配置。 */
private fun GameConfig.withRiichi(transform: RiichiRuleConfig.() -> RiichiRuleConfig): GameConfig = copy(ruleConfig = (ruleConfig as RiichiRuleConfig).transform())

/** 透過 [withRiichi] 更新日麻計分配置。 */
private fun GameConfig.withRiichiScore(transform: RiichiScoreConfig.() -> RiichiScoreConfig): GameConfig = withRiichi { copy(scoreConfig = scoreConfig.transform()) }
