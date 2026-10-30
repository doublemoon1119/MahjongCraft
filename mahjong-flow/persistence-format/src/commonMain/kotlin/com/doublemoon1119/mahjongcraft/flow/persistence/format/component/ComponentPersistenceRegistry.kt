package com.doublemoon1119.mahjongcraft.flow.persistence.format.component

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.UnregisteredPersistenceTypeException
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.persistenceJson
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import kotlinx.serialization.json.JsonObject

/** 登記組件的保存轉換；找不到定義時拒絕讀寫，不當成空值。 */
class ComponentPersistenceRegistry {
    /**
     * 捕捉一項轉換的具體型別，避免對外暴露未檢查的值轉型。
     *
     * @param V 組件值型別。
     * @param D 保存 DTO 型別。
     * @property definition 組件與保存轉換。
     */
    private class Entry<V : Any, D : Any>(val definition: ComponentPersistenceDefinition<V, D>) {
        /**
         * 從型別集合讀取並編碼已驗證值。
         *
         * @param components 來源集合。
         * @return 包含完整 ID 的保存資料。
         */
        fun encode(components: ComponentSet): TypedPersistenceDto {
            val value = checkNotNull(components[definition.component])
            val payload = persistenceJson.encodeToJsonElement(definition.serializer, definition.toDto(value)) as? JsonObject
                ?: error("Component DTO must encode to a JSON object")
            return TypedPersistenceDto(definition.component.id, payload)
        }

        /**
         * 解碼並透過原定義驗證及捕捉值。
         *
         * @param source 目前已解碼集合。
         * @param dto 這一項保存資料。
         * @return 新增該值的不可變集合。
         */
        fun decode(source: ComponentSet, dto: TypedPersistenceDto): ComponentSet {
            val value = definition.toDomain(persistenceJson.decodeFromJsonElement(definition.serializer, dto.payload))
            return source.with(definition.component, value)
        }
    }

    /** 依組件完整 ID 保存型別封裝的轉換。 */
    private val entries = linkedMapOf<String, Entry<*, *>>()

    /** 是否禁止新增轉換。 */
    private var frozen = false

    /** 已登記 ID 的獨立集合。 */
    val registrationKeys: Set<String> get() = entries.keys.toSet()

    /** 所有轉換所使用的組件定義，用於啟動時核對同一份 runtime registry。 */
    val componentTypes: List<ComponentType<*>> get() = entries.values.map { it.definition.component }

    /**
     * 登記完整轉換，不能重複或在凍結後改動。
     *
     * @param V 組件值型別。
     * @param D 保存 DTO 型別。
     * @param definition 組件及雙向保存轉換。
     */
    fun <V : Any, D : Any> register(definition: ComponentPersistenceDefinition<V, D>) {
        check(!frozen) { "Component persistence registry is frozen" }
        require(definition.component.id !in entries) { "Duplicate component persistence ID: ${definition.component.id}" }
        entries[definition.component.id] = Entry(definition)
    }

    /** 禁止新增或覆寫轉換。 */
    fun freeze() {
        frozen = true
    }

    /**
     * 依組件 ID 穩定排序後保存完整值，沒有未登記的靜默退回路徑。
     *
     * @param components 權威不可變組件。
     * @return 保存資料清單。
     */
    fun encode(components: ComponentSet): List<TypedPersistenceDto> = components.types.map { type ->
        val entry = entries[type.id] ?: throw UnregisteredPersistenceTypeException(type.id)
        entry.encode(components)
    }

    /**
     * 拒絕重複或未知 ID，還原為使用已登記定義的組件集合。
     *
     * @param values 保存資料。
     * @return 經定義驗證的組件集合。
     */
    fun decode(values: List<TypedPersistenceDto>): ComponentSet {
        require(values.map { it.typeKey }.distinct().size == values.size) { "Duplicate saved component ID" }
        return values.fold(ComponentSet.EMPTY) { result, dto ->
            val entry = entries[dto.typeKey] ?: throw UnregisteredPersistenceTypeException(dto.typeKey)
            entry.decode(result, dto)
        }
    }
}
