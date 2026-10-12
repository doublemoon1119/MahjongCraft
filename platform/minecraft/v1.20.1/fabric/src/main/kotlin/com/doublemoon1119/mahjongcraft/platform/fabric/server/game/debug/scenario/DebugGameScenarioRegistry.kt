package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import org.koin.core.annotation.Single

/** 保存 MahjongCraft 內建 development-only 權威對局情境的凍結式 registry。 */
@Single
class DebugGameScenarioRegistry {
    /** 依 ID 排序的不可變情境索引。 */
    private val scenariosById: Map<String, DebugGameScenario> = RiichiDebugGameScenarios.all
        .onEach { scenario -> NamespacedId.requireValid(scenario.id) { "Invalid debug scenario ID: ${scenario.id}" } }
        .associateBy(DebugGameScenario::id)
        .also { indexed -> require(indexed.size == RiichiDebugGameScenarios.all.size) { "Duplicate debug scenario ID" } }
        .toSortedMap()

    /**
     * 取得指定識別碼的情境。
     *
     * @param id 情境的完整識別碼。
     * @return 已登記的情境；未知識別碼為 null。
     */
    fun get(id: String): DebugGameScenario? = scenariosById[id]

    /**
     * 取得全部已登記情境。
     *
     * @return 依識別碼排序的情境清單副本。
     */
    fun getAll(): List<DebugGameScenario> = scenariosById.values.toList()
}
