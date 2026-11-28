package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.autotest

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.OfflineMatchPause
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestReport
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestReportRetention
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestStepResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.autotestReportMarkdown
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla.serverDirectory
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongLobbyInfoEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongPlayerInfoEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongScoringStickEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongTileSelectionConfirmEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ServerEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.autotest.AutotestServerTable.Companion.playerId
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import org.koin.core.Koin
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.Executor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * 專用伺服器的自動測試，分兩段在兩次開機中執行。
 *
 * 第一段：
 *
 * - 機器人玩家照玩家的流程擺三張牌桌（日麻、台麻、三人麻將），開房並補上 AI。
 * - 第一場開自動操作打完，並確認封存進牌譜。
 * - 由等待室或對局管理的物件指派給不存在的牌桌時，在第一個遊戲刻移除自己；擁有者還在時保留。第一場打完後的等待室與第二場
 *   進行中的對局各檢查一次。
 * - 第二場開局後，等各桌機器人的決策時間開始倒數。
 * - 一位沒有入座的機器人站到牌桌旁旁觀，三位房主機器人離線：確認各桌暫停、局況顯示暫停提示，牌局與決策時間都不前進。房主重新
 *   加入後確認各桌從暫停前的剩餘時間繼續。
 * - 記下各桌的牌局進度後正常關機。
 *
 * 第二段：
 *
 * - 讀回世界，比對牌局進度、房間成員與剩下的決策時間。
 * - 房主機器人還沒加入時，旁觀的機器人確認各桌仍在暫停；房主重新加入後確認各桌繼續。
 * - 房主開自動操作把牌局打完，再正常關機。
 *
 * 其他：
 *
 * - 兩段都檢查關機時間，以及本 mod 沒有記錄錯誤。
 * - 報告寫在伺服器資料夾的 `autotest/<開始時間>/`。第一段寫出後，由第二段接著寫進同一份報告。`autotest/latest.txt` 記錄最近
 *   一次的資料夾名稱。
 * - 伺服器自動測試不分步驟選擇，開發執行帶自動測試設定時一律執行全部步驟。
 *
 * @property koin 取得 mod 的服務。
 */
internal class AutotestServerRunner(private val koin: Koin) {
    /** 記錄自動測試進度的 logger。 */
    private val logger = mahjongCraftLogger(AutotestServerRunner::class)

    /** 這一次開機是第幾段。只有指定了階段才會建立這個執行者。 */
    private val phase = checkNotNull(requestedPhase()) { "The server autotest phase is not set" }

    /** 三張牌桌。 */
    private val tables = AutotestServerTable.all()

    /** 已加入的機器人玩家，以名稱索引。 */
    private val bots = mutableMapOf<String, ServerPlayer>()

    /** 這一段已完成的步驟結果。 */
    private val results = mutableListOf<AutotestStepResult>()

    /** 報告所在的資料夾。 */
    private lateinit var outputDirectory: Path

    /** 前一段已經寫進報告的步驟。 */
    private var earlierResults: List<AutotestStepResult> = emptyList()

    /** 報告的開始時間。 */
    private var startedAt: String = LocalDateTime.now().toString()

    /** 這一段開始時的單調時間。 */
    private val startMark = TimeSource.Monotonic.markNow()

    /** 第二段開機時立刻取得的牌局，在牌局繼續進行前保留讀回的樣子。 */
    private var restoredGames: Map<String, Game?> = emptyMap()

    /** 第二段開機時立刻取得的決策時間。 */
    private var restoredDecision: Pair<Duration, Duration>? = null

    /** 第二段旁觀的機器人，加入前為 null。 */
    private var spectator: ServerPlayer? = null

    /** 第二段房主重新加入前，各桌暫停時房主剩下的決策時間。暫停的檢查沒通過時為 null。 */
    private var pausedAfterRestart: Map<String, Pair<Duration, Duration>>? = null

    /** 要求關機的時間，還沒要求時為 null。 */
    private var stopRequestedAt: TimeMark? = null

    /**
     * 掛上伺服器事件：專用伺服器開好後開始，關機完成後寫出報告。
     *
     * @param events 登記伺服器事件用的事件來源。
     */
    fun register(events: ServerEvents) {
        events.onServerStarted { server ->
            if (server.isDedicatedServer) start(server)
        }
        events.onServerStopped { server ->
            if (server.isDedicatedServer && ::outputDirectory.isInitialized) finish()
        }
    }

    /**
     * 準備報告並在伺服器主執行緒上執行這一段的步驟。
     *
     * @param server 目前伺服器。
     */
    private fun start(server: MinecraftServer) {
        AutotestErrorLog.install()
        val root = serverDirectory(server).resolve("autotest")
        if (phase == PHASE_START) {
            val now = LocalDateTime.now()
            startedAt = now.toString()
            outputDirectory = root.resolve(now.format(AutotestReportRetention.FOLDER_FORMAT)).toAbsolutePath().normalize()
            Files.createDirectories(outputDirectory)
            Files.writeString(root.resolve("latest.txt"), outputDirectory.fileName.toString())
            AutotestReportRetention.prune(root, AutotestReportRetention.keepCount(System.getProperty(AutotestReportRetention.KEEP_PROPERTY)))
        } else {
            outputDirectory = root.resolve(Files.readString(root.resolve("latest.txt")).trim())
            val earlier = JSON.decodeFromString(AutotestReport.serializer(), Files.readString(outputDirectory.resolve(REPORT_JSON)))
            startedAt = earlier.startedAt
            earlierResults = earlier.steps
            // 牌局在第一個遊戲刻就可能繼續進行，開機後立刻取得讀回的樣子。
            captureRestored()
        }
        logger.info("Server autotest {} phase started; report folder {}", phase, outputDirectory)
        CoroutineScope(SupervisorJob() + (server as Executor).asCoroutineDispatcher()).launch {
            if (phase == PHASE_START) runStart(server) else runResume(server)
            logger.info("Server autotest {} phase finished; stopping the server", phase)
            stopRequestedAt = TimeSource.Monotonic.markNow()
            server.halt(false)
        }
    }

    /**
     * 第一段：擺桌開房、打完第一場、第二場打到一半記下狀態。
     *
     * @param server 目前伺服器。
     */
    private suspend fun runStart(server: MinecraftServer) {
        if (!step("bots") { joinBots(server) }) return
        if (!step("tables") { setUpTables() }) return
        if (!step("first-match") { playFirstMatch() }) return
        if (!step("room-owned-cleanup") { checkRoomOwnedCleanup(server) }) return
        if (!step("second-match") { startSecondMatch() }) return
        if (!step("game-owned-cleanup") { checkGameOwnedCleanup(server) }) return
        if (!step("offline-pause") { pauseWhileOffline(server) }) return
        step("interrupt") { interruptSecondMatch() }
    }

    /**
     * 第二段：比對讀回的狀態，機器人重新加入後把牌局打完。
     *
     * @param server 目前伺服器。
     */
    private suspend fun runResume(server: MinecraftServer) {
        readRecord()?.tables?.forEach { record -> tables.first { it.rule == record.rule }.tableId = Uuid.parse(record.tableId) }
        if (!step("restore") { checkRestored() }) return
        if (!step("restart-pause") { checkPausedAfterRestart(server) }) return
        if (!step("rejoin") { joinBots(server) }) return
        if (!step("resume") { checkResumedAfterRestart(server) }) return
        step("finish") { finishMatches() }
    }

    /**
     * 執行一個步驟並記錄結果。步驟拋出的例外記為失敗。
     *
     * @param id 步驟識別碼。
     * @param body 步驟內容。
     * @return 步驟通過時為 true。
     */
    private suspend fun step(id: String, body: suspend AutotestServerContext.() -> Unit): Boolean {
        logger.info("Server autotest step {} started", id)
        val context = AutotestServerContext()
        val mark = TimeSource.Monotonic.markNow()
        val error = try {
            context.body()
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            failure
        }
        val errors = AutotestErrorLog.drain()
        context.check("MahjongCraft logged no errors", errors.isEmpty(), errors.joinToString(" | ").takeIf(String::isNotEmpty))
        val passed = error == null && context.checks.all { it.passed }
        results += result(id, if (passed) AutotestStatus.PASSED else AutotestStatus.FAILED, mark.elapsedNow(), context, error)
        logger.info("Server autotest step {} {}", id, if (passed) "passed" else "failed")
        return passed
    }

    /**
     * 讓三位機器人玩家加入伺服器，並站到各自牌桌的前方。加入時的出生點是隨機的，可能落在牌桌要放的位置。
     *
     * @param server 目前伺服器。
     */
    private fun AutotestServerContext.joinBots(server: MinecraftServer) {
        tables.forEach { table ->
            val bot = AutotestBotPlayer.join(server, table.botName)
            table.approach(bot)
            bots[table.botName] = bot
        }
        check("The bots are online like players", bots.values.all { server.playerList.getPlayer(it.uuid) === it })
    }

    /** 每張牌桌由機器人放下、開房、改成這張牌桌的規則並補滿 AI。 */
    private suspend fun AutotestServerContext.setUpTables() {
        val rooms = koin.get<RoomRepository>()
        for (table in tables) {
            val bot = bots.getValue(table.botName)
            val tableId = table.place(bot)
            if (!check("${table.botName} places a table for ${table.rule}", tableId != null)) return
            val id = checkNotNull(tableId)
            // 放下的牌桌登記位置後，房間服務才找得到它。
            val located = waitUntil(SYNC_TIMEOUT) { koin.get<TableLocationRegistry>().get(id) != null }
            if (!check("The ${table.rule} table location is registered", located)) return
            AutotestServerTable.rooms(koin).createFromScreen(id, bot)
            if (!check("${table.botName} opens a room", waitUntil(SYNC_TIMEOUT) { rooms.getRoom(id)?.playerIds?.contains(bot.playerId()) == true })) return
            val config = table.gameConfig(koin, checkNotNull(rooms.getRoom(id)).gameConfig)
            AutotestServerTable.rooms(koin).updateConfig(bot, config)
            if (!check("The room uses ${table.rule}", waitUntil(SYNC_TIMEOUT) { AutotestServerTable.hasConfig(koin, id, config) })) return
            for (count in 2..config.ruleConfig.maxPlayers) {
                AutotestServerTable.rooms(koin).addAi(bot, AutotestServerTable.AI_STRATEGY)
                if (!check("AI player ${count - 1} joins the ${table.rule} room", waitUntil(SYNC_TIMEOUT) { rooms.getRoom(id)?.playerIds?.size == count })) return
            }
        }
    }

    /** 三張牌桌同時開局，機器人開自動操作，等三場都打完並封存進牌譜。 */
    private suspend fun AutotestServerContext.playFirstMatch() {
        val games = startMatches(matchIndex = 0) ?: return
        for (table in tables) {
            val game = games.getValue(table.rule)
            check("${table.botName} turns on automatic controls", AutotestServerTable.enableAutomaticControls(koin, game, bots.getValue(table.botName)))
        }
        waitForMatchesToFinish(games)
    }

    /**
     * 等待室管理的物件：日麻桌的房間提示指派給不存在的牌桌時移除自己，指派給這張還在等待的牌桌時保留。
     *
     * @param server 目前伺服器。
     */
    private suspend fun AutotestServerContext.checkRoomOwnedCleanup(server: MinecraftServer) {
        val table = tables.first()
        checkOwnership(server, table, ownedEntities = { tableId -> listOf("lobby info" to lobbyInfo(server, table, tableId)) })
    }

    /**
     * 對局管理的物件：指派給不存在的牌桌時移除自己，指派給日麻桌進行中的對局時保留。
     *
     * @param server 目前伺服器。
     */
    private suspend fun AutotestServerContext.checkGameOwnedCleanup(server: MinecraftServer) {
        val table = tables.first()
        checkOwnership(server, table) { tableId ->
            val level = server.overworld()
            listOf(
                "tile" to MahjongTileEntity(world = level).apply { assignToTable(tableId) },
                "point stick" to MahjongScoringStickEntity(world = level).apply { assignToTable(tableId) },
                "round info" to MahjongRoundInfoEntity(world = level).apply { assignToTable(tableId) },
                "player info" to MahjongPlayerInfoEntity(world = level).apply { assignToTable(tableId, table.position) },
                "tile selection confirm button" to MahjongTileSelectionConfirmEntity(world = level).apply {
                    assignToHolder(tableId = tableId, playerId = AutotestBotPlayer.playerIdOf(table.botName), segmentIndex = 0, isPrimary = true)
                },
            )
        }
    }

    /**
     * 在牌桌上方生成兩組相同種類的物件：一組指派給不存在的牌桌，一組指派給 [table]。前者應在第一個遊戲刻移除自己，後者應保留，
     * 檢查後移除，不影響之後的步驟。從存檔載入的遺留物件走同一條檢查。
     *
     * @param server 目前伺服器。
     * @param table 擁有者還在的牌桌。
     * @param ownedEntities 依牌桌識別碼建立各種物件，以名稱標示。
     */
    private suspend fun AutotestServerContext.checkOwnership(
        server: MinecraftServer,
        table: AutotestServerTable,
        ownedEntities: (Uuid) -> List<Pair<String, Entity>>,
    ) {
        val level = server.overworld()
        val orphans = ownedEntities(Uuid.random())
        val owned = ownedEntities(checkNotNull(table.tableId))
        for ((_, entity) in orphans + owned) {
            entity.moveTo(table.position.x + 0.5, table.position.y + CLEANUP_SPAWN_HEIGHT, table.position.z + 0.5, 0f, 0f)
            level.addFreshEntity(entity)
        }
        for ((name, entity) in orphans) {
            check("A leftover $name whose owner is gone removes itself", waitUntil(SYNC_TIMEOUT) { entity.isRemoved })
        }
        for ((name, entity) in owned) {
            // 遊戲刻在呼叫 tick 前遞增 tickCount，到 2 時第一個遊戲刻的檢查已經跑過。
            val ticked = waitUntil(SYNC_TIMEOUT) { entity.isRemoved || entity.tickCount >= 2 }
            check("A $name whose owner still exists stays", ticked && !entity.isRemoved)
            entity.discard()
        }
    }

    /**
     * 建立指派給 [tableId] 的房間提示，位置檢查用 [table] 的控制方塊。
     *
     * @param server 目前伺服器。
     * @param table 控制方塊所在的牌桌。
     * @param tableId 指派的牌桌識別碼。
     * @return 房間提示。
     */
    private fun lobbyInfo(server: MinecraftServer, table: AutotestServerTable, tableId: Uuid): Entity = MahjongLobbyInfoEntity(world = server.overworld()).apply { assignToTable(tableId, table.position) }

    /** 三張牌桌再開一場，等各桌機器人的決策時間都開始倒數。 */
    private suspend fun AutotestServerContext.startSecondMatch() {
        // 機器人都坐莊家位置，各桌第一個要做決定的就是它；牌局停在等機器人決定，記錄後到關機前不會再前進。
        // 其他家坐莊時，可能在機器人輪到之前就讓這一場結束（例如九種九牌），或在記錄之後才做動作（例如拔北）。
        startMatches(matchIndex = 1, botsAsDealers = true) ?: return
        val timers = koin.get<GameDecisionTimerManager>()
        for (table in tables) {
            val tableBotId = bots.getValue(table.botName).playerId()
            val counting = waitUntil(MATCH_TIMEOUT) { timers.getStatuses(checkNotNull(table.tableId))[tableBotId] != null }
            check("${table.botName}'s decision time is counting down", counting)
        }
    }

    /**
     * 旁觀的機器人到場後，三位房主離線：各桌應暫停且局況顯示暫停提示。房主重新加入後各桌應從暫停前的剩餘時間繼續。
     *
     * @param server 目前伺服器。
     */
    private suspend fun AutotestServerContext.pauseWhileOffline(server: MinecraftServer) {
        val watcher = joinSpectator(server)
        tables.forEach { AutotestBotPlayer.leave(bots.getValue(it.botName)) }
        check("The table bots are offline", tables.all { server.playerList.getPlayer(bots.getValue(it.botName).uuid) == null })
        val paused = checkPaused(server)
        joinBots(server)
        if (paused != null) checkResumed(server, paused)
        AutotestBotPlayer.leave(watcher)
    }

    /**
     * 第二段開機後房主機器人還沒加入：旁觀的機器人到場，確認各桌仍在暫停。
     *
     * @param server 目前伺服器。
     */
    private suspend fun AutotestServerContext.checkPausedAfterRestart(server: MinecraftServer) {
        spectator = joinSpectator(server)
        pausedAfterRestart = checkPaused(server)
    }

    /**
     * 房主機器人重新加入後，確認各桌從暫停前的剩餘時間繼續，旁觀的機器人離開。
     *
     * @param server 目前伺服器。
     */
    private suspend fun AutotestServerContext.checkResumedAfterRestart(server: MinecraftServer) {
        val paused = pausedAfterRestart
        if (check("The matches were paused before the bots rejoined", paused != null)) checkResumed(server, checkNotNull(paused))
        spectator?.let(AutotestBotPlayer::leave)
    }

    /**
     * 讓沒有入座的機器人加入，站到中間那張牌桌前旁觀。有玩家在附近時，牌桌一帶才會持續運作，局況顯示才會更新。
     *
     * @param server 目前伺服器。
     * @return 旁觀的機器人。
     */
    private fun AutotestServerContext.joinSpectator(server: MinecraftServer): ServerPlayer {
        val watcher = AutotestBotPlayer.join(server, SPECTATOR_NAME)
        val standing = tables[tables.size / 2].standingPosition
        watcher.teleportTo(watcher.serverLevel(), standing.x, standing.y, standing.z, 0f, 30f)
        check("The spectator bot is online", server.playerList.getPlayer(watcher.uuid) === watcher)
        return watcher
    }

    /**
     * 確認所有房主都離線時各桌暫停：局況顯示暫停提示，累計暫停 [PAUSE_HOLD] 後牌局與房主的決策時間都沒有前進。
     *
     * @param server 目前伺服器。
     * @return 各桌暫停時房主剩下的決策時間，以牌桌規則索引。有檢查沒通過時為 null。
     */
    private suspend fun AutotestServerContext.checkPaused(server: MinecraftServer): Map<String, Pair<Duration, Duration>>? {
        val pause = koin.get<OfflineMatchPause>()
        val timers = koin.get<GameDecisionTimerManager>()
        val gameRepository = koin.get<GameRepository>()
        for (table in tables) {
            val tableId = checkNotNull(table.tableId)
            val botId = AutotestBotPlayer.playerIdOf(table.botName)
            // 暫停時計時器停止，剩下的時間改由牌局保存。
            val stopped = waitUntil(SYNC_TIMEOUT) { pause.isPaused(tableId) && timers.getStatuses(tableId)[botId] == null }
            if (!check("The ${table.rule} match pauses while every seated player is offline", stopped)) return null
            check("The ${table.rule} round info shows the pause", waitUntil(SYNC_TIMEOUT) { roundInfo(server, table)?.offlinePaused == true })
        }
        val before = tables.associate { table -> table.rule to pausedState(table) }
        val held = waitUntil(SYNC_TIMEOUT) {
            tables.all { table ->
                val start = checkNotNull(before[table.rule]).pauseElapsed
                (gameRepository.getGame(checkNotNull(table.tableId))?.offlinePauseElapsed ?: Duration.ZERO) >= start + PAUSE_HOLD
            }
        }
        if (!check("Every match stays paused for $PAUSE_HOLD", held)) return null
        var unchanged = true
        for (table in tables) {
            val earlier = checkNotNull(before[table.rule])
            val later = pausedState(table)
            val sameProgress = later.fingerprint == earlier.fingerprint
            check("The ${table.rule} match does not move while paused", sameProgress, "before ${earlier.fingerprint}, after ${later.fingerprint}")
            val sameTime = later.time != null && later.time == earlier.time
            check("${table.botName}'s decision time does not run down while paused", sameTime, "before ${earlier.time}, after ${later.time}")
            unchanged = unchanged && sameProgress && sameTime
        }
        return if (unchanged) before.mapValues { checkNotNull(it.value.time) } else null
    }

    /**
     * 確認房主重新加入後各桌恢復：不再暫停、局況顯示移除暫停提示，房主的決策時間從暫停前剩下的時間繼續倒數。
     *
     * @param server 目前伺服器。
     * @param paused 各桌暫停時房主剩下的決策時間，以牌桌規則索引。
     */
    private suspend fun AutotestServerContext.checkResumed(server: MinecraftServer, paused: Map<String, Pair<Duration, Duration>>) {
        val pause = koin.get<OfflineMatchPause>()
        val timers = koin.get<GameDecisionTimerManager>()
        for (table in tables) {
            val tableId = checkNotNull(table.tableId)
            val botId = AutotestBotPlayer.playerIdOf(table.botName)
            val resumed = waitUntil(SYNC_TIMEOUT) { !pause.isPaused(tableId) && timers.getStatuses(tableId)[botId] != null }
            if (!check("The ${table.rule} match resumes after its player rejoins", resumed)) continue
            check("The ${table.rule} round info hides the pause", waitUntil(SYNC_TIMEOUT) { roundInfo(server, table)?.offlinePaused == false })
            val base = checkNotNull(paused[table.rule]).first
            val now = timers.getStatuses(tableId)[botId]?.time?.baseRemaining
            check(
                name = "${table.botName}'s decision time continues from where it paused",
                passed = now != null && now in (base - TIME_TOLERANCE)..base,
                detail = "paused at $base, now $now",
            )
        }
    }

    /**
     * 一張牌桌目前的牌局進度、房主剩下的決策時間與累計暫停時間。
     *
     * @param table 牌桌。
     * @return 目前的狀態。
     */
    private suspend fun pausedState(table: AutotestServerTable): AutotestPausedState {
        val tableId = checkNotNull(table.tableId)
        val game = koin.get<GameRepository>().getGame(tableId)
        return AutotestPausedState(
            fingerprint = game?.let(AutotestServerTable::fingerprint),
            time = decisionTime(tableId, AutotestBotPlayer.playerIdOf(table.botName)),
            pauseElapsed = game?.offlinePauseElapsed ?: Duration.ZERO,
        )
    }

    /**
     * 玩家剩下的決策時間。計時中取計時器的時間，計時器停止時取牌局保存的時間。
     *
     * @param tableId 牌桌識別碼。
     * @param playerId 玩家識別碼。
     * @return 剩下的基本時間與保留時間。都查不到時為 null。
     */
    private suspend fun decisionTime(tableId: Uuid, playerId: Uuid): Pair<Duration, Duration>? {
        val game = koin.get<GameRepository>().getGame(tableId)
        val status = koin.get<GameDecisionTimerManager>().getStatuses(tableId)[playerId]?.time
        val base = status?.baseRemaining ?: game?.interruptedBaseByPlayerId?.get(playerId)
        val reserve = status?.reserveRemaining ?: game?.remainingReserveByPlayerId?.get(playerId)
        return if (base != null && reserve != null) base to reserve else null
    }

    /**
     * 找出牌桌的局況顯示。
     *
     * @param server 目前伺服器。
     * @param table 牌桌。
     * @return 局況顯示。找不到時為 null。
     */
    private fun roundInfo(server: MinecraftServer, table: AutotestServerTable): MahjongRoundInfoEntity? = server.overworld()
        .getEntitiesOfClass(MahjongRoundInfoEntity::class.java, AABB(table.position).inflate(ROUND_INFO_SEARCH_RADIUS)) { it.managedTableId == table.tableId }
        .firstOrNull()

    /** 記下各桌的牌局進度與日麻桌機器人剩下的決策時間。 */
    private suspend fun AutotestServerContext.interruptSecondMatch() {
        val timers = koin.get<GameDecisionTimerManager>()
        val riichi = tables.first()
        val tableId = checkNotNull(riichi.tableId)
        val botId = bots.getValue(riichi.botName).playerId()
        val status = checkNotNull(timers.getStatuses(tableId)[botId]).time
        val gameRepository = koin.get<GameRepository>()
        val records = tables.map { table ->
            val game = checkNotNull(gameRepository.getGame(checkNotNull(table.tableId)))
            AutotestServerTableRecord(
                rule = table.rule,
                tableId = game.id.toString(),
                botName = table.botName,
                matchId = game.matchId.toString(),
                roomPlayerIds = game.roomPlayerIds.map { it.toString() },
                fingerprint = AutotestServerTable.fingerprint(game),
            )
        }
        check("Every table has a match in progress", records.size == tables.size)
        val record = AutotestServerRestartRecord(
            tables = records,
            decision = AutotestServerDecisionRecord(
                tableId = tableId.toString(),
                playerId = botId.toString(),
                baseRemainingMillis = status.baseRemaining.inWholeMilliseconds,
                reserveRemainingMillis = status.reserveRemaining.inWholeMilliseconds,
            ),
        )
        Files.writeString(outputDirectory.resolve(RESTART_JSON), JSON.encodeToString(AutotestServerRestartRecord.serializer(), record))
    }

    /** 第二段開機時立刻取得讀回的牌局與決策時間。 */
    private fun captureRestored() {
        val record = readRecord() ?: return
        runBlocking {
            val gameRepository = koin.get<GameRepository>()
            restoredGames = record.tables.associate { it.tableId to gameRepository.getGame(Uuid.parse(it.tableId)) }
            val decision = record.decision
            restoredDecision = decisionTime(Uuid.parse(decision.tableId), Uuid.parse(decision.playerId))
        }
    }

    /** 比對讀回的牌局、房間成員與決策時間。 */
    private fun AutotestServerContext.checkRestored() {
        val record = readRecord()
        if (!check("The first phase left its records", record != null)) return
        for (table in checkNotNull(record).tables) {
            val game = restoredGames[table.tableId]
            if (!check("The ${table.rule} match is restored", game != null && game.matchId.toString() == table.matchId)) continue
            val restored = checkNotNull(game)
            check("The ${table.rule} room members are restored", restored.roomPlayerIds.map { it.toString() } == table.roomPlayerIds)
            val fingerprint = AutotestServerTable.fingerprint(restored)
            val same = fingerprint == table.fingerprint
            check("The ${table.rule} match progress is restored", same, "before ${table.fingerprint}, after $fingerprint".takeUnless { same })
        }
        val decision = record.decision
        val restored = restoredDecision
        val base = decision.baseRemainingMillis.milliseconds
        val reserve = decision.reserveRemainingMillis.milliseconds
        check(
            name = "The bot's remaining decision time is restored",
            passed = restored != null && restored.first in (base - TIME_TOLERANCE)..base && restored.second in (reserve - TIME_TOLERANCE)..reserve,
            detail = "before base $base reserve $reserve, after ${restored?.first} ${restored?.second}",
        )
    }

    /** 機器人重新開自動操作，等三場都打完並封存進牌譜。 */
    private suspend fun AutotestServerContext.finishMatches() {
        val gameRepository = koin.get<GameRepository>()
        val games = tables.associate { table -> table.rule to table.tableId?.let { gameRepository.getGame(it) } }
        if (!check("Every match is still in progress", games.values.all { it != null })) return
        for (table in tables) {
            val game = checkNotNull(games[table.rule])
            check("${table.botName} turns on automatic controls again", AutotestServerTable.enableAutomaticControls(koin, game, bots.getValue(table.botName)))
        }
        waitForMatchesToFinish(games.mapValues { checkNotNull(it.value) })
    }

    /**
     * 三張牌桌由機器人照玩家流程開局，等牌局都開始。
     *
     * @param matchIndex 各桌的第幾場對局，從 0 起算，決定這一場使用的固定種子。
     * @param botsAsDealers 為 true 時各桌的機器人坐莊家位置，其他人照房間順序。為 false 時全部照種子排座。
     * @return 各規則的牌局。有牌桌沒有開局時為 null。
     */
    private suspend fun AutotestServerContext.startMatches(matchIndex: Int, botsAsDealers: Boolean = false): Map<String, Game>? {
        val gameRepository = koin.get<GameRepository>()
        val rooms = koin.get<RoomRepository>()
        for (table in tables) {
            val bot = bots.getValue(table.botName)
            table.approach(bot)
            val seatOrder = if (botsAsDealers) {
                val members = checkNotNull(rooms.getRoom(checkNotNull(table.tableId))).playerIds
                listOf(bot.playerId()) + (members - bot.playerId())
            } else {
                null
            }
            AutotestServerTable.rooms(koin).start(player = bot, seatOrder = seatOrder, matchSeed = table.matchSeed(matchIndex))
        }
        val started = waitUntil(SYNC_TIMEOUT) { tables.all { gameRepository.getGame(checkNotNull(it.tableId)) != null } }
        if (!check("Every table starts a match", started)) return null
        return tables.associate { it.rule to checkNotNull(gameRepository.getGame(checkNotNull(it.tableId))) }
    }

    /**
     * 等各桌這一場打完回到房間，並封存進牌譜。
     *
     * @param games 各規則開局時的牌局。
     */
    private suspend fun AutotestServerContext.waitForMatchesToFinish(games: Map<String, Game>) {
        for (table in tables) {
            val game = games.getValue(table.rule)
            val finished = waitUntil(MATCH_TIMEOUT) { AutotestServerTable.isWaitingRoom(koin, game.id) }
            if (!check("The ${table.rule} match finishes and returns to the room", finished)) continue
            check("The ${table.rule} match is archived", waitUntil(SYNC_TIMEOUT) { AutotestServerTable.isArchived(koin, game.matchId) })
        }
    }

    /** 關機完成：記錄關機時間並寫出報告。 */
    private fun finish() {
        val context = AutotestServerContext()
        val elapsed = stopRequestedAt?.elapsedNow()
        context.check("The server stops within $STOP_LIMIT", elapsed != null && elapsed <= STOP_LIMIT, "took $elapsed")
        val errors = AutotestErrorLog.drain()
        context.check("MahjongCraft logged no errors", errors.isEmpty(), errors.joinToString(" | ").takeIf(String::isNotEmpty))
        val passed = context.checks.all { it.passed }
        results += result("$phase-shutdown", if (passed) AutotestStatus.PASSED else AutotestStatus.FAILED, elapsed ?: Duration.ZERO, context, null)
        writeReport()
    }

    /** 寫出包含前一段與這一段的報告。 */
    private fun writeReport() {
        val steps = earlierResults + results
        val finished = phase == PHASE_RESUME
        val status = when {
            steps.any { it.status == AutotestStatus.FAILED } -> AutotestStatus.FAILED
            !finished -> AutotestStatus.ABORTED
            else -> AutotestStatus.PASSED
        }
        val report = AutotestReport(
            target = System.getProperty(TARGET_PROPERTY) ?: "unknown",
            startedAt = startedAt,
            durationMillis = startMark.elapsedNow().inWholeMilliseconds,
            status = status,
            notes = listOfNotNull(
                EULA_NOTE,
                "The resume phase has not run yet; run the autotestServer task to run both phases".takeUnless { finished },
            ),
            steps = steps,
        )
        Files.writeString(outputDirectory.resolve(REPORT_JSON), JSON.encodeToString(AutotestReport.serializer(), report))
        Files.writeString(outputDirectory.resolve("report.md"), autotestReportMarkdown(report))
    }

    /**
     * 讀第一段記下的狀態。
     *
     * @return 記下的狀態。沒有時為 null。
     */
    private fun readRecord(): AutotestServerRestartRecord? = outputDirectory.resolve(RESTART_JSON).takeIf(Files::exists)
        ?.let { JSON.decodeFromString(AutotestServerRestartRecord.serializer(), Files.readString(it)) }

    /** 伺服器自動測試的階段與系統屬性。 */
    companion object {
        /**
         * 這次開機指定要跑的階段。
         *
         * - 只有 `autotestServer` 任務啟動的伺服器會指定階段。
         * - 其他伺服器，例如範例或平常的 `runServer`，不執行伺服器自動測試，避免動到平常的開發世界。
         *
         * @return [PHASE_START] 或 [PHASE_RESUME]，沒有指定或不是這兩者時為 null。
         */
        fun requestedPhase(): String? = phaseOf(System.getProperty(PHASE_PROPERTY))

        /**
         * 解析階段屬性的值。
         *
         * @param value 系統屬性的值。沒有設定時為 null。
         * @return [PHASE_START] 或 [PHASE_RESUME]。其他值為 null。
         */
        internal fun phaseOf(value: String?): String? = value?.trim()?.takeIf { it == PHASE_START || it == PHASE_RESUME }

        /** 開發執行以這個系統屬性傳入第幾段。 */
        private const val PHASE_PROPERTY = "mahjongcraft.autotest.serverPhase"

        /** 第一段。 */
        private const val PHASE_START = "start"

        /** 第二段。 */
        private const val PHASE_RESUME = "resume"

        /** 開發執行以這個系統屬性傳入測試的目標。 */
        private const val TARGET_PROPERTY = "mahjongcraft.autotest.target"

        /** 報告的 JSON 檔名。 */
        private const val REPORT_JSON = "report.json"

        /** 第一段記下狀態的檔名。 */
        private const val RESTART_JSON = "restart.json"

        /** 報告中提醒執行者測試代為同意了 Minecraft EULA。 */
        private const val EULA_NOTE = "This test accepted the Minecraft EULA (https://aka.ms/MinecraftEULA) on behalf of whoever ran it, " +
            "in this server's run directory"

        /** 報告的 JSON 格式。 */
        private val JSON = Json { prettyPrint = true }

        /** 等伺服器處理房間操作的最長時間。 */
        private val SYNC_TIMEOUT = 30.seconds

        /** 等一場打完或等到指定狀態的最長時間。 */
        private val MATCH_TIMEOUT = 15.minutes

        /** 旁觀的機器人名稱。它不入座，在房主都離線時確認對局仍然暫停。 */
        private const val SPECTATOR_NAME = "MahjongSpectator"

        /** 確認暫停時，對局至少要累計暫停這麼久。 */
        private val PAUSE_HOLD = 3.seconds

        /** 清理檢查的物件生成在牌桌控制方塊上方的高度。 */
        private const val CLEANUP_SPAWN_HEIGHT = 3.0

        /** 從牌桌控制方塊找局況顯示的範圍。 */
        private const val ROUND_INFO_SEARCH_RADIUS = 8.0

        /** 關機應該在這段時間內完成。 */
        private val STOP_LIMIT = 20.seconds

        /** 讀回的決策時間與關機前記下的時間可以相差的範圍。 */
        private val TIME_TOLERANCE = 5.seconds

        /**
         * 一個步驟的結果。
         *
         * @param id 步驟識別碼。
         * @param status 結果。
         * @param duration 耗時。
         * @param context 步驟的檢查。
         * @param error 使步驟中斷的錯誤。沒有時為 null。
         * @return 步驟結果。
         */
        fun result(id: String, status: AutotestStatus, duration: Duration, context: AutotestServerContext, error: Throwable?) = AutotestStepResult(
            id = id,
            status = status,
            durationMillis = duration.inWholeMilliseconds,
            checks = context.checks.toList(),
            screenshots = emptyList(),
            sounds = emptyList(),
            error = error?.stackTraceToString(),
        )
    }
}

/**
 * 一張牌桌在暫停中某個時刻的狀態。
 *
 * @property fingerprint 牌局進度。找不到牌局時為 null。
 * @property time 房主剩下的基本時間與保留時間。查不到時為 null。
 * @property pauseElapsed 對局累計暫停的時間。
 */
private data class AutotestPausedState(val fingerprint: String?, val time: Pair<Duration, Duration>?, val pauseElapsed: Duration)
