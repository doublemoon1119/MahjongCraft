package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import org.koin.core.annotation.Single

/** 保存 MahjongCraft 內建 development-only 權威對局情境的凍結式 registry。 */
@Single
class DebugGameScenarioRegistry {
    /** 依 ID 排序的不可變情境索引。 */
    private val scenariosById: Map<String, DebugGameScenario> = RiichiDebugGameScenarios.all
        .onEach { scenario -> require(NAMESPACED_ID.matches(scenario.id)) { "Invalid debug scenario ID: ${scenario.id}" } }
        .associateBy(DebugGameScenario::id)
        .also { indexed -> require(indexed.size == RiichiDebugGameScenarios.all.size) { "Duplicate debug scenario ID" } }
        .toSortedMap()

    /** 取得指定 ID 的情境；未知 ID 回傳 null。 */
    fun get(id: String): DebugGameScenario? = scenariosById[id]

    /** 取得依 ID 排序的全部已登記情境。 */
    fun getAll(): List<DebugGameScenario> = scenariosById.values.toList()

    private companion object {
        /** Debug scenario ID 採用的最小 namespaced ID 格式。 */
        val NAMESPACED_ID: Regex = Regex("^[a-z0-9_.-]+:[a-z0-9_./-]+$")
    }
}
