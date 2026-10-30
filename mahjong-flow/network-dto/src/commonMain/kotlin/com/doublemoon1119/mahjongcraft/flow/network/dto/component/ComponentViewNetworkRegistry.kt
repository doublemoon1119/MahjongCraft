package com.doublemoon1119.mahjongcraft.flow.network.dto.component

import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewType
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/** 登記已裁切組件的網路格式，不保存權威組件的轉換。 */
class ComponentViewNetworkRegistry {
    /** 依輸出 ID 查找轉換。 */
    private val byViewId = linkedMapOf<String, Entry<*, *>>()

    /** 依網路型別查找轉換。 */
    private val byDtoClass = linkedMapOf<KClass<out ComponentViewDto>, Entry<*, *>>()

    /** 依序列化名稱查找轉換。 */
    private val bySerialName = linkedMapOf<String, Entry<*, *>>()

    /** 是否禁止新增轉換。 */
    private var frozen = false

    /** 網路序列化名稱的獨立快照。 */
    val registrationKeys: Set<String> get() = bySerialName.keys.toSet()

    /** 已登記的輸出定義，供啟動時核對同一份定義。 */
    val viewTypes: List<ComponentViewType<*>> get() = byViewId.values.map { it.definition.viewType }

    /**
     * 登記具型別的雙向轉換，任何一種身分重複都會拒絕。
     *
     * @param V 輸出值型別。
     * @param D 網路型別。
     * @param definition 轉換定義。
     */
    fun <V : Any, D : ComponentViewDto> register(definition: ComponentViewNetworkDefinition<V, D>) {
        check(!frozen) { "Component view network registry is frozen" }
        val serialName = definition.serializer.descriptor.serialName
        require(definition.viewType.id !in byViewId) { "Duplicate component view network ID: ${definition.viewType.id}" }
        require(definition.dtoClass !in byDtoClass) { "Duplicate component view DTO class" }
        require(serialName !in bySerialName) { "Duplicate component view serial name: $serialName" }
        val entry = Entry(definition)
        byViewId[definition.viewType.id] = entry
        byDtoClass[definition.dtoClass] = entry
        bySerialName[serialName] = entry
    }

    /** 禁止後續新增或取代轉換。 */
    fun freeze() {
        frozen = true
    }

    /**
     * 只編碼本次實際可交付的項目。
     *
     * @param views 已裁切的輸出。
     * @return 可由網路格式序列化的資料。
     */
    fun encode(views: ComponentViewSet): List<ComponentViewDto> = views.types.map { type ->
        val entry = byViewId[type.id] ?: error("No network DTO for component view ${type.id}")
        entry.encode(views)
    }

    /**
     * 還原輸出值，不建立權威組件；重複項目會拒絕。
     *
     * @param dtos 網路資料。
     * @return 已裁切的組件輸出。
     */
    fun decode(dtos: List<ComponentViewDto>): ComponentViewSet {
        val seen = mutableSetOf<String>()
        return dtos.fold(ComponentViewSet.EMPTY) { result, dto ->
            val entry = byDtoClass[dto::class] ?: error("No component view mapping for ${dto::class}")
            require(seen.add(entry.definition.viewType.id)) { "Duplicate component view in network payload" }
            entry.decode(result, dto)
        }
    }

    /**
     * 取得目前 DTO 的動態序列化器。
     *
     * @param dto 網路值。
     * @return 已登記序列化器；沒有時為 null。
     */
    @Suppress("UNCHECKED_CAST")
    fun serializerFor(dto: ComponentViewDto): KSerializer<ComponentViewDto>? = byDtoClass[dto::class]?.definition?.serializer as? KSerializer<ComponentViewDto>

    /**
     * 依網路序列化名稱取得轉換。
     *
     * @param serialName 序列化名稱。
     * @return 已登記序列化器；沒有時為 null。
     */
    fun serializerFor(serialName: String): KSerializer<out ComponentViewDto>? = bySerialName[serialName]?.definition?.serializer

    /**
     * 保留具體值及 DTO 型別的關係。
     *
     * @param V 輸出值型別。
     * @param D 網路型別。
     * @property definition 具型別的轉換。
     */
    private class Entry<V : Any, D : ComponentViewDto>(val definition: ComponentViewNetworkDefinition<V, D>) {
        /**
         * 讀取與原定義相符的輸出值後轉換。
         *
         * @param views 已裁切集合。
         * @return 網路資料。
         */
        fun encode(views: ComponentViewSet): ComponentViewDto = definition.toDto(checkNotNull(views[definition.viewType]))

        /**
         * 核對實際型別後還原獨立輸出。
         *
         * @param result 已還原項目。
         * @param dto 網路資料。
         * @return 含本項結果的新集合。
         */
        @Suppress("UNCHECKED_CAST")
        fun decode(result: ComponentViewSet, dto: ComponentViewDto): ComponentViewSet {
            require(definition.dtoClass.isInstance(dto)) { "Invalid component view DTO type" }
            return result.with(definition.viewType, definition.toValue(dto as D))
        }
    }
}
