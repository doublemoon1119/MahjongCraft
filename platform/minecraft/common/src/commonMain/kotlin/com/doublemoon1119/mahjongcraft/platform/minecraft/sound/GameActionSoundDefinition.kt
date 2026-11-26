package com.doublemoon1119.mahjongcraft.platform.minecraft.sound

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 單一規則模組動作對應的語音定義。
 *
 * @property ruleModuleId 提供此對應的規則模組完整 ID。
 * @property actionId [GameAction.soundPresentationId] 回傳的穩定動作 ID。
 * @property presentation 實際播放設定。
 */
data class GameActionSoundDefinition(
    val ruleModuleId: String,
    val actionId: String,
    val presentation: GameActionSoundPresentation,
) {
    init {
        NamespacedId.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        NamespacedId.requireValid(actionId) { "Game action ID must be namespaced: $actionId" }
    }
}
