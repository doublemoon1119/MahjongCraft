package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.progression

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareExhaustiveDrawUseCase
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPlayerTableScope
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 建立 `/mahjongcraft debug round`：直接操作呼叫者目前這一局的推進與本場數。
 *
 * - `next`：把對局推進到下一局。以正式的 [DeclareExhaustiveDrawUseCase] 讓本局以流局收尾（因此會寫入
 *   正式的回合結算摘要與分數），再登記 [PendingGameTransition.AdvanceRound]，由平台心跳跟正常遊玩一樣
 *   呼叫連莊／過莊用例——實際開新局的是正式流程，指令只負責把本局提前結束。與正常流局唯一的差別是不播
 *   流局結算演出，呼叫端要驗收的是換局本身，不需要先等演出播完。
 * - `combo <count>`：直接設定本場數，連帶決定莊家桌角的積棒支數。
 *
 * 兩者都是為了讓換局與積棒呈現可以被反覆驗收，不必真的打完一整局或連莊多次。
 *
 * @property gameRepository 讀取桌況、登記待進行的換局並寫入本場數。
 * @property declareExhaustiveDrawUseCase 正式的流局結算用例。
 * @property moduleRegistry 解析對局採用的規則模組，取得供託支數與局況顯示內容。
 * @property snapshotSynchronizer 本場數變更後同步快照給觀察中的玩家。
 * @property presentationPublisher 重新發布積棒、供託與局況顯示。
 * @property playerTableScope 解析呼叫者目前入座的桌子。
 */
@Single
class FabricDebugRoundCommand(
    private val gameRepository: GameRepository,
    private val declareExhaustiveDrawUseCase: DeclareExhaustiveDrawUseCase,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val presentationPublisher: GamePresentationPublisher,
    private val playerTableScope: DebugPlayerTableScope,
) {
    /** 建立直接操作本局推進與本場數的測試指令樹。 */
    fun buildRoundCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(ROUND_SUBCOMMAND)
        .then(literal(NEXT_SUBCOMMAND).executes { context -> advanceToNextRound(context.source) })
        .then(
            literal(COMBO_SUBCOMMAND).then(
                argument(COUNT_ARGUMENT, IntegerArgumentType.integer(MIN_COMBO_COUNT, MAX_COMBO_COUNT))
                    .executes { context -> setComboCount(context.source, IntegerArgumentType.getInteger(context, COUNT_ARGUMENT)) },
            ),
        )

    /** 以流局結束本局，並登記換局讓正式流程接手開下一局。 */
    private fun advanceToNextRound(source: ServerCommandSource): Int = playerTableScope.runSuspending(source) { tableId, _ ->
        gameRepository.getTableState(tableId)
            ?: return@runSuspending "Game not found"
        if (declareExhaustiveDrawUseCase(tableId) !is Outcome.Success) {
            return@runSuspending "This rule does not support an exhaustive draw settlement"
        }
        gameRepository.updateGame(tableId) { game ->
            game?.copy(pendingTransition = PendingGameTransition.AdvanceRound) to Unit
        }
        "Ended the round as an exhaustive draw; the next round starts on the next tick"
    }

    /**
     * 直接把本場數設成指定值，並重新發布受它影響的呈現。
     *
     * 本場數決定莊家桌角的積棒支數；供託堆疊在積棒後方，局況顯示也帶本場數，因此三者一起重新發布，
     * 與正式換局路徑（`AdvanceRoundUseCase`）發布的內容相同。
     */
    private fun setComboCount(source: ServerCommandSource, comboCount: Int): Int = playerTableScope.runSuspending(source) { tableId, _ ->
        val state = gameRepository.updateGame(tableId) { game ->
            val current = game?.tableState ?: return@updateGame game to null
            val updated = current.copy(comboCount = comboCount)
            game.copy(tableState = updated) to updated
        } ?: return@runSuspending "Game not found"
        snapshotSynchronizer.syncAll(tableId)
        publishStickPresentation(tableId, state)
        "Set the combo count to $comboCount"
    }

    /** 重新發布積棒、供託與局況顯示。 */
    private suspend fun publishStickPresentation(tableId: Uuid, state: TableState) {
        val module = moduleRegistry.getModule(state.config)
        val dealerSeatIndex = state.dealerIndex
        presentationPublisher.publishScoringSticksUpdated(tableId, dealerSeatIndex, state.comboCount)
        val declaredSeatIndices = state.players.withIndex()
            .filter { (_, player) -> module.isPlayerInRiichi(player) }
            .mapTo(mutableSetOf()) { (seatIndex, _) -> seatIndex }
        presentationPublisher.publishStickPotUpdated(
            gameId = tableId,
            declaredSeatIndices = declaredSeatIndices,
            dealerSeatIndex = dealerSeatIndex,
            comboStickCount = state.comboCount,
            pooledStickCount = module.getStickPotCount(state) - declaredSeatIndices.size,
        )
        presentationPublisher.publishRoundInfoUpdated(tableId, module.getRoundInfoLines(state))
    }

    private companion object {
        /** 換局測試指令的根 literal。 */
        const val ROUND_SUBCOMMAND: String = "round"

        /** 推進到下一局的 literal。 */
        const val NEXT_SUBCOMMAND: String = "next"

        /** 設定本場數的 literal。 */
        const val COMBO_SUBCOMMAND: String = "combo"

        /** 本場數的參數名稱。 */
        const val COUNT_ARGUMENT: String = "count"

        /** 可設定的最小本場數。 */
        const val MIN_COMBO_COUNT: Int = 0

        /** 可設定的最大本場數；足以涵蓋積棒疊到多層的呈現驗收。 */
        const val MAX_COMBO_COUNT: Int = 99
    }
}
