package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import kotlin.reflect.KClass

/**
 * 管理擴充動作型別與 AI handler 的可凍結註冊表。
 *
 * @property moduleRegistry 依決策情境的對局設定取得交給 handler 的規則模組。
 */
class ExtensionGameActionAiRegistry(private val moduleRegistry: MahjongModuleRegistry) {
    /**
     * 保存單一動作型別與其命令候選建立方式的對應。
     *
     * @param A 此項目可處理的擴充動作型別。
     * @property handler 為此型別建立 AI 命令候選的策略。
     */
    private class Entry<A : ExtensionGameAction>(val handler: ExtensionGameActionAiHandler<A>)

    /** 依擴充動作具體型別索引的 handler。 */
    private val entries = mutableMapOf<KClass<out ExtensionGameAction>, Entry<*>>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 目前已登記動作型別的穩定類別名稱快照。 */
    val registrationKeys: Set<String> get() = entries.keys.mapTo(mutableSetOf()) { it.qualifiedName ?: it.toString() }

    /**
     * 註冊一種擴充動作的 AI 命令候選建立方式。
     *
     * @param A 要登記的擴充動作型別。
     * @param definition 動作具體型別與 handler。
     * @throws IllegalStateException 註冊表已凍結。
     * @throws IllegalArgumentException 此動作型別已登記。
     */
    fun <A : ExtensionGameAction> register(definition: ExtensionGameActionAiDefinition<A>) {
        check(!frozen) { "Extension game action AI registry is frozen" }
        require(definition.actionClass !in entries) { "AI handler already registered for ${definition.actionClass}" }
        entries[definition.actionClass] = Entry(definition.handler)
    }

    /** 凍結註冊表。 */
    fun freeze() {
        frozen = true
    }

    /**
     * 查詢指定擴充動作是否已有 AI 處理策略。
     *
     * @param actionClass 要查詢的具體動作型別。
     * @return 此型別已登記時為 true。
     */
    fun isRegistered(actionClass: KClass<out ExtensionGameAction>): Boolean = actionClass in entries

    /**
     * 以已登記的處理策略轉換擴充動作；未登記此動作型別時回傳空清單。
     *
     * @param action 要轉換的擴充動作。
     * @param context 用來選擇規則模組並建立命令的決策資訊。
     * @return 處理策略產生的命令候選；不攔截規則查找或處理策略拋出的例外。
     */
    @Suppress("UNCHECKED_CAST")
    fun createCandidates(action: ExtensionGameAction, context: AiDecisionContext): List<ExtensionCommandCandidate> {
        val entry = entries[action::class] as? Entry<ExtensionGameAction> ?: return emptyList()
        return entry.handler.createCandidates(action, context, moduleRegistry.getModule(context.snapshot.config))
    }
}
