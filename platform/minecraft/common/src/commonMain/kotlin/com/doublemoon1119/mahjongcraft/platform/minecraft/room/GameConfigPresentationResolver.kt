package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry

/** 一次解析後供 RoomScreen 與聊天 hover 共用的規則設定呈現。 */
data class ResolvedGameConfigPresentation(
    val ruleModuleId: String,
    val definition: GameConfigPresentationDefinition?,
    val valuesByFieldId: Map<String, GameConfigPresentationValue>,
)

/** 集中解析規則 ID、schema 與欄位值，避免 GUI 和聊天各自重建同一套判斷。 */
class GameConfigPresentationResolver(
    private val presentations: GameConfigPresentationRegistry,
    private val modules: MahjongModuleRegistry,
) {
    fun resolve(config: GameConfig): ResolvedGameConfigPresentation {
        val moduleId = modules.getModule(config.ruleConfig).id
        val definition = presentations.find(moduleId)
        return ResolvedGameConfigPresentation(
            ruleModuleId = moduleId,
            definition = definition,
            valuesByFieldId = definition?.fields?.associate { it.id to it.read(config) }.orEmpty(),
        )
    }
}
