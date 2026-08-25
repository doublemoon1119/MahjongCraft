package com.doublemoon1119.mahjongcraft.flow.network.dto.registry

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/**
 * 領域層開放介面（例如 [MahjongRuleConfig]）與其對應
 * DTO 之間的通用註冊表，比照 [MahjongModuleRegistryImpl] 的既有精神：建構時是空
 * 的對照表，日麻/台麻透過 `registerBuiltInRuleConfigDtos()` 呼叫 [register] 註冊進來，第三方規則
 * 模組要支援序列化的話走同一套流程，這個類別本身不知道、也不在乎誰註冊了什麼。
 *
 * 領域層的這幾組型別本來就刻意設計成開放介面（讓第三方能註冊自己的規則），DTO 層如果寫死成
 * `sealed interface` 會讓第三方規則完全無法被序列化，因此這裡改用執行期註冊表 +
 * 動態 polymorphic provider 在每次編解碼時查詢 registry，而不是使用編譯期窮舉或在
 * `SerializersModule` 建立時複製一份靜態清單。
 *
 * @param Domain 領域層的開放介面型別。
 * @param Dto 對應的 DTO 開放介面型別。
 */
class DtoRegistry<Domain : Any, Dto : Any> {

    /** 將具體 DTO serializer 與雙向 mapper 綁在同一筆註冊資料。 */
    private inner class Entry<D : Domain, T : Dto>(
        val serializer: KSerializer<T>,
        val toDto: (D) -> T,
        val toDomain: (T) -> D,
    )

    private val byDomainClass = mutableMapOf<KClass<out Domain>, Entry<*, *>>()
    private val byDtoClass = mutableMapOf<KClass<out Dto>, Entry<*, *>>()
    private val bySerialName = mutableMapOf<String, Entry<*, *>>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /**
     * 註冊一組領域型別 ↔ DTO 的對應關係。
     *
     * @param domainClass 領域層的具體實作類別（例如 `RiichiRuleConfig::class`）。
     * @param dtoClass 對應的具體 DTO 類別。
     * @param serializer 該 DTO 類別的序列化器。
     * @param toDto 領域物件轉換成 DTO 的函式。
     * @param toDomain DTO 轉換回領域物件的函式。
     */
    fun <D : Domain, T : Dto> register(
        domainClass: KClass<D>,
        dtoClass: KClass<T>,
        serializer: KSerializer<T>,
        toDto: (D) -> T,
        toDomain: (T) -> D,
    ) {
        check(!frozen) { "Network DTO registry is frozen" }
        require(domainClass !in byDomainClass) { "Network DTO already registered for $domainClass" }
        require(dtoClass !in byDtoClass) { "Network domain mapping already registered for $dtoClass" }
        val serialName = serializer.descriptor.serialName
        require(serialName !in bySerialName) { "Network DTO serial name already registered: $serialName" }
        val entry = Entry(serializer, toDto, toDomain)
        byDomainClass[domainClass] = entry
        byDtoClass[dtoClass] = entry
        bySerialName[serialName] = entry
    }

    /** 凍結註冊表；凍結後不得新增或覆寫 DTO mapper。 */
    fun freeze() {
        frozen = true
    }

    /**
     * 將領域物件轉換成對應的 DTO。
     *
     * @throws IllegalStateException 若 [domain] 的具體類別尚未透過 [register] 註冊過。
     */
    @Suppress("UNCHECKED_CAST")
    fun toDto(domain: Domain): Dto {
        val entry = byDomainClass[domain::class] as? Entry<Domain, Dto>
            ?: error("No DTO registered for ${domain::class}")
        return entry.toDto(domain)
    }

    /**
     * 將 DTO 轉換回對應的領域物件。
     *
     * @throws IllegalStateException 若 [dto] 的具體類別尚未透過 [register] 註冊過。
     */
    @Suppress("UNCHECKED_CAST")
    fun toDomain(dto: Dto): Domain {
        val entry = byDtoClass[dto::class] as? Entry<Domain, Dto>
            ?: error("No domain mapping registered for ${dto::class}")
        return entry.toDomain(dto)
    }

    /**
     * 依執行期 DTO 型別取得 serializer。
     *
     * 這個查詢刻意保留動態性：即使 `Json` 早於 extension bootstrap 建立，之後完成的 DTO
     * 註冊仍可由 polymorphic default provider 看見。
     */
    @Suppress("UNCHECKED_CAST")
    fun serializerFor(dto: Dto): KSerializer<Dto>? = (byDtoClass[dto::class] as? Entry<Domain, Dto>)?.serializer

    /**
     * 依序列化資料中的 discriminator 名稱取得 serializer，供動態 polymorphic 解碼使用。
     */
    @Suppress("UNCHECKED_CAST")
    fun serializerFor(serialName: String): KSerializer<out Dto>? = (bySerialName[serialName] as? Entry<Domain, Dto>)?.serializer
}
