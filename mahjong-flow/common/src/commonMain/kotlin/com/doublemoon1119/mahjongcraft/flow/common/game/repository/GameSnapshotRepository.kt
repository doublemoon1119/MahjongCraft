package com.doublemoon1119.mahjongcraft.flow.common.game.repository

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/**
 * 桌況快照數據倉庫。
 *
 * 用於管理供不同觀察者呈現或同步使用的快照數據。
 */
interface GameSnapshotRepository {
    /**
     * 獲取特定觀察者所看見的桌況快照。
     *
     * @param gameId 遊戲的唯一識別碼。
     * @param observerId 觀察者的玩家 Uuid。
     * @return 該觀察者視角的 [TableStateSnapshot]，若不存在則回傳 null。
     */
    suspend fun getSnapshot(gameId: Uuid, observerId: Uuid): TableStateSnapshot?

    /**
     * 設置或更新特定觀察者的遊戲快照。
     *
     * @param observerId 接收此快照的觀察者 Uuid。
     * @param snapshot 針對該觀察者生成的過濾後快照。
     */
    suspend fun setSnapshot(observerId: Uuid, snapshot: TableStateSnapshot)

    /**
     * 取得指定觀看者可見的開局準備快照；預設實作回傳 null，表示沒有提供此類快照。
     *
     * @param gameId 快照所屬遊戲的識別碼。
     * @param observerId 要取得快照的觀看者識別碼。
     * @return 觀看者可見的開局準備快照；沒有快照或使用預設實作時為 null。
     */
    suspend fun getRoundPreparationSnapshot(gameId: Uuid, observerId: Uuid): RoundPreparationSnapshot? = null

    /**
     * 設定或清除指定觀看者的開局準備快照；預設實作不保存資料。
     *
     * @param gameId 快照所屬遊戲的識別碼。
     * @param observerId 接收或移除快照的觀看者識別碼。
     * @param snapshot 要保存的快照；null 表示清除快照。
     */
    suspend fun setRoundPreparationSnapshot(gameId: Uuid, observerId: Uuid, snapshot: RoundPreparationSnapshot?) = Unit

    /**
     * 移除特定觀察者的遊戲快照。
     *
     * @param gameId 遊戲的唯一識別碼。
     * @param observerId 觀察者的玩家 Uuid。
     */
    suspend fun removeSnapshot(gameId: Uuid, observerId: Uuid)

    /**
     * 在 repository 互斥鎖內確認來源仍有效後移除桌況與準備快照。
     *
     * 條件只能讀取已發布資料，不得阻塞、重新呼叫此 repository 或修改來源。
     *
     * @param gameId 遊戲的唯一識別碼。
     * @param observerId 觀察者的玩家 Uuid。
     * @param isStillValid 鎖內刪除前確認來源仍屬於這次工作的條件。
     * @return 條件成立並完成這次移除時為 true；條件不成立時不修改資料並回傳 false。
     */
    suspend fun removeSnapshotIf(gameId: Uuid, observerId: Uuid, isStillValid: () -> Boolean): Boolean

    /**
     * 獲取目前所有正在觀察該遊戲的所有觀察者 Uuid 集合。
     *
     * @param gameId 遊戲的唯一識別碼。
     * @return 觀察該遊戲的所有玩家 Uuid 集合。
     */
    suspend fun getAllObservers(gameId: Uuid): Set<Uuid>

    /** 清除目前 server session 的所有遊戲 observer 快照。 */
    suspend fun clearAll()
}
