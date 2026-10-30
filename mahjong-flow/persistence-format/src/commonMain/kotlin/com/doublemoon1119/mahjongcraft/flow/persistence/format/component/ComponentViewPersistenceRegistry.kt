package com.doublemoon1119.mahjongcraft.flow.persistence.format.component

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.UnregisteredPersistenceTypeException
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.persistenceJson
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewType
import kotlinx.serialization.json.JsonObject

/** 保存提交時已裁切的投影；解碼只還原投影集合，不建立權威組件。 */
class ComponentViewPersistenceRegistry {
    /**
     * 捕捉一項轉換的具體型別，避免對外暴露未檢查的值轉型。
     *
     * @param V 投影值型別。
     * @param D 保存 DTO 型別。
     * @property definition 投影與保存轉換。
     */
    private class Entry<V : Any, D : Any>(val definition: ComponentViewPersistenceDefinition<V, D>) {
        /**
         * 從型別集合讀取並編碼已驗證值。
         *
         * @param views 來源集合。
         * @return 包含完整 ID 的保存資料。
         */
        fun encode(views: ComponentViewSet): TypedPersistenceDto {
            val value = checkNotNull(views[definition.viewType])
            val payload = persistenceJson.encodeToJsonElement(definition.serializer, definition.toDto(value)) as? JsonObject
                ?: error("Component view DTO must encode to a JSON object")
            return TypedPersistenceDto(definition.viewType.id, payload)
        }

        /**
         * 解碼並透過原定義驗證及捕捉值。
         *
         * @param source 目前已解碼集合。
         * @param dto 這一項保存資料。
         * @return 新增該值的不可變集合。
         */
        fun decode(source: ComponentViewSet, dto: TypedPersistenceDto): ComponentViewSet {
            val value = definition.toDomain(persistenceJson.decodeFromJsonElement(definition.serializer, dto.payload))
            return source.with(definition.viewType, value)
        }
    }

    /** 依投影完整 ID 保存型別封裝的轉換。 */
    private val entries = linkedMapOf<String, Entry<*, *>>()

    /** 是否禁止新增轉換。 */
    private var frozen = false

    /** 已登記 ID 的獨立集合。 */
    val registrationKeys: Set<String> get() = entries.keys.toSet()

    /** 所有轉換所使用的投影定義，用於啟動時核對同一份 runtime registry。 */
    val viewTypes: List<ComponentViewType<*>> get() = entries.values.map { it.definition.viewType }

    /**
     * 登記完整轉換，不能重複或在凍結後改動。
     *
     * @param V 投影值型別。
     * @param D 保存 DTO 型別。
     * @param definition 投影及雙向保存轉換。
     */
    fun <V : Any, D : Any> register(definition: ComponentViewPersistenceDefinition<V, D>) {
        check(!frozen) { "Component view persistence registry is frozen" }
        require(definition.viewType.id !in entries) { "Duplicate component view persistence ID: ${definition.viewType.id}" }
        entries[definition.viewType.id] = Entry(definition)
    }

    /** 禁止新增或覆寫轉換。 */
    fun freeze() {
        frozen = true
    }

    /**
     * 依投影 ID 穩定排序後保存已裁切的值，沒有未登記的靜默退回路徑。
     *
     * @param views 已裁切的不可變投影。
     * @return 保存資料清單。
     */
    fun encode(views: ComponentViewSet): List<TypedPersistenceDto> = views.types.map { type ->
        val entry = entries[type.id] ?: throw UnregisteredPersistenceTypeException(type.id)
        entry.encode(views)
    }

    /**
     * 拒絕重複或未知 ID，還原為使用已登記定義的投影集合。
     *
     * @param values 保存資料。
     * @return 經定義驗證的投影集合。
     */
    fun decode(values: List<TypedPersistenceDto>): ComponentViewSet {
        require(values.map { it.typeKey }.distinct().size == values.size) { "Duplicate saved component view ID" }
        return values.fold(ComponentViewSet.EMPTY) { result, dto ->
            val entry = entries[dto.typeKey] ?: throw UnregisteredPersistenceTypeException(dto.typeKey)
            entry.decode(result, dto)
        }
    }
}
