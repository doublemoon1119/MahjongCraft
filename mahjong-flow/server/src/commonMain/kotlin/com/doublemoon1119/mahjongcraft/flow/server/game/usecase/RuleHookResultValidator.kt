package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 驗證移出手牌動作只改動它被允許改動的資料，以及規則回傳的牌與流局原因符合 Flow 的契約。
 *
 * 不符合時丟出 [RuleContractViolationException]，由既有的中止收尾結束本局，不寫入任何一半的結果。
 */
internal object RuleHookResultValidator {
    /**
     * 確認規則指定要移出的牌，與執行者立牌（含摸牌位）中同一張實體牌完全相同，包括牌面。
     *
     * @param state 套用動作前的權威桌況。
     * @param actorPlayerId 執行動作的玩家。
     * @param action 移出手牌的動作。
     * @param setAsideTile 規則指定要移出的牌。
     * @return 通過驗證的 [setAsideTile]。
     * @throws RuleContractViolationException 執行者沒有持有這張牌，或同一張牌的牌面不同時。
     */
    fun requireHeldSetAsideTile(
        state: TableState,
        actorPlayerId: Uuid,
        action: GameAction.Extension,
        setAsideTile: IdentifiedTile,
    ): IdentifiedTile {
        val actor = state.players.first { it.id == actorPlayerId }
        if (setAsideTile !in actor.hand.standingTiles) {
            throw RuleContractViolationException(
                GameAbortReasonIds.RULE_EXECUTION_FAILED,
                "Tile set-aside action ${action.value.id} chose a tile the actor does not hold: ${setAsideTile.id}",
            )
        }
        return setAsideTile
    }

    /**
     * 取得規則在一炮多響判定為流局時的流局原因；多家和政策選擇流局的規則必須提供原因。
     *
     * @return 規則提供的流局原因。
     * @throws RuleContractViolationException 規則沒有提供原因時。
     */
    fun requireMultiRonAbortiveDrawReason(module: MahjongRuleModule<*>): ExhaustiveDrawReason = module.resolveMultiRonAbortiveDraw()
        ?: throw RuleContractViolationException(
            GameAbortReasonIds.RULE_EXECUTION_FAILED,
            "Rule ${module.id} configured multi-Ron abortive draw without an abortive draw reason",
        )

    /**
     * 確認移出手牌動作的結果：執行者只從立牌（含摸牌位）移出 [setAsideTile]，並把它接在移出區最後；
     * 執行者另外只多一筆 [action] 的動作紀錄與規則資料變化，其他玩家只有規則狀態可以改變，其餘桌況只有對局組件可以改變。
     *
     * @param before 套用動作前的權威桌況。
     * @param after 規則回傳的候選桌況。
     * @param actorPlayerId 執行動作的玩家。
     * @param action 移出手牌的動作。
     * @param setAsideTile 規則指定要移出的牌。
     * @param components 驗證規則組件的檢查器。
     * @return 通過驗證的 [after]。
     * @throws RuleContractViolationException 結果不符合上述限制，或組件不符合契約時。
     */
    fun requireValidTileSetAside(
        before: TableState,
        after: TableState,
        actorPlayerId: Uuid,
        action: GameAction.Extension,
        setAsideTile: IdentifiedTile,
        components: RuleComponentChecker,
    ): TableState {
        fun violation(detail: String): Nothing = throw RuleContractViolationException(
            GameAbortReasonIds.RULE_EXECUTION_FAILED,
            "Tile set-aside action ${action.value.id} returned an unauthorized result: $detail",
        )

        if (after.copy(players = before.players, components = before.components) != before) {
            violation("table data other than players or rule components changed")
        }
        if (after.players.map { it.id } != before.players.map { it.id }) violation("player list changed")
        before.players.zip(after.players).forEach { (previous, updated) ->
            if (previous.id != actorPlayerId) {
                if (updated.copy(components = previous.components) != previous) violation("another player's data other than rule state changed")
                if (updated.components != previous.components) components.requirePlayer(after, updated, "applyTileSetAsideAction")
                return@forEach
            }
            if (setAsideTile !in previous.hand.standingTiles) violation("set-aside tile was not held by the actor")
            val remaining = previous.hand.standingTiles.filterNot { it.id == setAsideTile.id }
            if (updated.hand.standingTiles.sortedBy { it.id.toString() } != remaining.sortedBy { it.id.toString() }) {
                violation("standing tiles changed beyond the set-aside tile")
            }
            if (updated.hand.melds != previous.hand.melds) violation("melds changed")
            if (updated.actionHistory != previous.actionHistory + action) violation("action history must record only the action")
            val previousAreas = previous.tileAreas.areas
            val updatedAreas = updated.tileAreas.areas
            val originalById = previousAreas.associateBy { it.id }
            if (updatedAreas.filter { it.id in originalById }.map { it.id } != previousAreas.map { it.id }) {
                violation("existing tile areas were removed or reordered")
            }
            val changedAreas = updatedAreas.filter { it.tiles != originalById[it.id]?.tiles.orEmpty() }
            if (changedAreas.size != 1) violation("exactly one tile area must receive the removed tile")
            val target = changedAreas.single()
            if (target.tiles != originalById[target.id]?.tiles.orEmpty() + setAsideTile) {
                violation("target tile area must append only the removed tile")
            }
            val expectedAreaCount = previousAreas.size + if (target.id in originalById) 0 else 1
            if (updatedAreas.size != expectedAreaCount) violation("unrelated tile areas changed")
            val unchangedActor = updated.copy(
                hand = previous.hand,
                components = previous.components,
                actionHistory = previous.actionHistory,
                tileAreas = previous.tileAreas,
            )
            if (unchangedActor != previous) violation("actor data other than hand, rule state, tile areas or history changed")
            if (updated.components != previous.components) components.requirePlayer(after, updated, "applyTileSetAsideAction")
        }
        if (after.components != before.components) components.requireTable(after, "applyTileSetAsideAction")
        return after
    }
}
