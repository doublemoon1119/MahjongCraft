package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import kotlin.reflect.KClass
import kotlin.uuid.Uuid

/** 將一種規則擴充動作及其選牌結果轉換為可執行命令。 */
fun interface ExtensionGameActionCommandFactory<A : ExtensionGameAction> {
    /** 建立命令；選牌資料不符合動作契約時回傳 null。 */
    fun create(action: A, selectedTileIds: List<Uuid>): ExtensionGameCommand?
}

/** 管理規則擴充動作命令 factory 的可凍結註冊表。 */
class ExtensionGameActionCommandFactoryRegistry {
    /** 保留具體動作型別的 factory 包裝。 */
    private class Entry<A : ExtensionGameAction>(val factory: ExtensionGameActionCommandFactory<A>)

    /** 依動作具體型別索引的 factory。 */
    private val entries = mutableMapOf<KClass<out ExtensionGameAction>, Entry<*>>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 註冊一種擴充動作的命令 factory。 */
    fun <A : ExtensionGameAction> register(
        actionClass: KClass<A>,
        factory: ExtensionGameActionCommandFactory<A>,
    ) {
        check(!frozen) { "Extension game action command factory registry is frozen" }
        require(actionClass !in entries) { "Command factory already registered for $actionClass" }
        entries[actionClass] = Entry(factory)
    }

    /** 凍結註冊表。 */
    fun freeze() {
        frozen = true
    }

    /** 查詢指定擴充動作是否已有命令 factory。 */
    fun isRegistered(actionClass: KClass<out ExtensionGameAction>): Boolean = actionClass in entries

    /** 建立指定擴充動作的命令；未知動作或非法選牌安全回傳 null。 */
    @Suppress("UNCHECKED_CAST")
    fun createCommand(action: ExtensionGameAction, selectedTileIds: List<Uuid>): ExtensionGameCommand? {
        val entry = entries[action::class] as? Entry<ExtensionGameAction> ?: return null
        return entry.factory.create(action, selectedTileIds)
    }
}

/** 登記 MahjongCraft 內建日麻立直動作的命令 factory。 */
fun ExtensionGameActionCommandFactoryRegistry.registerRiichiGameActionCommandFactory() {
    register(RiichiGameAction.Riichi::class) { _, selectedTileIds ->
        selectedTileIds.singleOrNull()?.let(::RiichiGameCommand)
    }
}
