package com.doublemoon1119.mahjongcraft.flow.server.game.service

import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 記錄每位玩家「是否啟用自動整理手牌」這個純呈現偏好，不透過 [org.koin.core.annotation.Provided]
 * 的 client-only 疊加（不像牌角標籤那種純客戶端呈現），因為手牌 tile entity 是伺服器端共用的實體，
 * 排序結果必須由伺服器套用才會反映在實際世界座標上，見 [HandSortPreferenceStore] 使用端
 * `SetHandSortPreferenceUseCase` KDoc。
 *
 * 刻意純記憶體、不接進 `AuthoritativeStateStore`：這只是呈現偏好，不是遊戲正確性狀態，伺服器重啟
 * 後回到預設值即可，玩家重新連線時 client 會自動重送一次目前的偏好。
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
