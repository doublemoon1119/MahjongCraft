package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 解析 debug 指令呼叫者目前入座的牌桌，供需要牌桌身分的 debug 子指令共用。
 *
 * 兩個入口的錯誤語意相同——非玩家執行時回報必須由入座玩家執行，查無牌桌時回報未入座——但動作的執行
 * 執行緒不同，呼叫端必須依自己的需求選擇：
 *
 * - [runOnMain] 的動作在 main thread 執行，適合只讀寫程序內 debug 狀態、不呼叫 suspend service 的指令。
 * - [runSuspending] 的動作在協程中執行，適合需要呼叫 suspend flow service 的指令；只有最後送出回饋才回到
 *   main thread。
 *
 * 兩者都立即回傳 Brigadier 結果碼，實際訊息稍後才非同步送出。
 *
 * @property membershipRepository 查詢玩家目前入座的牌桌。
 * @property scope 執行查詢與動作的協程 scope。
 * @property dispatchers 取得回到 main thread 送出回饋的 dispatcher。
 */
@Single
class DebugPlayerTableScope(
    private val membershipRepository: PlayerMembershipRepository,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) {
    /** 解析呼叫者的牌桌後，在 main thread 執行 [action] 並送出它回傳的訊息。 */
    fun runOnMain(source: ServerCommandSource, action: (tableId: Uuid) -> String): Int {
        val player = requirePlayer(source) ?: return COMMAND_FAILURE
        scope.launch {
            val tableId = membershipRepository.getTableId(player.uuid.toKotlinUuid())
            withContext(dispatchers.main) {
                if (tableId == null) {
                    sendNotSeated(source)
                } else {
                    source.sendFeedback({ Text.literal(action(tableId)) }, true)
                }
            }
        }
        return COMMAND_SUCCESS
    }

    /** 解析呼叫者的牌桌後，在協程中執行 [action]，再回到 main thread 送出它回傳的訊息。 */
    fun runSuspending(
        source: ServerCommandSource,
        action: suspend (tableId: Uuid, playerId: Uuid) -> String,
    ): Int {
        val player = requirePlayer(source) ?: return COMMAND_FAILURE
        scope.launch {
            val playerId = player.uuid.toKotlinUuid()
            val tableId = membershipRepository.getTableId(playerId)
            val message = tableId?.let { action(it, playerId) }
            withContext(dispatchers.main) {
                if (message == null) {
                    sendNotSeated(source)
                } else {
                    source.sendFeedback({ Text.literal(message) }, true)
                }
            }
        }
        return COMMAND_SUCCESS
    }

    /** 取得執行指令的玩家；由非玩家執行時回報錯誤並回傳 `null`。 */
    private fun requirePlayer(source: ServerCommandSource) = source.player ?: run {
        source.sendError(Text.literal("This debug subcommand must be run by a player seated at a table"))
        null
    }

    /** 回報呼叫者目前沒有入座任何牌桌。 */
    private fun sendNotSeated(source: ServerCommandSource) {
        source.sendError(Text.literal("You are not seated at any mahjong table"))
    }

    private companion object {
        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
    }
}
