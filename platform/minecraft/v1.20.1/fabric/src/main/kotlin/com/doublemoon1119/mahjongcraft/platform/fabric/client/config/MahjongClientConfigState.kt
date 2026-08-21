package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * MahjongCraft client 端持久化設定；跟 server 端設定（[FabricServerConfigManager]）
 * 完全分開存放，互不影響。TOML 欄位一律 kebab-case，跟 server 設定同一套命名慣例。
 */
@Serializable
data class MahjongClientConfigState(
    /**
     * 是否在牌面角落顯示輔助標籤（數字／字母），給非中文圈玩家辨識牌面用；透過
     * `/mahjongcraft_client label toggle` 切換。
     */
    @SerialName("tile-labels-enabled")
    val tileLabelsEnabled: Boolean = false,

    /**
     * 是否啟用自動整理手牌；透過 `/mahjongcraft_client hand_sort toggle` 切換。跟
     * [tileLabelsEnabled] 不同，這個偏好還需要同步給伺服器（見 `FabricHandSortCommand` KDoc）——
     * 手牌 tile entity 是伺服器端共用的實體，排序結果必須由伺服器套用才會反映在實際世界座標上。
     */
    @SerialName("auto-sort-hand-enabled")
    val autoSortHandEnabled: Boolean = true,
)
