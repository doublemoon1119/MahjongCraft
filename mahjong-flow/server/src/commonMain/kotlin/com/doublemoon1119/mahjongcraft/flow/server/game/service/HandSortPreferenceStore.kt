package com.doublemoon1119.mahjongcraft.flow.server.game.service

import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 記錄每位玩家選擇的手牌排列方式：是否自動整理手牌。
 *
 * 偏好只影響本人快照的手牌順序；其他觀察者的蓋牌快照與共享布局使用不依牌面排列的公開順序。
 * 套用時機見 `SetHandSortPreferenceUseCase`。
 *
 * 刻意純記憶體、不接進 `AuthoritativeStateStore`：伺服器重啟後回到預設值即可，玩家重新連線時用戶端會自動重送一次
 * 目前的選擇。
 */
class HandSortPreferenceStore {
    /** 以玩家識別碼索引本次 server session 的手牌整理偏好。 */
    private val preferences = ConcurrentHashMap<Uuid, Boolean>()

    /**
     * 查詢 [playerId] 目前是否啟用自動整理手牌；未曾設定過視為 `false`。
     *
     * @param playerId 欲查詢的玩家識別碼。
     * @return 已啟用時為 `true`，否則為 `false`。
     */
    fun isEnabled(playerId: Uuid): Boolean = preferences[playerId] ?: false

    /**
     * 設定 [playerId] 的自動整理手牌偏好。
     *
     * @param playerId 要更新偏好的玩家識別碼。
     * @param enabled 是否啟用自動整理。
     */
    fun set(playerId: Uuid, enabled: Boolean) {
        preferences[playerId] = enabled
    }
}
