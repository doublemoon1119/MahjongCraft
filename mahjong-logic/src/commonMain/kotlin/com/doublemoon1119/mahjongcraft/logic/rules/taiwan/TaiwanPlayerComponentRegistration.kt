package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.api.registration.ComponentRegistrar
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRoundTransition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer

/**
 * 登記台灣麻將玩家組件、每局重設的生命週期與公開範圍。
 *
 * 宣告聽牌對所有讀取者公開；過水只交付本人；包牌責任只供結算使用，不交付任何讀取者。
 *
 * @param registry 本次執行尚未凍結的組件登記。
 */
fun registerTaiwanPlayerComponents(registry: ComponentRegistrar) {
    registry.register(TaiwanPlayerComponents.READY_DECLARATION)
    registry.registerView(TaiwanPlayerComponentViews.READY_DECLARATION)
    registry.registerLifecycle(resetEachRound(TaiwanPlayerComponents.READY_DECLARATION))
    registry.registerProjection(
        definition = ComponentProjectionDefinition(
            id = "mahjongcraft:taiwan/projection/ready_declaration",
            ownerType = BuiltInComponentOwners.PLAYER,
            component = TaiwanPlayerComponents.READY_DECLARATION,
            viewType = TaiwanPlayerComponentViews.READY_DECLARATION,
            appliesTo = { _, config -> config is TaiwanRuleConfig },
            project = { _, value -> value },
        ),
    )
    registry.register(TaiwanPlayerComponents.PAO_LIABILITY)
    registry.registerLifecycle(resetEachRound(TaiwanPlayerComponents.PAO_LIABILITY))
    registry.register(TaiwanPlayerComponents.PASSED_WIN)
    registry.registerView(TaiwanPlayerComponentViews.PASSED_WIN)
    registry.registerLifecycle(resetEachRound(TaiwanPlayerComponents.PASSED_WIN))
    registry.registerProjection(
        definition = ComponentProjectionDefinition(
            id = "mahjongcraft:taiwan/projection/passed_win",
            ownerType = BuiltInComponentOwners.PLAYER,
            component = TaiwanPlayerComponents.PASSED_WIN,
            viewType = TaiwanPlayerComponentViews.PASSED_WIN,
            appliesTo = { _, config -> config is TaiwanRuleConfig },
            project = { context, _ ->
                val owner = context.owner
                Unit.takeIf { owner != null && (context.reader as? ComponentReadContext.Player)?.playerId == owner.id }
            },
        ),
    )
}

/**
 * 建立只適用台灣麻將、每局重設為未設定的玩家組件生命週期。
 *
 * @param component 要登記的玩家組件。
 * @return 生命週期定義。
 */
private fun <T : Any> resetEachRound(component: ComponentType<T>): ComponentLifecycleDefinition<MahjongPlayer, T> = ComponentLifecycleDefinition(
    component = component,
    ownerType = BuiltInComponentOwners.PLAYER,
    appliesTo = { _, config -> config is TaiwanRuleConfig },
    required = false,
    roundTransition = ComponentRoundTransition.RESET,
    initialValue = { null },
)
