package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.common.game.model.defaultSubmission
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 一種規則模組的規則選擇流程：在各個時間點決定要不要請玩家做選擇、驗證提交，並在收齊後套用效果。
 *
 * 所有方法都只讀桌況、不保存可變狀態；相同輸入必須得到相同結果。選擇的效果寫進回傳的桌況，例如玩家規則狀態或動作紀錄，
 * 之後的結算直接讀桌況。
 */
interface RuleDecisionResolver {
    /** 此解析器所屬的規則模組 ID。 */
    val ruleModuleId: String

    /**
     * 依實際規則設定決定是否需要玩家規則選擇時間。
     *
     * 相同設定必須回傳相同結果，不得修改狀態或依賴外部可變資料。房間設定與選擇流程會各自查詢，
     * 不保證每局只呼叫一次；只需要自動處理的選擇步驟不需要玩家時間設定。
     *
     * @param ruleConfig 目前對局使用的完整規則設定。
     * @return 任何時間點需要玩家選擇時的預設時間；不需要玩家選擇或只會自動完成時為 null。
     */
    fun ruleDecisionConfig(ruleConfig: MahjongRuleConfig): RuleDecisionConfig? = null

    /**
     * 在 [timing] 決定要不要開啟選擇；不需要時回傳 null，牌局直接接續。
     *
     * @param timing 目前的時間點。
     * @param tableState 這個時間點的桌況，例如開局時已完成發牌、流局前牌山已摸完。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 第一個選擇步驟；不需要選擇時為 null。
     */
    fun begin(
        timing: RuleDecisionTiming,
        tableState: TableState,
        ruleModule: MahjongRuleModule<*>,
    ): RuleDecisionStep?

    /**
     * 驗證通過核心結構檢查的玩家提交。
     *
     * @param tableState 目前桌況。
     * @param decision 目前等待完成的選擇視窗。
     * @param playerId 提交資料的玩家識別碼。
     * @param submission 玩家提交的選擇資料。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 提交是否符合此規則的額外條件。
     */
    fun accepts(
        tableState: TableState,
        decision: PendingRuleDecision,
        playerId: Uuid,
        submission: RuleDecisionSubmission,
        ruleModule: MahjongRuleModule<*>,
    ): Boolean = true

    /**
     * 為真人逾時或無法完成決策的 AI 產生可重現提交。
     *
     * @param tableState 目前桌況。
     * @param decision 目前等待完成的選擇視窗。
     * @param playerId 需要產生預設提交的玩家識別碼。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 可套用的預設提交。
     */
    fun fallbackSubmission(
        tableState: TableState,
        decision: PendingRuleDecision,
        playerId: Uuid,
        ruleModule: MahjongRuleModule<*>,
    ): RuleDecisionSubmission = decision.inputSpecsByPlayerId.getValue(playerId).defaultSubmission()

    /**
     * 收齊提交或遇到無參與者的自動步驟後，套用此步驟並解析下一步。
     *
     * @param tableState 套用選擇結果前的桌況。
     * @param decision 已收齊提交、或沒有參與者的選擇視窗。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 套用結果與後續選擇流程決策。
     */
    fun resolve(
        tableState: TableState,
        decision: PendingRuleDecision,
        ruleModule: MahjongRuleModule<*>,
    ): RuleDecisionResolution
}
