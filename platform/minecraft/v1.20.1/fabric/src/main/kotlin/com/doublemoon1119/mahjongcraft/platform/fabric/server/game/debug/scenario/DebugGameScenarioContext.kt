package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import kotlin.uuid.Uuid

/**
 * 建立 development-only 權威對局情境的受控輸入。
 *
 * @property currentGame 目前同桌的權威遊戲。
 * @property invokingPlayerId 執行載入指令的同桌玩家。
 * @property componentRegistry 以正式開局流程建立情境時初始化規則組件所用的組件登記。
 */
data class DebugGameScenarioContext(
    val currentGame: Game,
    val invokingPlayerId: Uuid,
    val componentRegistry: ComponentRegistry,
)
