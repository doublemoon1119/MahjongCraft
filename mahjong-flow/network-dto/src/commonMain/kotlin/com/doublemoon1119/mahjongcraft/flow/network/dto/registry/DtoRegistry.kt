package com.doublemoon1119.mahjongcraft.flow.network.dto.registry

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlin.reflect.KClass

/**
 * 領域層開放介面（例如 [com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig]）與其對應
 * DTO 之間的通用註冊表，比照
 * [com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl] 的既有精神：建構時是空
 * 的對照表，日麻/台麻透過 `registerBuiltInRuleConfigDtos()` 呼叫 [register] 註冊進來，第三方規則
 * 模組要支援序列化的話走同一套流程，這個類別本身不知道、也不在乎誰註冊了什麼。
 *
 * 領域層的這幾組型別本來就刻意設計成開放介面（讓第三方能註冊自己的規則），DTO 層如果寫死成
 * `sealed interface` 會讓第三方規則完全無法被序列化，因此這裡改用執行期註冊表 +
 * [registerSubclasses] 動態組成 `SerializersModule` 的多型清單，而不是編譯期窮舉。
 *
 * @param Domain 領域層的開放介面型別。
 * @param Dto 對應的 DTO 開放介面型別。
 */
class DtoRegistry<Domain : Any, Dto : Any> {

    /**
     * 用 inner class（而非一般 nested class）宣告，讓 [D]/[T] 能直接綁定外層的 [Domain]/[Dto]
     * 型別上界——這樣 [registerSubclass] 呼叫 kotlinx 的 `subclass(KClass<T>, KSerializer<T>)`
     * 時，[T] 是同一個具體型別參數，不會因為之後從 `Map<*, Entry<*, *>>` 取出時被拆成兩個各自獨立、
     * 編譯器無法證明相關的萬用字元擷取型別。
     */
    private inner class Entry<D : Domain, T : Dto>(
        val dtoClass: KClass<T>,
        val serializer: KSerializer<T>,
        val toDto: (D) -> T,
        val toDomain: (T) -> D,
    ) {
        fun registerSubclass(builder: PolymorphicModuleBuilder<Dto>) {
            builder.subclass(dtoClass, serializer)
        }
    }

    private val byDomainClass = mutableMapOf<KClass<out Domain>, Entry<*, *>>()
    private val byDtoClass = mutableMapOf<KClass<out Dto>, Entry<*, *>>()

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
        val entry = Entry(dtoClass, serializer, toDto, toDomain)
        byDomainClass[domainClass] = entry
        byDtoClass[dtoClass] = entry
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
     * 把目前已註冊的每個具體 DTO 類別與其序列化器登記進 `SerializersModule` 的
     * `polymorphic { }` 區塊。
     */
    fun registerSubclasses(builder: PolymorphicModuleBuilder<Dto>) {
        byDtoClass.values.forEach { it.registerSubclass(builder) }
    }
}
