package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.state.ClientAutomaticControlStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.state.ClientMahjongStateStore
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 替本機玩家開啟自動操作，讓這一局在沒有人操作的情況下打到結束，截圖並檢查摸打牌的聲音。
 *
 * - 需要前面步驟開好的牌局。
 * - 四家都自動操作時，每一局一定以和牌或流局結束。
 */
internal object AutotestPlayStep : AutotestStep {
    override val id: String = "play"

    override suspend fun run(context: AutotestContext) {
        if (!context.check("A game is ready at the table", context.ensureGameRunning())) return
        val tableId = checkNotNull(AutotestTable.tableId)
        val playerId = checkNotNull(context.client.player).uuid.toKotlinUuid()
        val discardsBefore = localDiscardCount(tableId, playerId)
        val controls = GlobalContext.get().get<ClientAutomaticControlStateStore>()
        for (control in AUTOMATIC_CONTROLS) {
            // 同一時間只能有一個更新要求在等伺服器回覆，等前一個生效再送下一個。
            context.runClientCommand("mahjongcraft_client automatic_control $control on")
            val enabled = context.waitUntil(SYNC_TIMEOUT) { controls.pendingRequest() == null && control in controls.snapshot()?.enabledControlIds.orEmpty() }
            if (!context.check("Automatic control $control is on", enabled)) return
        }

        val discarded = context.waitUntil(ROUND_TIMEOUT) { localDiscardCount(tableId, playerId) > discardsBefore || context.isRoundOver() }
        if (!context.check("The player's turn passes without input", discarded)) return
        context.tableScreenshots("playing")
        val roundOver = context.waitUntil(ROUND_TIMEOUT) { context.isRoundOver() }
        if (!context.check("The round plays to its end", roundOver)) return
        context.tableScreenshots("round-over")

        val sounds = AutotestRuntime.peekSounds().map(AutotestSoundRequest::soundId)
        context.check("Tile sounds play during the game", sounds.any { it.startsWith("mahjongcraft:tile.") }, sounds.filter { it.startsWith("mahjongcraft:") }.distinct().joinToString())
    }

    /**
     * 本機玩家在用戶端牌局狀態中的牌河張數。
     *
     * @param tableId 牌桌識別碼。
     * @param playerId 本機玩家識別碼。
     * @return 牌河張數。不在牌局中時為 0。
     */
    private fun localDiscardCount(tableId: Uuid, playerId: Uuid): Int = GlobalContext.get().get<ClientMahjongStateStore>()
        .gameSnapshot(tableId)?.players?.firstOrNull { it.id == playerId }?.discardPile?.entries?.size ?: 0

    /**
     * 伺服器上這一局是否已經結束：已經結算、正等演出結束後換局或結束整場，或整場已經結束。
     *
     * @return 這一局結束時為 true。
     */
    private suspend fun AutotestContext.isRoundOver(): Boolean {
        val tableId = AutotestTable.tableId ?: return true
        val game = GlobalContext.get().get<GameRepository>().getGame(tableId) ?: return true
        return game.isMatchOver || game.pendingTransition != null
    }

    /** 等伺服器回覆自動操作更新的最長時間。 */
    private val SYNC_TIMEOUT = 10.seconds

    /** 等這一局進行到某個狀態的最長時間。 */
    private val ROUND_TIMEOUT = 5.minutes

    /** 替本機玩家開啟的自動操作：自動摸切、自動和牌、不吃碰槓。 */
    private val AUTOMATIC_CONTROLS = listOf("mahjongcraft:auto_tsumogiri", "mahjongcraft:auto_win", "mahjongcraft:decline_calls")
}
