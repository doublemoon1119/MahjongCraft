package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.event.TablePresentationBusyTracker
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.SeatingSessionFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.TableOccupancyDto
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.koin.core.context.GlobalContext
import kotlin.math.hypot
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/** 自動測試中開局的牌桌位置，以及開局後記下的牌桌識別碼，供之後的步驟使用。 */
internal object AutotestTable {
    /** 牌桌的位置。 */
    val POSITION: BlockPos = BlockPos(0, -60, 6)

    /** 開局的牌桌識別碼；還沒開局時為 null。 */
    var tableId: Uuid? = null

    /**
     * 本機玩家是否坐在開局的牌桌對局中。
     *
     * @param client 目前客戶端。
     * @return 坐在這張牌桌時為 true。
     */
    fun isSeated(client: Minecraft): Boolean {
        val playerId = client.player?.uuid?.toKotlinUuid() ?: return false
        return tableId != null && GlobalContext.get().get<ClientMahjongStateStore>().findTableWhereSeated(playerId) == tableId
    }
}

/**
 * 截兩張牌桌的圖：玩家目前的視角，以及從牌桌前上方俯視整張桌子的固定鏡頭，四家的牌河與副露都在畫面中。
 *
 * @param name 截圖名稱；俯視的那張加上 `-overhead`。
 */
internal suspend fun AutotestContext.tableScreenshots(name: String) {
    client.setScreen(null)
    screenshot(name)
    overheadScreenshot("$name-overhead", Vec3.atBottomCenterOf(AutotestTable.POSITION))
}

/**
 * 從指定位置的北方上方俯視截圖，與俯視牌桌的鏡頭相同距離與角度。
 *
 * @param name 截圖名稱。
 * @param center 要拍的位置。
 */
internal suspend fun AutotestContext.overheadScreenshot(name: String, center: Vec3) {
    screenshotFrom(name, Vec3(center.x, center.y + OVERHEAD_HEIGHT, center.z - OVERHEAD_DISTANCE), yaw = 0f, pitch = OVERHEAD_PITCH)
}

/** 俯視鏡頭比拍攝位置高多少格。 */
private const val OVERHEAD_HEIGHT = 5.5

/** 俯視鏡頭在拍攝位置北方多遠。 */
private const val OVERHEAD_DISTANCE = 4.0

/** 俯視鏡頭的俯角。 */
private const val OVERHEAD_PITCH = 52f

/**
 * 牌桌目前是否在播演出：直接查伺服器判斷能否接受下一個操作的同一份狀態。
 *
 * @return 在播演出，或還沒開局時為 true。
 */
internal suspend fun AutotestContext.isTableBusy(): Boolean {
    val tableId = AutotestTable.tableId ?: return true
    val tracker = GlobalContext.get().get<TablePresentationBusyTracker>()
    return onServer { tracker.isBusy(tableId) }
}

/**
 * 等到牌桌沒有在播演出。
 *
 * @return 在時限內停下時為 true。
 */
internal suspend fun AutotestContext.waitUntilTableIdle(): Boolean = waitUntil(READY_TIMEOUT) { !isTableBusy() }

/**
 * 伺服器上這張牌桌的對局是否可以接受下一個操作：對局進行中、不在換局或結束的途中，牌桌也沒有在播演出。與 debug 場景載入
 * 檢查的條件相同。
 *
 * @return 可以接受操作時為 true。
 */
internal suspend fun AutotestContext.isGameReady(): Boolean {
    val tableId = AutotestTable.tableId ?: return false
    val game = GlobalContext.get().get<GameRepository>().getGame(tableId) ?: return false
    return !game.isMatchOver && game.pendingTransition == null && !isTableBusy()
}

/**
 * 確保本機玩家坐在開局的牌桌對局中，而且對局可以接受下一個操作；上一場對局已經結束、牌桌回到房間時，以房主身分再開一場。
 *
 * 再開一場時指定本機玩家換到和上一場不同的座位，固定檢查換座位時新座位沒有被上一場的 AI 擋住。最後檢查本機玩家在自己的座位上，
 * 每位 AI 也都有角色在場。
 *
 * 牌局的進展是隨機的，可能很早就有人和牌而結束，因此需要牌局的步驟在開始前先呼叫。
 *
 * @return 坐在對局中而且對局可以接受操作時為 true。
 */
internal suspend fun AutotestContext.ensureGameRunning(): Boolean {
    val tableId = AutotestTable.tableId ?: return false
    val koin = GlobalContext.get()
    val states = koin.get<ClientMahjongStateStore>()
    val settled = waitUntil(READY_TIMEOUT) { isGameReady() || states.tableOccupancy(tableId)?.occupancy == TableOccupancyDto.ROOM }
    if (!check("The table has a game or a room", settled)) return false
    if (!isGameReady() && !startNextMatchAtAnotherSeat(tableId)) return false
    client.setScreen(null)
    if (!check("The player is seated at the table", waitUntil(SYNC_TIMEOUT) { AutotestTable.isSeated(client) })) return false
    val atSeat = waitUntil(SYNC_TIMEOUT) { seatDistance(tableId)?.let { it <= SEAT_TOLERANCE } == true }
    if (!check("The player is at their seat", atSeat, "distance ${seatDistance(tableId)}")) return false
    val aiCount = koin.get<GameRepository>().getGame(tableId)?.aiPlayerIds?.size
    val charactersPresent = waitUntil(SYNC_TIMEOUT) { botCount() == aiCount }
    if (!check("Every AI player has a character at the table", charactersPresent, "expected $aiCount, found ${botCount()}")) return false
    // 檢查期間對局會繼續進行，可以接受操作的狀態放在最後確認，緊接著下一個操作。
    return check("The game can take the next action", waitUntil(READY_TIMEOUT) { isGameReady() })
}

/**
 * 以房主身分再開一場，指定本機玩家坐到上一場下一個座位。
 *
 * @param tableId 牌桌識別碼。
 * @return 新的一場已開局、可以接受操作，而且本機玩家在指定座位時為 true。
 */
private suspend fun AutotestContext.startNextMatchAtAnotherSeat(tableId: Uuid): Boolean {
    val playerId = checkNotNull(client.player).uuid.toKotlinUuid()
    val koin = GlobalContext.get()
    val previousSeats = koin.get<RoomRepository>().getRoom(tableId)?.playerIds
    if (!check("The room keeps the previous seat order", previousSeats != null && playerId in previousSeats)) return false
    val previousSeat = checkNotNull(previousSeats).indexOf(playerId)
    val seat = (previousSeat + 1) % previousSeats.size
    if (!runCheckedCommand("mahjongcraft debug room start $seat")) return false
    if (!check("A new match starts", waitUntil(READY_TIMEOUT) { isGameReady() })) return false
    val seated = koin.get<GameRepository>().getGame(tableId)?.tableState?.players?.indexOfFirst { it.id == playerId }
    return check("The player moves from seat $previousSeat to seat $seat", seated == seat, "seat $seated")
}

/**
 * 伺服器上本機玩家與自己座位的水平距離。
 *
 * @param tableId 牌桌識別碼。
 * @return 水平距離；不在對局中或座位無法解析時為 null。
 */
private suspend fun AutotestContext.seatDistance(tableId: Uuid): Double? {
    val playerId = checkNotNull(client.player).uuid
    val koin = GlobalContext.get()
    val roster = koin.get<GameRepository>().getGame(tableId)?.tableState?.players?.map { it.id } ?: return null
    val index = roster.indexOf(playerId.toKotlinUuid()).takeIf { it >= 0 } ?: return null
    return onServer { server ->
        val seat = koin.get<SeatingSessionFactory>().prepare(tableId, roster)?.requests?.get(index)?.seat ?: return@onServer null
        val player = server.playerList.getPlayer(playerId) ?: return@onServer null
        hypot(player.x - seat.x, player.z - seat.z)
    }
}

/**
 * 伺服器上本 mod 的 AI 角色數量。
 *
 * @return 世界中的 AI 角色數量。
 */
private suspend fun AutotestContext.botCount(): Int = onServer { server ->
    server.allLevels.sumOf { level -> level.allEntities.count { BuiltInRegistries.ENTITY_TYPE.getKey(it.type) == BOT_ENTITY_ID } }
}

/** 等牌桌演出結束、換局或再開一場的最長時間。 */
private val READY_TIMEOUT = 2.minutes

/** 等用戶端收到伺服器狀態的最長時間。 */
private val SYNC_TIMEOUT = 10.seconds

/** 玩家與座位中心水平距離的容許值，與正式入座檢查座位位置的容許值相同。 */
private const val SEAT_TOLERANCE = 0.25

/** 本 mod 內建 AI 角色的實體種類。 */
private val BOT_ENTITY_ID = ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_bot")
