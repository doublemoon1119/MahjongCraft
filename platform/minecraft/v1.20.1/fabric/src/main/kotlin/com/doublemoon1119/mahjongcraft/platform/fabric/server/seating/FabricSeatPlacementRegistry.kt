package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricSeatPlacementRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatPlacementDefinition

/** 保存初始化期間的放置能力，執行期只讀取排序後的獨立清單。 */
internal class FabricSeatPlacementRegistry : FabricSeatPlacementRegistrar {
    /** 依完整識別碼保存定義。 */
    private val definitions = linkedMapOf<String, FabricSeatPlacementDefinition>()

    override val registrationKeys: Set<String>
        get() = definitions.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: FabricSeatPlacementDefinition) {
        check(!isFrozen) { "Seat placement registry is frozen" }
        require(definitions.putIfAbsent(definition.id, definition) == null) { "Duplicate seat placement: ${definition.id}" }
    }

    /** 初始化完成後停止登記。 */
    fun freeze() {
        isFrozen = true
    }

    /**
     * 取得依優先順序排列的能力快照。
     *
     * @return 優先值由大到小，同值依識別碼排列的清單。
     */
    fun entries(): List<FabricSeatPlacementDefinition> = definitions.values.sortedWith(
        compareByDescending<FabricSeatPlacementDefinition> { it.priority }.thenBy { it.id },
    )
}
