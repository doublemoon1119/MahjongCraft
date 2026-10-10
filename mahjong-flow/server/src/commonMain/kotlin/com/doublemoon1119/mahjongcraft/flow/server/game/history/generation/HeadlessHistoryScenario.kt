package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import kotlinx.coroutines.flow.flow

/**
 * 開發對局生成支援的日麻規則與場長，開局仍沿用正式隨機初始化；座位全部由 AI 坐滿。
 *
 * @property identifier 生成情境的穩定識別碼。
 * @property ruleConfig 對局規則設定；座位數取其人數上限。
 */
enum class HeadlessHistoryScenario(val identifier: String, val ruleConfig: RiichiFamilyRuleConfig) {
    /** 四人東風戰。 */
    RIICHI_EAST("mahjongcraft:riichi_east", RiichiRuleConfig(gameLength = RiichiGameLength.East)),

    /** 四人半莊戰。 */
    RIICHI_HANCHAN("mahjongcraft:riichi_hanchan", RiichiRuleConfig(gameLength = RiichiGameLength.TwoWinds)),

    /** 三人東風戰。 */
    THREE_PLAYER_RIICHI_EAST("mahjongcraft:riichi_three_player_east", ThreePlayerRiichiRuleConfig(gameLength = RiichiGameLength.East)),

    /** 三人半莊戰。 */
    THREE_PLAYER_RIICHI_HANCHAN("mahjongcraft:riichi_three_player_hanchan", ThreePlayerRiichiRuleConfig(gameLength = RiichiGameLength.TwoWinds)),
}
