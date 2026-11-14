package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig

/**
 * 開發對局生成支援的規則與場長，其餘設定為該規則的預設值；開局仍沿用正式隨機初始化，座位全部由 AI 坐滿。
 *
 * @property identifier 生成情境的穩定識別碼。
 * @property ruleConfig 對局規則設定；座位數取其人數上限。
 */
enum class HeadlessHistoryScenario(val identifier: String, val ruleConfig: MahjongRuleConfig) {
    /** 四人東風戰。 */
    RIICHI_EAST("mahjongcraft:riichi/history_scenario/east", RiichiRuleConfig(gameLength = RiichiGameLength.East)),

    /** 四人半莊戰。 */
    RIICHI_HANCHAN("mahjongcraft:riichi/history_scenario/hanchan", RiichiRuleConfig(gameLength = RiichiGameLength.TwoWinds)),

    /** 三人東風戰。 */
    THREE_PLAYER_RIICHI_EAST("mahjongcraft:riichi_three_player/history_scenario/east", ThreePlayerRiichiRuleConfig(gameLength = RiichiGameLength.East)),

    /** 三人半莊戰。 */
    THREE_PLAYER_RIICHI_HANCHAN("mahjongcraft:riichi_three_player/history_scenario/hanchan", ThreePlayerRiichiRuleConfig(gameLength = RiichiGameLength.TwoWinds)),

    /** 台灣麻將東風圈。 */
    TAIWAN_EAST("mahjongcraft:taiwan/history_scenario/east", TaiwanRuleConfig(gameLength = TaiwanGameLength.East)),

    /** 台灣麻將一將（東南西北圈）。 */
    TAIWAN_FOUR_WINDS("mahjongcraft:taiwan/history_scenario/four_winds", TaiwanRuleConfig(gameLength = TaiwanGameLength.FourWinds)),
}
