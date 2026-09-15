package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DefaultGameConfigProvider
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import org.koin.core.annotation.Single

/** Fabric 內建的新房間預設設定提供者，目前使用四人日本麻將。 */
@Single
class BuiltInDefaultGameConfigProvider : DefaultGameConfigProvider {
    /** 建立一份使用內建四人日本麻將規則與預設流程選項的設定。 */
    override fun create(): GameConfig = GameConfig(RiichiRuleConfig())
}
