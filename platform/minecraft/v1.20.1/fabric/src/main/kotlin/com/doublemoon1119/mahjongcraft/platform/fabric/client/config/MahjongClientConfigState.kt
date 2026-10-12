package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * MahjongCraft client 端持久化設定；跟 server 端設定（[FabricServerConfigManager]）
 * 完全分開存放，互不影響。TOML 欄位一律 kebab-case，跟 server 設定同一套命名慣例。
 *
 * @property tileLabelsEnabled 是否在牌面角落顯示數字／字母輔助標籤，方便辨識牌面。
 * @property autoSortHandEnabled 自動整理手牌的永久偏好；另須同步至伺服器，由伺服器調整手牌實體位置。
 * @property hudLayout 內建遊戲 HUD 的本機位置設定。
 * @property presentationVisibility 可由玩家個別關閉的 HUD、遊戲面板與短暫視覺回饋。
 */
@Serializable
data class MahjongClientConfigState(
    @SerialName("tile-labels-enabled")
    val tileLabelsEnabled: Boolean = false,

    @SerialName("auto-sort-hand-enabled")
    val autoSortHandEnabled: Boolean = true,

    @SerialName("hud-layout")
    val hudLayout: MahjongHudLayoutConfig = MahjongHudLayoutConfig(),

    @SerialName("presentation-visibility")
    val presentationVisibility: MahjongPresentationVisibilityConfig = MahjongPresentationVisibilityConfig(),
)
