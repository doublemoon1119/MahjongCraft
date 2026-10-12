package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentation
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 每張桌子開局呈現途中的暫存資料。
 *
 * 開局的四個階段——牌牆生成掉落、擲骰、發牌、開門——由呼叫端分四次發布，但後面的階段需要前面階段算出的
 * 數字才能把自己的動畫延後到正確的時刻。呼叫端應先記錄牌牆資料，再發布擲骰與發牌；
 * 資料缺失時，各查詢提供 0 或 null，供呼叫端略過等待或相關呈現。
 *
 * 只保存 [Uuid]、[Int] 與牌牆呈現值物件，不碰世界、entity 或 scheduler。同一張桌子的各階段可能在不同
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

    /**
     * 開始新一局的牌牆：記錄本局的動畫時長、墩數與面數，並丟棄上一局尚未播出的開門資料。
     *
     * 每局覆寫上一局的資料；多個欄位分別更新，不構成跨欄位的原子快照。
     *
     * @param gameId 桌子／對局識別碼。
     * @param wallDropTicks 牌牆掉落動畫時長。
     * @param stacksPerSide 單面墩數。
     * @param sideCount 牌牆面數。
     */
    fun beginWall(
        gameId: Uuid,
        wallDropTicks: Int,
        stacksPerSide: Int,
        sideCount: Int,
    ) {
        pendingWallOpeningByTable.remove(gameId)
        wallDropTicksByTable[gameId] = wallDropTicks
        wallStacksPerSideByTable[gameId] = stacksPerSide
        wallSideCountByTable[gameId] = sideCount
    }

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
     * 丟棄這張桌子全部的開局暫存資料。
     *
     * @param gameId 桌子／對局識別碼。
     */
    fun clear(gameId: Uuid) {
        pendingWallOpeningByTable.remove(gameId)
        wallDropTicksByTable.remove(gameId)
        wallStacksPerSideByTable.remove(gameId)
        wallSideCountByTable.remove(gameId)
    }
}
