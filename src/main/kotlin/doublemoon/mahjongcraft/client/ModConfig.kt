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
    val displayTableLabels: Boolean = true,
    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    val quickActions: QuickActions = QuickActions()
) : ConfigData {

    /**
     * 快捷操作
     *
     * @param displayHud 顯示遊戲內的 HUD
     * @param autoArrange 自動理牌
     * @param autoCallWin 自動和牌 (每回合結束時自動重置)
     * @param noChiiPonKan 不吃碰槓 (每回合結束時自動重置)
     * @param autoDrawAndDiscard 自動摸切 (每回合結束時自動重置)
     * */
    @Config(name = "quick_action")
    data class QuickActions(
        val displayHud: Boolean = true,
        val autoArrange: Boolean = true,
        @ConfigEntry.Gui.Tooltip var autoCallWin: Boolean = false,
        @ConfigEntry.Gui.Tooltip var noChiiPonKan: Boolean = false,
        @ConfigEntry.Gui.Tooltip var autoDrawAndDiscard: Boolean = false
    ) : ConfigData
}