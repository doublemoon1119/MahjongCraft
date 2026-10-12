package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import kotlin.reflect.KClass
import kotlin.uuid.Uuid

/** 管理規則擴充動作命令 factory 的可凍結註冊表。 */
class ExtensionGameActionCommandFactoryRegistry {
    /** 保留具體動作型別的 factory 包裝。 */
    private class Entry<A : ExtensionGameAction>(val factory: ExtensionGameActionCommandFactory<A>)

    /** 依動作具體型別索引的 factory。 */
    private val entries = mutableMapOf<KClass<out ExtensionGameAction>, Entry<*>>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 目前已登記動作型別的穩定類別名稱快照。 */
    val registrationKeys: Set<String> get() = entries.keys.mapTo(mutableSetOf()) { it.qualifiedName ?: it.toString() }

    /**
     * 註冊一種擴充動作的命令 factory；註冊表凍結後或同一動作型別已登記時會失敗。
     *
     * @param A 動作型別。
     * @param actionClass 要登記的動作類別。
     * @param factory 將該動作轉為命令的 factory。
     */
    fun <A : ExtensionGameAction> register(
        actionClass: KClass<A>,
        factory: ExtensionGameActionCommandFactory<A>,
    ) {
        check(!frozen) { "Extension game action command factory registry is frozen" }
        require(actionClass !in entries) { "Command factory already registered for $actionClass" }
        entries[actionClass] = Entry(factory)
    }

    /** 凍結註冊表；後續註冊會失敗。重複呼叫不改變結果。 */
    fun freeze() {
        frozen = true
    }

    /**
     * 查詢指定擴充動作是否已有命令 factory。
     *
     * @param actionClass 要查詢的動作類別。
     * @return 已登記時為 `true`。
     */
    fun isRegistered(actionClass: KClass<out ExtensionGameAction>): Boolean = actionClass in entries

    /**
     * 建立指定擴充動作的命令；未知動作或 factory 拒絕選牌資料時回傳 null。
     *
     * @param action 要轉換的動作。
     * @param selectedTileIds 玩家選取的實體牌識別碼。
     * @return 建立的命令；沒有對應 factory 或選牌資料不符合動作契約時為 null。
     */
    @Suppress("UNCHECKED_CAST")
    fun createCommand(action: ExtensionGameAction, selectedTileIds: List<Uuid>): ExtensionGameCommand? {
        val entry = entries[action::class] as? Entry<ExtensionGameAction> ?: return null
        return entry.factory.create(action, selectedTileIds)
    }
}
