package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * [DebugNotenDeclarationRiichiRuleModule] 中，玩家在一般流局結算前宣告不聽的動作。
 *
 * 記錄在玩家本局的動作紀錄裡；一般流局結算把有這個動作的玩家當作不聽。
 */
internal data object DebugDeclareNotenAction : ExtensionGameAction {
    override val id: String = MahjongCraftMetadata.id("debug_declare_noten")
}
