package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricRoomParticipantDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricRoomParticipantRegistrar

/** 初始化後只提供已排序能力快照，不在登記期間辨識世界實體。 */
internal class FabricRoomParticipantRegistry : FabricRoomParticipantRegistrar {
    /** 以完整 ID 保存定義。 */
    private val entries = linkedMapOf<String, FabricRoomParticipantDefinition>()

    /** 凍結後重用排序結果，不在每次實體查詢重新排序。 */
    private var ordered: List<FabricRoomParticipantDefinition>? = null

    override val registrationKeys: Set<String> get() = entries.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: FabricRoomParticipantDefinition) {
        check(!isFrozen) { "Room participant registry is frozen" }
        require(definition.id !in entries) { "Duplicate room participant provider: ${definition.id}" }
        require(entries.size < MAX_PROVIDERS) { "Too many room participant providers; maximum=$MAX_PROVIDERS" }
        entries[definition.id] = definition
    }

    /** 依優先權與完整 ID 排序的獨立快照。 */
    fun definitions(): List<FabricRoomParticipantDefinition> = (ordered ?: sortedDefinitions()).toList()

    /** 完成登記後拒絕寫入。 */
    fun freeze() {
        ordered = sortedDefinitions()
        isFrozen = true
    }

    /** 先比較優先權，再以完整識別碼穩定排序。 */
    private fun sortedDefinitions(): List<FabricRoomParticipantDefinition> = entries.values.sortedWith(compareByDescending<FabricRoomParticipantDefinition> { it.priority }.thenBy { it.id })

    /** 辨識工作量的登記上限，超出時在初始化明確拒絕。 */
    private companion object {
        /** 每次辨識最多檢查的能力數。 */
        const val MAX_PROVIDERS: Int = 64
    }
}
