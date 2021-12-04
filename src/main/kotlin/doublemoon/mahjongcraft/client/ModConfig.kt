package doublemoon.mahjongcraft.client

import doublemoon.mahjongcraft.MOD_ID
import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry

/**
 * 這個模組的設定檔
 *
 * @param displayTableLabels 是否要顯示麻將桌方塊關於麻將遊戲的標籤
 * */
@Config(name = MOD_ID)
data class ModConfig(
    var displayTableLabels: Boolean = true,
    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    val quickActions: QuickActions = QuickActions()
) : ConfigData {

    /**
     * 快捷操作
     *
     * @param displayHudWhenPlaying 在遊玩中的時候顯示 HUD
     * @param autoArrange 自動理牌
     * @param autoCallWin 自動和牌 (每回合結束時自動重置)
     * @param noChiiPonKan 不吃碰槓 (每回合結束時自動重置)
     * @param autoDrawAndDiscard 自動摸切 (每回合結束時自動重置)
     * */
    data class QuickActions(
        var displayHudWhenPlaying: Boolean = true,
        @ConfigEntry.Gui.Excluded val hudPosition: HudPosition = HudPosition(0.0, 0.7),
        var autoArrange: Boolean = true,
        @ConfigEntry.Gui.Tooltip var autoCallWin: Boolean = false,
        @ConfigEntry.Gui.Tooltip var noChiiPonKan: Boolean = false,
        @ConfigEntry.Gui.Tooltip var autoDrawAndDiscard: Boolean = false
    ) : ConfigData
}

/**
 * HUD 的位置, 本來要用 Pair 但是不支持
 *
 * @param x x 軸的百分比座標 (max: 1.0, min: 0.0)
 * @param y y 軸的百分比座標 (max: 1.0, min: 0.0)
 * */
data class HudPosition(
    var x: Double,
    var y: Double
) : ConfigData