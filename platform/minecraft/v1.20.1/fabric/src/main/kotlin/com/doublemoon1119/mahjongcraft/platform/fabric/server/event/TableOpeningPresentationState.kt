package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentation
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 每張桌子開局呈現途中的暫存資料。
 *
 * 開局的四個階段——牌牆生成掉落、擲骰、發牌、開門——由呼叫端分四次發布，但後面的階段需要前面階段算出的
 * 數字才能把自己的動畫延後到正確的時刻。這個類別保存那些跨階段的中間值，讓各階段之間不必約定呼叫順序：
 * 讀不到值時一律有安全的預設行為。
 *
 * 只保存 [Uuid]、[Int] 與牌牆呈現值物件，不碰世界、entity 或 scheduler。同一張桌子的各階段可能在不同
 * 執行緒排入，因此以 [ConcurrentHashMap] 保存。
 */
internal class TableOpeningPresentationState {
    /** 每張桌子最近一次牌牆生成掉落動畫的總時長（ticks）。 */
    private val wallDropTicksByTable = ConcurrentHashMap<Uuid, Int>()

    /** 每張桌子最近一次牌牆結構換算出的單面墩數。 */
    private val wallStacksPerSideByTable = ConcurrentHashMap<Uuid, Int>()

    /** 等待初次發牌與四家翻牌完成後才可排入 entity 佇列的本局開門資料。 */
    private val pendingWallOpeningByTable = ConcurrentHashMap<Uuid, MahjongTileWallPresentation>()

    /**
     * 開始新一局的牌牆：記錄本局的動畫時長與墩數，並丟棄上一局尚未播出的開門資料。
     *
     * 每局都會重新呼叫，覆寫上一局的舊值；後續讀取端因此不需要協調誰先讀、誰負責清除。
     */
    fun beginWall(gameId: Uuid, wallDropTicks: Int, stacksPerSide: Int) {
        pendingWallOpeningByTable.remove(gameId)
        wallDropTicksByTable[gameId] = wallDropTicks
        wallStacksPerSideByTable[gameId] = stacksPerSide
    }

    /** 牌牆已成功生成且本局要播開門：保留開門資料，等發牌與四家翻牌完成後才排入。 */
    fun armOpening(gameId: Uuid, presentation: MahjongTileWallPresentation) {
        pendingWallOpeningByTable[gameId] = presentation
    }

    /** 牌牆呈現沒有成立：本局不播開門。 */
    fun cancelOpening(gameId: Uuid) {
        pendingWallOpeningByTable.remove(gameId)
    }

    /** 取出並消費本局的開門資料；沒有要播開門時為 `null`。開門一局只播一次。 */
    fun consumeOpening(gameId: Uuid): MahjongTileWallPresentation? = pendingWallOpeningByTable.remove(gameId)

    /**
     * 牌牆生成掉落動畫的總時長（ticks），供擲骰與發牌把自己的動畫延後到牌牆完全落地。
     *
     * 沒有紀錄時為 `0`——例如規則沒有牌牆，或呼叫端沒有先發布牌牆結構；那種情況下動畫不需要延後。
     */
    fun wallDropTicks(gameId: Uuid): Int = wallDropTicksByTable[gameId] ?: 0

    /** 本局牌牆的單面墩數；沒有紀錄時為 `null`，呼叫端應放棄需要它的呈現。 */
    fun wallStacksPerSide(gameId: Uuid): Int? = wallStacksPerSideByTable[gameId]

    /** 丟棄這張桌子全部的開局暫存資料。 */
    fun clear(gameId: Uuid) {
        pendingWallOpeningByTable.remove(gameId)
        wallDropTicksByTable.remove(gameId)
        wallStacksPerSideByTable.remove(gameId)
    }
}
