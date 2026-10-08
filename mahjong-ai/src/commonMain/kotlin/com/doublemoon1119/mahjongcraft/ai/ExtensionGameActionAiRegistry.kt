package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import kotlin.reflect.KClass
import kotlin.uuid.Uuid

/**
 * 擴充動作轉成的一個 AI 命令候選。
 *
 * 命令本身的內容由規則定義，AI 無法解讀；[discardTileId] 與 [declaration] 以規則中立的形式說明執行後的結果，
 * 讓評估局面的策略可以把它當成「宣告後打出某張牌」比較。
 *
 * @property command 可直接送出的命令。
 * @property discardTileId 命令執行時會打出的牌；不打牌的命令為 null。
 * @property declaration 命令宣告的擴充動作；不宣告任何動作時為 null。
 * @property setAsideTileId 命令執行時會移出手牌、之後補一張牌的那張牌（例如三人日麻的拔北）；沒有時為 null。
 */
data class ExtensionCommandCandidate(
    val command: GameCommand,
    val discardTileId: Uuid? = null,
    val declaration: GameAction.Extension? = null,
    val setAsideTileId: Uuid? = null,
)

/** 將一種擴充動作轉換成 AI 可執行命令的策略。 */
fun interface ExtensionGameActionAiHandler<A : ExtensionGameAction> {
    /** 依目前情境與這一局的 [module] 建立所有可安全執行的命令候選。 */
    fun createCandidates(
        action: A,
        context: AiDecisionContext,
        module: MahjongRuleModule<*>,
    ): List<ExtensionCommandCandidate>
}

/**
 * 管理擴充動作型別與 AI handler 的可凍結註冊表。
 *
 * @property moduleRegistry 依決策情境的對局設定取得交給 handler 的規則模組。
 */
class ExtensionGameActionAiRegistry(private val moduleRegistry: MahjongModuleRegistry) {
    /** 未擦除型別前的單一 handler 包裝。 */
    private class Entry<A : ExtensionGameAction>(val handler: ExtensionGameActionAiHandler<A>)

    /** 依擴充動作具體型別索引的 handler。 */
    private val entries = mutableMapOf<KClass<out ExtensionGameAction>, Entry<*>>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 目前已登記動作型別的穩定類別名稱快照。 */
    val registrationKeys: Set<String> get() = entries.keys.mapTo(mutableSetOf()) { it.qualifiedName ?: it.toString() }

    /** 註冊一種擴充動作的 AI handler。 */
    fun <A : ExtensionGameAction> register(
        actionClass: KClass<A>,
        handler: ExtensionGameActionAiHandler<A>,
    ) {
        check(!frozen) { "Extension game action AI registry is frozen" }
        require(actionClass !in entries) { "AI handler already registered for $actionClass" }
        entries[actionClass] = Entry(handler)
    }

    /** 凍結註冊表。 */
    fun freeze() {
        frozen = true
    }

    /** 查詢指定擴充動作是否已有 AI handler。 */
    fun isRegistered(actionClass: KClass<out ExtensionGameAction>): Boolean = actionClass in entries

    /** 將擴充動作轉換成命令候選；未知或無法安全決策的動作回傳空清單。 */
    @Suppress("UNCHECKED_CAST")
    fun createCandidates(action: ExtensionGameAction, context: AiDecisionContext): List<ExtensionCommandCandidate> {
        val entry = entries[action::class] as? Entry<ExtensionGameAction> ?: return emptyList()
        return entry.handler.createCandidates(action, context, moduleRegistry.getModule(context.snapshot.config))
    }
}
