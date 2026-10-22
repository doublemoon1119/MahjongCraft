package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 驗證規則掛勾與移出手牌動作只改動它們被允許改動的資料。
 *
 * 不符合時丟出 [RuleContractViolationException]，由既有的中止收尾結束本局，不寫入任何一半的結果。
 */
internal object RuleHookResultValidator {
    /**
     * 確認單一玩家的掛勾結果只改動規則狀態。
     *
     * @param before 呼叫掛勾前的玩家。
     * @param after 掛勾回傳的玩家。
     * @param hookName 診斷訊息使用的掛勾名稱。
     * @return 通過驗證的 [after]。
     * @throws RuleContractViolationException 改動規則狀態以外的欄位時。
     */
    fun requireRuleStateOnly(before: MahjongPlayer, after: MahjongPlayer, hookName: String): MahjongPlayer {
        if (after.copy(playerRuleState = before.playerRuleState) != before) {
            throw RuleContractViolationException(
                GameAbortReasonIds.RULE_EXECUTION_FAILED,
                "$hookName changed player data other than its rule state: ${before.id}",
            )
        }
        return after
    }

    /**
     * 確認整桌玩家的掛勾結果只改動各玩家的規則狀態，玩家數量與順序不變。
     *
     * @param before 呼叫掛勾前的玩家列表。
     * @param after 掛勾回傳的玩家列表。
     * @param hookName 診斷訊息使用的掛勾名稱。
     * @return 通過驗證的 [after]。
     * @throws RuleContractViolationException 玩家列表或規則狀態以外的欄位被改動時。
     */
    fun requireRuleStateOnly(before: List<MahjongPlayer>, after: List<MahjongPlayer>, hookName: String): List<MahjongPlayer> {
        if (after.size != before.size) {
            throw RuleContractViolationException(
                GameAbortReasonIds.RULE_EXECUTION_FAILED,
                "$hookName changed the number of players",
            )
        }
        before.zip(after).forEach { (previous, updated) -> requireRuleStateOnly(previous, updated, hookName) }
        return after
    }

    /**
     * 確認移出手牌動作的結果：執行者只從立牌（含摸牌位）移出 [setAsideTile]，並把它接在移出區最後；
     * 執行者另外只多一筆 [action] 的動作紀錄與規則狀態變化，其他玩家只有規則狀態可以改變，其餘桌況只有規則動態狀態可以改變。
     *
     * @param before 套用動作前的權威桌況。
     * @param after 規則回傳的候選桌況。
     * @param actorPlayerId 執行動作的玩家。
     * @param action 移出手牌的動作。
     * @param setAsideTile 規則指定要移出的牌。
     * @param module 對局採用的規則模組。
     * @return 通過驗證的 [after]。
     * @throws RuleContractViolationException 結果不符合上述限制時。
     */
    fun requireValidTileSetAside(
        before: TableState,
        after: TableState,
        actorPlayerId: Uuid,
        action: GameAction.Extension,
        setAsideTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
    ): TableState {
        fun violation(detail: String): Nothing = throw RuleContractViolationException(
            GameAbortReasonIds.RULE_EXECUTION_FAILED,
            "Tile set-aside action ${action.value.id} returned an unauthorized result: $detail",
        )

        if (after.copy(players = before.players, dynamicRuleState = before.dynamicRuleState) != before) {
            violation("table data other than players or dynamic rule state changed")
        }
        if (after.players.map { it.id } != before.players.map { it.id }) violation("player list changed")
        before.players.zip(after.players).forEach { (previous, updated) ->
            if (previous.id != actorPlayerId) {
                requireRuleStateOnly(previous, updated, "applyTileSetAsideAction")
                return@forEach
            }
            val remaining = previous.hand.standingTiles.filterNot { it.id == setAsideTile.id }
            if (remaining.size != previous.hand.standingTiles.size - 1) violation("set-aside tile was not held by the actor")
            if (updated.hand.standingTiles.sortedBy { it.id.toString() } != remaining.sortedBy { it.id.toString() }) {
                violation("standing tiles changed beyond the set-aside tile")
            }
            if (updated.hand.melds != previous.hand.melds) violation("melds changed")
            if (updated.actionHistory != previous.actionHistory + action) violation("action history must record only the action")
            if (module.setAsideTiles(updated) != module.setAsideTiles(previous) + setAsideTile) {
                violation("set-aside tiles must end with the removed tile")
            }
            val unchangedActor = updated.copy(
                hand = previous.hand,
                playerRuleState = previous.playerRuleState,
                actionHistory = previous.actionHistory,
            )
            if (unchangedActor != previous) violation("actor data other than hand, rule state or history changed")
        }
        return after
    }
}
