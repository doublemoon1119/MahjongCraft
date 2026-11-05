package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStep
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStepEvent
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 槓或移出手牌成立、補牌與補牌後自動處理的依序步驟：動作成立（含收付與補牌前公開的牌）一步，補牌一步，之後接自動處理。
 *
 * @param playerId 宣告動作的玩家。
 * @param action 成立的動作。
 * @param followUp 補牌之後的自動處理；沒有補牌時為 null。
 * @param announced 動作的宣告是否已經呈現過，例如等待搶和時先呈現了宣告；已呈現時動作成立那一步不再帶宣告事件。
 * @param drawnState 補牌之後、自動處理之前的桌況；預設為這次結果的桌況。
 * @return 依序排列的步驟。
 */
internal fun SelfDeclarationApplier.Result.Applied.tableSteps(
    playerId: Uuid,
    action: GameAction,
    followUp: DrawFollowUpApplier.Result?,
    announced: Boolean = false,
    drawnState: TableState = tableState,
): List<TableStep> {
    val declared = TableStep(
        declaredState,
        listOfNotNull(
            TableStepEvent.ActionDeclared(playerId, action).takeUnless { announced },
            scoring?.let(TableStepEvent::ActionScored),
        ),
    )
    val drawEvents = listOfNotNull(physicalWallTransitionPhases.takeIf { it.isNotEmpty() }?.let(TableStepEvent::WallRearranged))
    val drawSteps = followUp?.tableSteps(playerId, drawnState, drawEvents) ?: listOf(TableStep(drawnState, drawEvents))
    return listOf(declared) + drawSteps
}
