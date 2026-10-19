package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry

/**
 * 集中解析規則 ID、schema 與欄位值，避免 GUI 和聊天各自重建同一套判斷。
 *
 * @property presentations 設定呈現定義 registry。
 * @property modules 規則模組 registry。
 */
class GameConfigPresentationResolver(
    private val presentations: GameConfigPresentationRegistry,
    private val modules: MahjongModuleRegistry,
) {
    /**
     * 解析設定所屬規則模組的呈現定義與各欄位目前值。
     *
     * @param config 要解析的遊戲設定。
     * @return 規則模組 ID、可用定義及已解析欄位值。
     */
    fun resolve(config: GameConfig): ResolvedGameConfigPresentation {
        val moduleId = modules.getModule(config.ruleConfig).id
        val definition = presentations.find(moduleId, config.ruleConfig)
        return ResolvedGameConfigPresentation(
            ruleModuleId = moduleId,
            definition = definition,
            valuesByFieldId = definition?.fields?.associate { it.id to it.read(config) }.orEmpty(),
        )
    }
}
