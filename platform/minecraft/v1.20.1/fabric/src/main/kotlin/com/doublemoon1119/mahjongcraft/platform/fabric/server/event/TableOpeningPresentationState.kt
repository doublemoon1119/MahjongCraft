package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.uuid.Uuid

/**
 * 每張桌子開局呈現途中的暫存資料。
 *
 * 開局的四個階段——牌牆生成掉落、擲骰、發牌、開門——由呼叫端分四次發布，但後面的階段需要前面階段算出的
 * 數字才能把自己的動畫延後到正確的時刻。呼叫端應先記錄牌牆資料，再發布擲骰與發牌；
 * 新開局的後續階段會等待牌牆成功建立後交付開始時間；失敗或清理會取消等待，不自行猜測起點。
 *
 * 保存識別碼、呈現資料與開始時間的等待結果，不碰世界、entity 或 scheduler。同一張桌子的各階段可能在不同
 * 執行緒排入，因此以 [ConcurrentHashMap] 保存。
 */
internal class TableOpeningPresentationState {
    /** 每張桌子最近一次牌牆生成掉落動畫的總時長（ticks）。 */
    private val wallDropTicksByTable = ConcurrentHashMap<Uuid, Int>()

    /** 每張桌子最近一次牌牆結構換算出的單面墩數。 */
    private val wallStacksPerSideByTable = ConcurrentHashMap<Uuid, Int>()

    /** 每張桌子最近一次牌牆結構的面數。 */
    private val wallSideCountByTable = ConcurrentHashMap<Uuid, Int>()

    /** 等待初次發牌與各座位翻牌完成後使用的本局開門資料。 */
    private val pendingWallOpeningByTable = ConcurrentHashMap<Uuid, MahjongTileWallPresentation>()

    /** 擲骰揭示完成前暫存的局況資料；揭示完成後由初次發牌階段交付。 */
    private val pendingRoundInfoByTable = ConcurrentHashMap<Uuid, TableState>()

    /** 提供每次新開局呈現使用的單調序號，避免平台自行猜測局數或本場數的上限。 */
    private val nextOpeningSequence = AtomicLong()

    /** 每張桌子目前開局批次中，各輪擲骰實際使用的穩定序號。 */
    private val openingRollSequenceByTable = ConcurrentHashMap<Uuid, ConcurrentHashMap<Int, Long>>()

    /** 每張桌子本批牌牆動畫開始時間的等待結果，成功建立牌牆後才完成。 */
    private val wallStartGameTimeByTable = ConcurrentHashMap<Uuid, CompletableDeferred<Long>>()

    /**
     * 開始新一局的牌牆：記錄本局的動畫時長、墩數與面數，並丟棄上一局尚未播出的開門資料。
     *
     * 每局覆寫上一局的資料；多個欄位分別更新，不構成跨欄位的原子快照。
     *
     * @param gameId 桌子／對局識別碼。
     * @param wallDropTicks 牌牆掉落動畫時長。
     * @param stacksPerSide 單面墩數。
     * @param sideCount 牌牆面數。
     * @param isNewOpening 是否建立新的開局呈現序號。
     */
    fun beginWall(
        gameId: Uuid,
        wallDropTicks: Int,
        stacksPerSide: Int,
        sideCount: Int,
        isNewOpening: Boolean = true,
    ) {
        pendingWallOpeningByTable.remove(gameId)
        pendingRoundInfoByTable.remove(gameId)
        wallDropTicksByTable[gameId] = wallDropTicks
        wallStacksPerSideByTable[gameId] = stacksPerSide
        wallSideCountByTable[gameId] = sideCount
        if (isNewOpening) {
            openingRollSequenceByTable[gameId] = ConcurrentHashMap()
            wallStartGameTimeByTable.put(gameId, CompletableDeferred())?.cancel()
        }
    }

    /** 記錄本批牌牆動畫實際開始的世界 tick，供後續階段共用同一時間線錨點。 */
    fun markWallStarted(gameId: Uuid, worldTime: Long) {
        wallStartGameTimeByTable[gameId]?.complete(worldTime)
    }

    /**
     * 等待本批牌牆成功建立，取得動畫共同的世界 tick 起點。
     *
     * @param gameId 桌子／對局識別碼。
     * @return 本批開始時間；沒有建立新開局批次時為 null。
     * @throws CancellationException 等待期間該批次被清除、取代或伺服器停止。
     */
    suspend fun awaitWallStartGameTime(gameId: Uuid): Long? = wallStartGameTimeByTable[gameId]?.await()

    /**
     * 牌牆已成功生成且本局要播開門：保留開門資料，供發牌與各座位翻牌完成後使用。
     *
     * @param gameId 桌子／對局識別碼。
     * @param presentation 已成功呈現的牌牆及開門資料。
     */
    fun armOpening(gameId: Uuid, presentation: MahjongTileWallPresentation) {
        pendingWallOpeningByTable[gameId] = presentation
    }

    /**
     * 牌牆呈現沒有成立：本局不播開門。
     *
     * @param gameId 桌子／對局識別碼。
     */
    fun cancelOpening(gameId: Uuid) {
        pendingWallOpeningByTable.remove(gameId)
    }

    /**
     * 取出並消費本局的開門資料；沒有要播開門時為 `null`。開門一局只播一次。
     *
     * @param gameId 桌子／對局識別碼。
     * @return 待播放的開門資料，或沒有資料時的 null。
     */
    fun consumeOpening(gameId: Uuid): MahjongTileWallPresentation? = pendingWallOpeningByTable.remove(gameId)

    /** 暫存開局期間的局況資料，避免在擲骰結果揭示前先顯示莊家與局況。 */
    fun deferRoundInfo(gameId: Uuid, tableState: TableState) {
        pendingRoundInfoByTable[gameId] = tableState
    }

    /** 取出並消費等待擲骰揭示後交付的局況資料。 */
    fun consumeRoundInfo(gameId: Uuid): TableState? = pendingRoundInfoByTable.remove(gameId)

    /**
     * 牌牆生成掉落動畫的總時長（ticks），供擲骰與發牌把自己的動畫延後到牌牆完全落地。
     *
     * 沒有紀錄時為 `0`——例如規則沒有牌牆，或呼叫端沒有先發布牌牆結構；那種情況下動畫不需要延後。
     *
     * @param gameId 桌子／對局識別碼。
     * @return 記錄的掉落動畫時長，沒有資料時為 0。
     */
    fun wallDropTicks(gameId: Uuid): Int = wallDropTicksByTable[gameId] ?: 0

    /**
     * 本局牌牆的單面墩數；沒有紀錄時為 `null`，呼叫端應放棄需要它的呈現。
     *
     * @param gameId 桌子／對局識別碼。
     * @return 記錄的單面墩數；沒有資料時為 null。
     */
    fun wallStacksPerSide(gameId: Uuid): Int? = wallStacksPerSideByTable[gameId]

    /**
     * 本局牌牆的面數；沒有紀錄時為 `null`，呼叫端應放棄需要它的呈現。
     *
     * @param gameId 桌子／對局識別碼。
     * @return 記錄的牌牆面數；沒有資料時為 null。
     */
    fun wallSideCount(gameId: Uuid): Int? = wallSideCountByTable[gameId]

    /**
     * 取得本局擲骰的穩定呈現序號。
     *
     * @param gameId 桌子／對局識別碼。
     * @param rollIndex 本局擲骰程序中的零基底輪次。
     * @return 同桌本批次內唯一且穩定的序號；沒有新開局批次時為 null。
     */
    fun openingRollSequence(gameId: Uuid, rollIndex: Int): Long? = openingRollSequenceByTable[gameId]?.computeIfAbsent(rollIndex) {
        nextOpeningSequence.getAndIncrement()
    }

    /**
     * 丟棄這張桌子全部的開局暫存資料。
     *
     * @param gameId 桌子／對局識別碼。
     */
    fun clear(gameId: Uuid) {
        pendingWallOpeningByTable.remove(gameId)
        pendingRoundInfoByTable.remove(gameId)
        wallDropTicksByTable.remove(gameId)
        wallStacksPerSideByTable.remove(gameId)
        wallSideCountByTable.remove(gameId)
        openingRollSequenceByTable.remove(gameId)
        wallStartGameTimeByTable.remove(gameId)?.cancel(CancellationException("Opening presentation was cleared"))
    }

    /** 清除伺服器停止時所有桌子的開局暫存資料。 */
    fun clearAll() {
        pendingWallOpeningByTable.clear()
        pendingRoundInfoByTable.clear()
        wallDropTicksByTable.clear()
        wallStacksPerSideByTable.clear()
        wallSideCountByTable.clear()
        openingRollSequenceByTable.clear()
        wallStartGameTimeByTable.values.forEach { it.cancel(CancellationException("Opening session stopped")) }
        wallStartGameTimeByTable.clear()
    }
}
