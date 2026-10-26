package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolverDefinition
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiGameEventPublicMarkerResolver
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules

/**
 * 將舊的事實 fixture 投影成事件批次，讓既有判定測試繼續驗證正式事件路徑。
 *
 * 這個 helper 只存在測試 source set，production 不保留從事實直接判定通用成果的入口。
 */
internal fun GameAchievementDetector.detect(facts: CommittedGameFacts): List<PlayerAchievements> {
    val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
    val markers = GameEventPublicMarkerResolverRegistry().apply {
        register(GameEventPublicMarkerResolverDefinition(BuiltInRuleModuleIds.RIICHI, RiichiGameEventPublicMarkerResolver))
        register(GameEventPublicMarkerResolverDefinition(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, RiichiGameEventPublicMarkerResolver))
    }
    val batch = GameEventProjector(modules, markerResolvers = markers).projectBatch(facts) ?: return emptyList()
    return detect(batch)
}
