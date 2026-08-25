package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import kotlin.reflect.KClass

/** 將一種擴充動作轉換成 AI 可執行命令的策略。 */
fun interface ExtensionGameActionAiHandler<A : ExtensionGameAction> {
    /** 依目前情境建立所有可安全執行的命令候選。 */
    fun createCommands(action: A, context: AiDecisionContext): List<GameCommand>
}

/** 管理擴充動作型別與 AI handler 的可凍結註冊表。 */
class ExtensionGameActionAiRegistry {
    /** 未擦除型別前的單一 handler 包裝。 */
    private class Entry<A : ExtensionGameAction>(val handler: ExtensionGameActionAiHandler<A>)

    /** 依擴充動作具體型別索引的 handler。 */
    private val entries = mutableMapOf<KClass<out ExtensionGameAction>, Entry<*>>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

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
    fun createCommands(action: ExtensionGameAction, context: AiDecisionContext): List<GameCommand> {
        val entry = entries[action::class] as? Entry<ExtensionGameAction> ?: return emptyList()
        return entry.handler.createCommands(action, context)
    }
}

/** 登記 MahjongCraft 內建規則提供的 AI action handler。 */
fun ExtensionGameActionAiRegistry.registerRiichiGameActionHandler(moduleRegistry: MahjongModuleRegistry) {
    register(RiichiGameAction.Riichi::class) { _, context ->
        val player = context.snapshot.players.first { it.id == context.selfId }
        val calculator = moduleRegistry.getModule(context.snapshot.config).createShantenCalculator()
        val visibleTiles = (player.hand.standingTiles + listOfNotNull(player.hand.lastDrawn)).mapNotNull { snapshot ->
            snapshot.tile?.let { IdentifiedTile(snapshot.id, it) }
        }
        val candidates = visibleTiles.filter { candidate ->
            val remainingTiles = visibleTiles.filterNot { it.id == candidate.id }
            calculator.calculate(Hand(tiles = remainingTiles)) is ShantenResult.Tenpai
        }
        candidates.map { GameCommand.Extension(RiichiGameCommand(it.id)) }
    }
}
