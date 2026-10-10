package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.defaultSubmission
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 一種規則模組的開局準備流程解析器。 */
interface RoundPreparationResolver {
    /** 此解析器所屬的規則模組 ID。 */
    val ruleModuleId: String

    /**
     * 發牌後建立第一個步驟；不需要準備時回傳 null。
     *
     * @param tableState 已完成發牌的桌況。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 待完成的準備步驟；不需要準備時為 null。
     */
    fun begin(tableState: TableState, ruleModule: MahjongRuleModule<*>): PendingRoundPreparation?

    /**
     * 驗證通過核心結構檢查的玩家提交。
     *
     * @param tableState 目前桌況。
     * @param preparation 目前待完成的準備步驟。
     * @param playerId 提交資料的玩家識別碼。
     * @param submission 玩家提交的準備資料。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 提交是否符合此規則的額外條件。
     */
    fun accepts(
        tableState: TableState,
        preparation: PendingRoundPreparation,
        playerId: Uuid,
        submission: RoundPreparationSubmission,
        ruleModule: MahjongRuleModule<*>,
    ): Boolean = true

    /**
     * 為真人逾時或無法完成決策的 AI 產生可重現提交。
     *
     * @param tableState 目前桌況。
     * @param preparation 目前待完成的準備步驟。
     * @param playerId 需要產生預設提交的玩家識別碼。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 可套用的預設準備提交。
     */
    fun fallbackSubmission(
        tableState: TableState,
        preparation: PendingRoundPreparation,
        playerId: Uuid,
        ruleModule: MahjongRuleModule<*>,
    ): RoundPreparationSubmission = preparation.inputSpecsByPlayerId.getValue(playerId).defaultSubmission()

    /**
     * 收齊提交或遇到無參與者的自動步驟後，套用此步驟並解析下一步。
     *
     * @param tableState 套用準備結果前的桌況。
     * @param preparation 已收集目前步驟提交的準備狀態。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 套用結果與後續準備流程決策。
     */
    fun resolve(
        tableState: TableState,
        preparation: PendingRoundPreparation,
        ruleModule: MahjongRuleModule<*>,
    ): RoundPreparationResolution
}
