package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.OfflineMatchPause
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.autotest.AutotestBotPlayer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.autotest.AutotestServerTable
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.autotest.AutotestServerTable.Companion.playerId
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * 一位機器人玩家在另一張牌桌開局後離線，本機玩家站在旁邊旁觀。
 *
 * - 局況顯示應加上「所有玩家離線，對局暫停中」，截圖確認文字與紅色的「暫停中」。
 * - 之後把離線暫停時限縮短，確認這一場因此中止，再恢復原本的設定。
 * - 牌桌、房間與離線的機器人留在世界中，所以放在最後一個步驟。
 */
internal object AutotestOfflinePauseStep : AutotestStep {
    override val id: String = "offline-pause"

    override suspend fun run(context: AutotestContext) {
        val koin = GlobalContext.get()
        val table = AutotestServerTable(rule = BuiltInRuleModuleIds.RIICHI, botName = BOT_NAME, position = TABLE_POSITION, seedNumbers = listOf(1))
        val bot = context.onServer { server -> AutotestBotPlayer.join(server, BOT_NAME) }
        val tableId = context.onServer { table.place(bot) }
        if (!context.check("The bot places a table", tableId != null)) return
        val id = checkNotNull(tableId)
        if (!context.check("The table location is registered", context.waitUntil(SYNC_TIMEOUT) { koin.get<TableLocationRegistry>().get(id) != null })) return
        if (!openRoom(context, table, bot, id)) return
        if (!startMatch(context, table, bot, id)) return

        val standing = table.standingPosition
        if (!context.teleport(x = standing.x, y = standing.y, z = standing.z, yaw = 0f, pitch = 30f)) return
        context.onServer { AutotestBotPlayer.leave(bot) }
        val pause = koin.get<OfflineMatchPause>()
        if (!context.check("The match pauses once its seated player is offline", context.waitUntil(SYNC_TIMEOUT) { pause.isPaused(id) })) return
        val shown = context.waitUntil(SYNC_TIMEOUT) { context.roundInfo(id)?.offlinePaused == true }
        if (!context.check("The spectator's round info shows the pause", shown)) return
        val panel = checkNotNull(context.roundInfo(id)).position()
        // 局況顯示永遠面向鏡頭，從南方平視即可看到整塊面板。
        context.screenshotFrom("offline-pause", eye = Vec3(panel.x, panel.y, panel.z + CAMERA_DISTANCE), yaw = CAMERA_YAW, pitch = 0f)

        abortPausedMatch(context, id)
    }

    /**
     * 機器人開房、改成日麻的預設設定並補滿 AI。
     *
     * @param context 步驟環境。
     * @param table 機器人的牌桌。
     * @param bot 機器人玩家。
     * @param tableId 牌桌識別碼。
     * @return 房間已經坐滿時為 true。
     */
    private suspend fun openRoom(context: AutotestContext, table: AutotestServerTable, bot: ServerPlayer, tableId: Uuid): Boolean {
        val koin = GlobalContext.get()
        val rooms = koin.get<RoomRepository>()
        context.onServer { AutotestServerTable.rooms(koin).createFromScreen(tableId, bot) }
        if (!context.check("The bot opens a room", context.waitUntil(SYNC_TIMEOUT) { rooms.getRoom(tableId) != null })) return false
        val config = table.gameConfig(koin, checkNotNull(rooms.getRoom(tableId)).gameConfig)
        context.onServer { AutotestServerTable.rooms(koin).updateConfig(bot, config) }
        if (!context.check("The room uses riichi", context.waitUntil(SYNC_TIMEOUT) { AutotestServerTable.hasConfig(koin, tableId, config) })) return false
        for (count in 2..config.ruleConfig.maxPlayers) {
            context.onServer { AutotestServerTable.rooms(koin).addAi(bot, AutotestServerTable.AI_STRATEGY) }
            if (!context.check("AI player ${count - 1} joins the room", context.waitUntil(SYNC_TIMEOUT) { rooms.getRoom(tableId)?.playerIds?.size == count })) return false
        }
        return true
    }

    /**
     * 機器人坐莊開局，等它的決策時間開始倒數。
     *
     * @param context 步驟環境。
     * @param table 機器人的牌桌。
     * @param bot 機器人玩家。
     * @param tableId 牌桌識別碼。
     * @return 機器人的決策時間開始倒數時為 true。
     */
    private suspend fun startMatch(context: AutotestContext, table: AutotestServerTable, bot: ServerPlayer, tableId: Uuid): Boolean {
        val koin = GlobalContext.get()
        val members = checkNotNull(koin.get<RoomRepository>().getRoom(tableId)).playerIds
        val seatOrder = listOf(bot.playerId()) + (members - bot.playerId())
        context.onServer { AutotestServerTable.rooms(koin).start(player = bot, seatOrder = seatOrder, matchSeed = table.matchSeed(0)) }
        if (!context.check("The bot starts a match", context.waitUntil(SYNC_TIMEOUT) { koin.get<GameRepository>().getGame(tableId) != null })) return false
        val timers = koin.get<GameDecisionTimerManager>()
        val counting = context.waitUntil(MATCH_TIMEOUT) { timers.getStatuses(tableId)[bot.playerId()] != null }
        return context.check("The bot's decision time is counting down", counting)
    }

    /**
     * 把離線暫停時限縮短，確認暫停中的這一場因此中止，再恢復原本的設定。
     *
     * @param context 步驟環境。
     * @param tableId 牌桌識別碼。
     */
    private suspend fun abortPausedMatch(context: AutotestContext, tableId: Uuid) {
        val configState = GlobalContext.get().get<MinecraftServerConfigState>()
        val original = configState.current
        try {
            context.onServer { configState.replace(original.copy(offlinePauseTimeout = 1.seconds)) }
            val aborted = context.waitUntil(SYNC_TIMEOUT) { GlobalContext.get().get<GameRepository>().getGame(tableId) == null }
            context.check("The paused match is aborted once the pause limit has passed", aborted)
        } finally {
            withContext(NonCancellable) { context.onServer { configState.replace(original) } }
        }
    }

    /**
     * 用戶端世界中這張牌桌的局況顯示。
     *
     * @param tableId 牌桌識別碼。
     * @return 局況顯示。用戶端還沒收到時為 null。
     */
    private fun AutotestContext.roundInfo(tableId: Uuid): MahjongRoundInfoEntity? = client.level
        ?.getEntitiesOfClass(MahjongRoundInfoEntity::class.java, AABB(TABLE_POSITION).inflate(ROUND_INFO_SEARCH_RADIUS)) { it.managedTableId == tableId }
        ?.firstOrNull()

    /** 機器人玩家的名稱。 */
    private const val BOT_NAME = "MahjongBot1"

    /** 機器人牌桌的位置，離前面步驟的牌桌夠遠，互不干擾。 */
    private val TABLE_POSITION = BlockPos(24, -60, 6)

    /** 從牌桌控制方塊找局況顯示的範圍。 */
    private const val ROUND_INFO_SEARCH_RADIUS = 8.0

    /** 鏡頭在局況顯示南方的距離。 */
    private const val CAMERA_DISTANCE = 2.5

    /** 鏡頭朝北，與原版相同：180 朝北（z 減少的方向）。 */
    private const val CAMERA_YAW = 180f

    /** 等伺服器處理操作的最長時間。 */
    private val SYNC_TIMEOUT = 30.seconds

    /** 等開局演出結束、輪到機器人的最長時間。 */
    private val MATCH_TIMEOUT = 2.minutes
}
