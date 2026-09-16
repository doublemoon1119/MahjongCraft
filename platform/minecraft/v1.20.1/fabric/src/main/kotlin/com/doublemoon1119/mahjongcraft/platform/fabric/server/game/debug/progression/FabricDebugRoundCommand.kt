package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.progression

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareExhaustiveDrawUseCase
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPlayerTableScope
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import org.koin.core.annotation.Single

/**
 * 建立 `/mahjongcraft debug round`：把呼叫者目前的對局直接推進到下一局。
 *
 * 目的是讓換局本身可以被反覆驗收，不必真的打完一整局。作法是走正式路徑而不是自訂捷徑：以正式的
 * [DeclareExhaustiveDrawUseCase] 讓本局以流局收尾（因此會寫入正式的回合結算摘要與分數），再登記
 * [PendingGameTransition.AdvanceRound]，由平台心跳跟正常遊玩一樣呼叫連莊／過莊用例。換句話說，實際
 * 開新局的是正式流程，這個指令只負責把本局提前結束。
 *
 * 與正常流局唯一的差別是不播流局結算演出——呼叫端要驗收的是換局本身，不需要先等演出播完。
 *
 * @property gameRepository 讀取桌況並登記待進行的換局。
 * @property declareExhaustiveDrawUseCase 正式的流局結算用例。
 * @property playerTableScope 解析呼叫者目前入座的桌子。
 */
@Single
class FabricDebugRoundCommand(
    private val gameRepository: GameRepository,
    private val declareExhaustiveDrawUseCase: DeclareExhaustiveDrawUseCase,
    private val playerTableScope: DebugPlayerTableScope,
) {
    /** 建立把目前對局推進到下一局的測試指令樹。 */
    fun buildRoundCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(ROUND_SUBCOMMAND)
        .then(literal(NEXT_SUBCOMMAND).executes { context -> advanceToNextRound(context.source) })

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

    private companion object {
        /** 換局測試指令的根 literal。 */
        const val ROUND_SUBCOMMAND: String = "round"

        /** 推進到下一局的 literal。 */
        const val NEXT_SUBCOMMAND: String = "next"
    }
}
