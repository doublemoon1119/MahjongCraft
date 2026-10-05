package com.doublemoon1119.mahjongcraft.flow.server.game.service

import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 記錄每位玩家選擇的手牌排列方式：是否自動整理手牌。
 *
 * 其他玩家看得到牌的擺放順序，排列方式會影響可被讀出的資訊，因此由伺服器套用到權威手牌順序，而不是各用戶端各自
 * 排序；套用時機見 `SetHandSortPreferenceUseCase`。
 *
 * 刻意純記憶體、不接進 `AuthoritativeStateStore`：伺服器重啟後回到預設值即可，玩家重新連線時用戶端會自動重送一次
 * 目前的選擇。
 */
@Single
class HandSortPreferenceStore {
    private val preferences = ConcurrentHashMap<Uuid, Boolean>()

    /** [playerId] 目前是否啟用自動整理手牌；未曾設定過視為 `false`。 */
    fun isEnabled(playerId: Uuid): Boolean = preferences[playerId] ?: false

    /** 設定 [playerId] 的自動整理手牌偏好。 */
    fun set(playerId: Uuid, enabled: Boolean) {
        preferences[playerId] = enabled
    }
}
