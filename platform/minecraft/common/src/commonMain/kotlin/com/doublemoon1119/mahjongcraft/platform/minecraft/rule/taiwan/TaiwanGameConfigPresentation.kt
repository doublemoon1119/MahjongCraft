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
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.BuiltInGameConfigFields.translationKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition

/** 台麻設定畫面的 schema；共用流程欄位由 registry 登記時附加。 */
internal fun taiwanGameConfigPresentation(): GameConfigPresentationDefinition = GameConfigPresentationDefinition(
    ruleModuleId = BuiltInRuleModuleIds.TAIWAN,
    descriptionTranslationKey = translationKey("rule.taiwan.description"),
    selectable = true,
    defaultRuleConfig = ::TaiwanRuleConfig,
    categories = categories(),
    fields = listOf(
        choiceField(
            "taiwan_game_length",
            RULE,
            listOf("taiwan_one_game", "taiwan_east", "taiwan_two_winds", "taiwan_four_winds"),
            read = { (it.ruleConfig as TaiwanRuleConfig).gameLength.toOption() },
            update = { config, option -> config.withTaiwan { copy(gameLength = option.toTaiwanGameLength()) } },
        ),
        boolField(
            "use_flower_tiles",
            RULE,
            read = { (it.ruleConfig as TaiwanRuleConfig).useFlowerTiles },
            update = { config, enabled -> config.withTaiwan { copy(useFlowerTiles = enabled) } },
        ),
        boolField(
            "four_winds_abortive_draw",
            RULE,
            read = { (it.ruleConfig as TaiwanRuleConfig).fourWindsAbortiveDraw },
            update = { config, enabled -> config.withTaiwan { copy(fourWindsAbortiveDraw = enabled) } },
        ),
        boolField(
            "kan_clears_passed_win",
            RULE,
            read = { (it.ruleConfig as TaiwanRuleConfig).kanClearsPassedWin },
            update = { config, enabled -> config.withTaiwan { copy(kanClearsPassedWin = enabled) } },
        ),
        intField(
            "taiwan_minimum_tai",
            RULE,
            0,
            100,
            read = { (it.ruleConfig as TaiwanRuleConfig).minimumWinConstraint },
            update = { config, number -> config.withTaiwan { copy(minimumWinConstraint = number) } },
        ),
        choiceField(
            "taiwan_double_win_resolution",
            RULE,
            ronOptions(),
            read = { (it.ruleConfig as TaiwanRuleConfig).multiRonPolicy.doubleRonResolution.toOption() },
            update = { config, option -> config.withTaiwan { copy(multiRonPolicy = multiRonPolicy.copy(doubleRonResolution = option.toRonResolution())) } },
        ),
        choiceField(
            "taiwan_triple_win_resolution",
            RULE,
            ronOptions(),
            read = { (it.ruleConfig as TaiwanRuleConfig).multiRonPolicy.tripleRonResolution.toOption() },
            update = { config, option -> config.withTaiwan { copy(multiRonPolicy = multiRonPolicy.copy(tripleRonResolution = option.toRonResolution())) } },
        ),
        intField(
            "base_score",
            SCORE,
            0,
            1_000_000,
            step = 10,
            read = { (it.ruleConfig as TaiwanRuleConfig).scoreConfig.baseScore },
            update = { config, number -> config.withTaiwanScore { copy(baseScore = number) } },
        ),
        intField(
            "point_per_tai",
            SCORE,
            0,
            1_000_000,
            step = 10,
            read = { (it.ruleConfig as TaiwanRuleConfig).scoreConfig.pointPerTai },
            update = { config, number -> config.withTaiwanScore { copy(pointPerTai = number) } },
        ),
        intField(
            "initial_score",
            SCORE,
            0,
            10_000_000,
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

/** 將台麻賽程轉成設定欄位使用的選項值。 */
private fun TaiwanGameLength.toOption(): String = when (this) {
    TaiwanGameLength.OneGame -> "taiwan_one_game"
    TaiwanGameLength.East -> "taiwan_east"
    TaiwanGameLength.TwoWinds -> "taiwan_two_winds"
    TaiwanGameLength.FourWinds -> "taiwan_four_winds"
}

/** 將設定欄位選項轉回台麻賽程；不支援的選項視為程式錯誤。 */
private fun String.toTaiwanGameLength(): TaiwanGameLength = when (this) {
    "taiwan_one_game" -> TaiwanGameLength.OneGame
    "taiwan_east" -> TaiwanGameLength.East
    "taiwan_two_winds" -> TaiwanGameLength.TwoWinds
    "taiwan_four_winds" -> TaiwanGameLength.FourWinds
    else -> error("Unknown Taiwan game length option: $this")
}

/** 將多響處理方式轉成設定欄位使用的小寫選項值。 */
private fun RonResolution.toOption(): String = when (this) {
    RonResolution.NEAREST_WINNER -> "taiwan_nearest_winner"
    RonResolution.ALL_WINNERS -> "all_winners"
    RonResolution.ABORTIVE_DRAW -> error("Taiwan rules do not support abortive draws for multiple wins")
}

/** 將設定欄位選項轉回多響處理方式。 */
private fun String.toRonResolution(): RonResolution = when (this) {
    "taiwan_nearest_winner" -> RonResolution.NEAREST_WINNER
    "all_winners" -> RonResolution.ALL_WINNERS
    else -> error("Unknown Taiwan multiple-win option: $this")
}

/** 台麻多響設定接受的選項值；台麻不以多響作為流局。 */
private fun ronOptions(): List<String> = listOf("taiwan_nearest_winner", "all_winners")
