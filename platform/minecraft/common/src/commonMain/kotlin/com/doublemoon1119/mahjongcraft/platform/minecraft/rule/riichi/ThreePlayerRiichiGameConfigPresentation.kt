package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.RULE
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.SCORE
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.boolField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.categories
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.choiceField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.intField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigOption
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition

/**
 * 三人日麻設定畫面的 schema：欄位與四人日麻相同，但赤寶牌只能選 0 或 2 張，且三人對局最多兩家同時榮和，
 * 沒有三響處理。
 */
internal fun threePlayerRiichiGameConfigPresentation(): GameConfigPresentationDefinition = GameConfigPresentationDefinition(
    ruleModuleId = BuiltInRuleModuleIds.RIICHI_THREE_PLAYER,
    descriptionTranslationKey = RiichiGameConfigTranslationKeys.THREE_PLAYER_DESCRIPTION,
    selectable = true,
    defaultRuleConfig = ::ThreePlayerRiichiRuleConfig,
    categories = categories(),
    fields = listOf(
        choiceField(
            id = RiichiGameConfigIds.GAME_LENGTH,
            category = RULE,
            nameKey = RiichiGameConfigTranslationKeys.GAME_LENGTH,
            descriptionKey = RiichiGameConfigTranslationKeys.GAME_LENGTH_DESCRIPTION,
            options = RIICHI_GAME_LENGTH_OPTIONS,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).gameLength },
            update = { config, gameLength -> config.withThreePlayerRiichi { copy(gameLength = gameLength) } },
        ),
        choiceField(
            id = RiichiGameConfigIds.RED_DORA_COUNT,
            category = RULE,
            nameKey = RiichiGameConfigTranslationKeys.RED_DORA_COUNT,
            descriptionKey = RiichiGameConfigTranslationKeys.RED_DORA_COUNT_DESCRIPTION,
            options = listOf(
                BuiltInGameConfigOption(RiichiGameConfigIds.RED_DORA_COUNT_0, RiichiGameConfigTranslationKeys.OPTION_RED_DORA_0, 0),
                BuiltInGameConfigOption(RiichiGameConfigIds.RED_DORA_COUNT_2, RiichiGameConfigTranslationKeys.OPTION_RED_DORA_2, 2),
            ),
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).redDoraCount },
            update = { config, count -> config.withThreePlayerRiichi { copy(redDoraCount = count) } },
        ),
        boolField(
            id = RiichiGameConfigIds.ALLOW_OPEN_TANYAO,
            category = RULE,
            nameKey = RiichiGameConfigTranslationKeys.ALLOW_OPEN_TANYAO,
            descriptionKey = RiichiGameConfigTranslationKeys.ALLOW_OPEN_TANYAO_DESCRIPTION,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).allowOpenTanyao },
            update = { config, enabled -> config.withThreePlayerRiichi { copy(allowOpenTanyao = enabled) } },
        ),
        boolField(
            id = RiichiGameConfigIds.USE_LOCAL_YAKU,
            category = RULE,
            nameKey = RiichiGameConfigTranslationKeys.USE_LOCAL_YAKU,
            descriptionKey = RiichiGameConfigTranslationKeys.USE_LOCAL_YAKU_DESCRIPTION,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).useLocalYaku },
            update = { config, enabled -> config.withThreePlayerRiichi { copy(useLocalYaku = enabled) } },
        ),
        intField(
            id = RiichiGameConfigIds.MINIMUM_WIN_CONSTRAINT,
            category = RULE,
            nameKey = RiichiGameConfigTranslationKeys.MINIMUM_WIN_CONSTRAINT,
            descriptionKey = RiichiGameConfigTranslationKeys.MINIMUM_WIN_CONSTRAINT_DESCRIPTION,
            minimum = 1,
            maximum = 13,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).minimumWinConstraint },
            update = { config, number -> config.withThreePlayerRiichi { copy(minimumWinConstraint = number) } },
        ),
        intField(
            id = RiichiGameConfigIds.INITIAL_SCORE,
            category = SCORE,
            nameKey = RiichiGameConfigTranslationKeys.INITIAL_SCORE,
            descriptionKey = RiichiGameConfigTranslationKeys.INITIAL_SCORE_DESCRIPTION,
            minimum = 0,
            maximum = 10_000_000,
            step = 100,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).scoreConfig.initialScore },
            update = { config, number -> config.withThreePlayerRiichiScore { copy(initialScore = number) } },
        ),
        intField(
            id = RiichiGameConfigIds.BUST_THRESHOLD,
            category = SCORE,
            nameKey = RiichiGameConfigTranslationKeys.BUST_THRESHOLD,
            descriptionKey = RiichiGameConfigTranslationKeys.BUST_THRESHOLD_DESCRIPTION,
            minimum = -1_000_000,
            maximum = 10_000_000,
            step = 100,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).scoreConfig.bustThreshold ?: 0 },
            update = { config, number -> config.withThreePlayerRiichiScore { copy(bustThreshold = number) } },
        ),
        intField(
            id = RiichiGameConfigIds.MIN_POINTS_TO_WIN,
            category = SCORE,
            nameKey = RiichiGameConfigTranslationKeys.MIN_POINTS_TO_WIN,
            descriptionKey = RiichiGameConfigTranslationKeys.MIN_POINTS_TO_WIN_DESCRIPTION,
            minimum = 0,
            maximum = 10_000_000,
            step = 100,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).scoreConfig.minPointsToWin },
            update = { config, number -> config.withThreePlayerRiichiScore { copy(minPointsToWin = number) } },
        ),
        intField(
            id = RiichiGameConfigIds.NOTEN_PENALTY_UNIT,
            category = SCORE,
            nameKey = RiichiGameConfigTranslationKeys.NOTEN_PENALTY_UNIT,
            descriptionKey = RiichiGameConfigTranslationKeys.NOTEN_PENALTY_UNIT_DESCRIPTION,
            minimum = 0,
            maximum = 100_000,
            step = 100,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).scoreConfig.notenPenaltyUnit },
            update = { config, number -> config.withThreePlayerRiichiScore { copy(notenPenaltyUnit = number) } },
        ),
        choiceField(
            id = RiichiGameConfigIds.DOUBLE_RON_RESOLUTION,
            category = RULE,
            nameKey = RiichiGameConfigTranslationKeys.DOUBLE_RON_RESOLUTION,
            descriptionKey = RiichiGameConfigTranslationKeys.DOUBLE_RON_RESOLUTION_DESCRIPTION,
            options = RIICHI_DOUBLE_RON_OPTIONS,
            read = { (it.ruleConfig as ThreePlayerRiichiRuleConfig).multiRonPolicy.doubleRonResolution },
            update = { config, resolution -> config.withThreePlayerRiichi { copy(multiRonPolicy = multiRonPolicy.copy(doubleRonResolution = resolution)) } },
        ),
    ),
)

/** 將 [transform] 套用到三人日麻規則配置並回傳新的遊戲配置。 */
private fun GameConfig.withThreePlayerRiichi(transform: ThreePlayerRiichiRuleConfig.() -> ThreePlayerRiichiRuleConfig): GameConfig = copy(ruleConfig = (ruleConfig as ThreePlayerRiichiRuleConfig).transform())

/** 透過 [withThreePlayerRiichi] 更新三人日麻計分配置。 */
private fun GameConfig.withThreePlayerRiichiScore(transform: RiichiScoreConfig.() -> RiichiScoreConfig): GameConfig = withThreePlayerRiichi { copy(scoreConfig = scoreConfig.transform()) }
