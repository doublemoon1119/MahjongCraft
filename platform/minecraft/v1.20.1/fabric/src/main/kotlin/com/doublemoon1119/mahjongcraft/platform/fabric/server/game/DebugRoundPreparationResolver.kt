package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolver
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 開發環境指令建立受控 preparation state 時使用的日麻測試 resolver。 */
class DebugRoundPreparationResolver(override val ruleModuleId: String) : RoundPreparationResolver {

    /** 正常發牌不自動建立測試步驟，確保開發環境的正常對局不受影響。 */
    override fun begin(tableState: TableState, ruleModule: MahjongRuleModule<*>): PendingRoundPreparation? = null

    /** 測試步驟收齊提交後直接完成，不修改規則桌況。 */
    override fun resolve(
        tableState: TableState,
        preparation: PendingRoundPreparation,
        ruleModule: MahjongRuleModule<*>,
    ): RoundPreparationResolution = RoundPreparationResolution(tableState, null)
}
