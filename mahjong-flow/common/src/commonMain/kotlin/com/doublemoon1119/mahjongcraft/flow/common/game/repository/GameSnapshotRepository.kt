package com.doublemoon1119.mahjongcraft.flow.common.game.repository

import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import java.util.*

/**
 * 桌況快照數據倉庫。
 *
 * 用於管理供不同觀察者視圖渲染或同步使用的快照數據。
 */
interface GameSnapshotRepository {
    /**
     * 獲取特定觀察者所看見的桌況快照。
     *
     * @param gameId 遊戲的唯一識別碼。
     * @param observerId 觀察者的玩家 UUID。
     * @return 該觀察者視角的 [TableStateSnapshot]，若不存在則回傳 null。
     */
    suspend fun getSnapshot(gameId: UUID, observerId: UUID): TableStateSnapshot?

    /**
     * 設置或更新特定觀察者的遊戲快照。
     *
     * @param observerId 接收此快照的觀察者 UUID。
     * @param snapshot 針對該觀察者生成的過濾後快照。
     */
    suspend fun setSnapshot(observerId: UUID, snapshot: TableStateSnapshot)

    /**
     * 移除特定觀察者的遊戲快照。
     *
     * @param gameId 遊戲的唯一識別碼。
     * @param observerId 觀察者的玩家 UUID。
     */
    suspend fun removeSnapshot(gameId: UUID, observerId: UUID)

    /**
     * 獲取目前所有正在觀察該遊戲的所有觀察者 UUID 集合。
     *
     * @param roomId 遊戲的唯一識別碼。
     * @return 觀察該遊戲的所有玩家 UUID 集合。
     */
    suspend fun getAllObservers(roomId: UUID): Set<UUID>
}