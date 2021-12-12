package doublemoon.mahjongcraft.client

import doublemoon.mahjongcraft.MOD_ID
import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config

private const val DEFAULT_COLOR = 0x7f271c1d

/**
 * 這個模組的設定檔
 *
 * @param displayTableLabels 是否要顯示麻將桌方塊關於麻將遊戲的標籤
 * */
@Config(name = MOD_ID)
data class ModConfig(
    var displayTableLabels: Boolean = true,
    val quickActions: QuickActions = QuickActions()
) : ConfigData {

    /**
     * 快捷操作
     *
     * @param displayHudWhenPlaying 在遊玩中的時候顯示 HUD
     * @param hudAttribute 一些 HUD 的屬性
     * @param autoArrange 自動理牌
     * @param autoCallWin 自動和牌 (每回合結束時自動重置)
     * @param noChiiPonKan 不吃碰槓 (每回合結束時自動重置)
     * @param autoDrawAndDiscard 自動摸切 (每回合結束時自動重置)
     * */
    data class QuickActions(
        var displayHudWhenPlaying: Boolean = true,
        val hudAttribute: HudAttribute = HudAttribute(0.0, 0.6, DEFAULT_COLOR),
        var autoArrange: Boolean = true,
        var autoCallWin: Boolean = false,
        var noChiiPonKan: Boolean = false,
        var autoDrawAndDiscard: Boolean = false
    ) : ConfigData
}

/**
 * HUD 的一些屬性
 *
 * @param x x 軸的百分比座標 (max: 1.0, min: 0.0)
 * @param y y 軸的百分比座標 (max: 1.0, min: 0.0)
 * @param backgroundColor 背景顏色
 * */
data class HudAttribute(
    var x: Double,
    var y: Double,
    var backgroundColor: Int
) : ConfigData