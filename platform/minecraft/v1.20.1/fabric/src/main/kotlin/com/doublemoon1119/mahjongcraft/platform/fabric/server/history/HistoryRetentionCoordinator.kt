package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftHistoryConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

/** 排序保留政策發布與清理工作，避免較舊政策在 reload 成功後繼續刪除資料。 */
@Single
class HistoryRetentionCoordinator {
    /** 保護政策與清理工作；不與權威 store 共用鎖，也不在 store callback 內等待。 */
    private val mutex = Mutex()

    /** 目前已完成發布的不可變政策。 */
    private var policy = MinecraftHistoryConfig().retentionPolicy()

    /**
     * 發布有效政策；若外部設定交易失敗，保留原政策。
     *
     * @param next 新政策。
     * @param publish 發布設定與權威記錄政策的交易，不得重入此 coordinator。
     */
    internal suspend fun apply(next: HistoryRetentionPolicy, publish: suspend () -> Unit = {}) = mutex.withLock {
        publish()
        policy = next
    }

    /**
     * 以固定政策執行維護；更新政策會等待此工作結束，但不阻塞執行緒。
     *
     * @param block 可取消的維護工作，不得重入 coordinator。
     * @return 工作結果。
     */
    internal suspend fun <T> withPolicy(block: suspend (HistoryRetentionPolicy) -> T): T = mutex.withLock { block(policy) }
}
