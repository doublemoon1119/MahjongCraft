package com.doublemoon1119.mahjongcraft.application.common.game.repository

import com.doublemoon1119.mahjongcraft.domain.table.TableStateSnapshot
import java.util.UUID

/**
 * 桌況快照數據倉庫。
 *
 * 用於管理供視圖渲染或同步使用的快照數據。
 */
interface GameSnapshotRepository {
    /**
     * 獲取指定遊戲 ID 的快照數據。
     *
     * @param gameId 遊戲的唯一識別碼。
     * @return 該局遊戲的 [TableStateSnapshot]，若不存在則回傳 null。
     */
    suspend fun getSnapshot(gameId: UUID): TableStateSnapshot?

    /**
     * 設置或更新最新的遊戲快照。
     *
     * @param snapshot 最新生成的快照物件。
     */
    suspend fun setSnapshot(snapshot: TableStateSnapshot)

    /**
     * 移除指定遊戲的快照數據。
     *
     * @param gameId 遊戲的唯一識別碼。
     */
    suspend fun removeSnapshot(gameId: UUID)
}