package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanScoreConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.RULE
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.SCORE
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.boolField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.categories
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.choiceField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.intField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigOption
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition

/** 台麻設定畫面的 schema；共用流程欄位由 registry 登記時附加。 */
internal fun taiwanGameConfigPresentation(): GameConfigPresentationDefinition = GameConfigPresentationDefinition(
    ruleModuleId = BuiltInRuleModuleIds.TAIWAN,
    descriptionTranslationKey = TaiwanGameConfigTranslationKeys.DESCRIPTION,
    selectable = true,
    defaultRuleConfig = ::TaiwanRuleConfig,
    categories = categories(),
    fields = listOf(
        choiceField(
            id = TaiwanGameConfigIds.GAME_LENGTH,
            category = RULE,
            nameKey = TaiwanGameConfigTranslationKeys.GAME_LENGTH,
            descriptionKey = TaiwanGameConfigTranslationKeys.GAME_LENGTH_DESCRIPTION,
            options = listOf(
                BuiltInGameConfigOption(TaiwanGameConfigIds.GAME_LENGTH_ONE_GAME, TaiwanGameConfigTranslationKeys.OPTION_ONE_GAME, TaiwanGameLength.OneGame),
                BuiltInGameConfigOption(TaiwanGameConfigIds.GAME_LENGTH_EAST, TaiwanGameConfigTranslationKeys.OPTION_EAST, TaiwanGameLength.East),
                BuiltInGameConfigOption(TaiwanGameConfigIds.GAME_LENGTH_TWO_WINDS, TaiwanGameConfigTranslationKeys.OPTION_TWO_WINDS, TaiwanGameLength.TwoWinds),
                BuiltInGameConfigOption(TaiwanGameConfigIds.GAME_LENGTH_FOUR_WINDS, TaiwanGameConfigTranslationKeys.OPTION_FOUR_WINDS, TaiwanGameLength.FourWinds),
            ),
            read = { (it.ruleConfig as TaiwanRuleConfig).gameLength },
            update = { config, gameLength -> config.withTaiwan { copy(gameLength = gameLength) } },
        ),
        boolField(
            id = TaiwanGameConfigIds.USE_FLOWER_TILES,
            category = RULE,
            nameKey = TaiwanGameConfigTranslationKeys.USE_FLOWER_TILES,
            descriptionKey = TaiwanGameConfigTranslationKeys.USE_FLOWER_TILES_DESCRIPTION,
            read = { (it.ruleConfig as TaiwanRuleConfig).useFlowerTiles },
            update = { config, enabled -> config.withTaiwan { copy(useFlowerTiles = enabled) } },
        ),
        boolField(
            id = TaiwanGameConfigIds.FOUR_WINDS_ABORTIVE_DRAW,
            category = RULE,
            nameKey = TaiwanGameConfigTranslationKeys.FOUR_WINDS_ABORTIVE_DRAW,
            descriptionKey = TaiwanGameConfigTranslationKeys.FOUR_WINDS_ABORTIVE_DRAW_DESCRIPTION,
            read = { (it.ruleConfig as TaiwanRuleConfig).fourWindsAbortiveDraw },
            update = { config, enabled -> config.withTaiwan { copy(fourWindsAbortiveDraw = enabled) } },
        ),
        boolField(
            id = TaiwanGameConfigIds.KAN_CLEARS_PASSED_WIN,
            category = RULE,
            nameKey = TaiwanGameConfigTranslationKeys.KAN_CLEARS_PASSED_WIN,
            descriptionKey = TaiwanGameConfigTranslationKeys.KAN_CLEARS_PASSED_WIN_DESCRIPTION,
            read = { (it.ruleConfig as TaiwanRuleConfig).kanClearsPassedWin },
            update = { config, enabled -> config.withTaiwan { copy(kanClearsPassedWin = enabled) } },
        ),
        intField(
            id = TaiwanGameConfigIds.MINIMUM_TAI,
            category = RULE,
            nameKey = TaiwanGameConfigTranslationKeys.MINIMUM_TAI,
            descriptionKey = TaiwanGameConfigTranslationKeys.MINIMUM_TAI_DESCRIPTION,
            minimum = 0,
            maximum = 100,
            read = { (it.ruleConfig as TaiwanRuleConfig).minimumWinConstraint },
            update = { config, number -> config.withTaiwan { copy(minimumWinConstraint = number) } },
        ),
        choiceField(
            id = TaiwanGameConfigIds.DOUBLE_WIN_RESOLUTION,
            category = RULE,
            nameKey = TaiwanGameConfigTranslationKeys.DOUBLE_WIN_RESOLUTION,
            descriptionKey = TaiwanGameConfigTranslationKeys.DOUBLE_WIN_RESOLUTION_DESCRIPTION,
            options = taiwanWinResolutionOptions(
                nearestWinnerId = TaiwanGameConfigIds.DOUBLE_WIN_RESOLUTION_NEAREST_WINNER,
                allWinnersId = TaiwanGameConfigIds.DOUBLE_WIN_RESOLUTION_ALL_WINNERS,
            ),
            read = { (it.ruleConfig as TaiwanRuleConfig).multiRonPolicy.doubleRonResolution },
            update = { config, resolution -> config.withTaiwan { copy(multiRonPolicy = multiRonPolicy.copy(doubleRonResolution = resolution)) } },
        ),
        choiceField(
            id = TaiwanGameConfigIds.TRIPLE_WIN_RESOLUTION,
            category = RULE,
            nameKey = TaiwanGameConfigTranslationKeys.TRIPLE_WIN_RESOLUTION,
            descriptionKey = TaiwanGameConfigTranslationKeys.TRIPLE_WIN_RESOLUTION_DESCRIPTION,
            options = taiwanWinResolutionOptions(
                nearestWinnerId = TaiwanGameConfigIds.TRIPLE_WIN_RESOLUTION_NEAREST_WINNER,
                allWinnersId = TaiwanGameConfigIds.TRIPLE_WIN_RESOLUTION_ALL_WINNERS,
            ),
            read = { (it.ruleConfig as TaiwanRuleConfig).multiRonPolicy.tripleRonResolution },
            update = { config, resolution -> config.withTaiwan { copy(multiRonPolicy = multiRonPolicy.copy(tripleRonResolution = resolution)) } },
        ),
        intField(
            id = TaiwanGameConfigIds.BASE_SCORE,
            category = SCORE,
            nameKey = TaiwanGameConfigTranslationKeys.BASE_SCORE,
            descriptionKey = TaiwanGameConfigTranslationKeys.BASE_SCORE_DESCRIPTION,
            minimum = 0,
            maximum = 1_000_000,
            step = 10,
            read = { (it.ruleConfig as TaiwanRuleConfig).scoreConfig.baseScore },
            update = { config, number -> config.withTaiwanScore { copy(baseScore = number) } },
        ),
        intField(
            id = TaiwanGameConfigIds.POINT_PER_TAI,
            category = SCORE,
            nameKey = TaiwanGameConfigTranslationKeys.POINT_PER_TAI,
            descriptionKey = TaiwanGameConfigTranslationKeys.POINT_PER_TAI_DESCRIPTION,
            minimum = 0,
            maximum = 1_000_000,
            step = 10,
            read = { (it.ruleConfig as TaiwanRuleConfig).scoreConfig.pointPerTai },
            update = { config, number -> config.withTaiwanScore { copy(pointPerTai = number) } },
        ),
        intField(
            id = TaiwanGameConfigIds.INITIAL_SCORE,
            category = SCORE,
            nameKey = TaiwanGameConfigTranslationKeys.INITIAL_SCORE,
            descriptionKey = TaiwanGameConfigTranslationKeys.INITIAL_SCORE_DESCRIPTION,
            minimum = 0,
            maximum = 10_000_000,
            step = 100,
            read = { (it.ruleConfig as TaiwanRuleConfig).scoreConfig.initialScore },
            update = { config, number -> config.withTaiwanScore { copy(initialScore = number) } },
        ),
    ),
)

/** 將 [transform] 套用到台麻規則設定並回傳新的遊戲設定。 */
private fun GameConfig.withTaiwan(transform: TaiwanRuleConfig.() -> TaiwanRuleConfig): GameConfig = copy(ruleConfig = (ruleConfig as TaiwanRuleConfig).transform())

/** 透過 [withTaiwan] 更新台麻計分設定。 */
private fun GameConfig.withTaiwanScore(transform: TaiwanScoreConfig.() -> TaiwanScoreConfig): GameConfig = withTaiwan { copy(scoreConfig = scoreConfig.transform()) }

/**
 * 建立台麻多響欄位的選項；台麻不以多響作為流局。
 *
 * @param nearestWinnerId 最近順位優先選項的完整 ID。
 * @param allWinnersId 全部成立選項的完整 ID。
 * @return 依畫面順序排列的選項。
 */
private fun taiwanWinResolutionOptions(
    nearestWinnerId: String,
    allWinnersId: String,
): List<BuiltInGameConfigOption<RonResolution>> = listOf(
    BuiltInGameConfigOption(nearestWinnerId, TaiwanGameConfigTranslationKeys.OPTION_NEAREST_WINNER, RonResolution.NEAREST_WINNER),
    BuiltInGameConfigOption(allWinnersId, TaiwanGameConfigTranslationKeys.OPTION_ALL_WINNERS, RonResolution.ALL_WINNERS),
)
