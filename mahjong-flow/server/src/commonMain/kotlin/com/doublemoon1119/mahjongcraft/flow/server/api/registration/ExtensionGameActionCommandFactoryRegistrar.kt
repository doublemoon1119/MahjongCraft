package com.doublemoon1119.mahjongcraft.flow.server.api.registration

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryDefinition
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction

/** 擴充登記擴充動作的命令工廠時取得的登記介面；查詢、建立命令與凍結由 MahjongCraft 進行。 */
interface ExtensionGameActionCommandFactoryRegistrar {
    /** 目前已登記動作型別的穩定類別名稱快照。 */
    val registrationKeys: Set<String>

    /**
     * 註冊一種擴充動作的命令 factory；註冊表凍結後或同一動作型別已登記時會失敗。
     *
     * @param A 動作型別。
     * @param definition 動作類別與命令 factory。
     */
    fun <A : ExtensionGameAction> register(definition: ExtensionGameActionCommandFactoryDefinition<A>)
}
