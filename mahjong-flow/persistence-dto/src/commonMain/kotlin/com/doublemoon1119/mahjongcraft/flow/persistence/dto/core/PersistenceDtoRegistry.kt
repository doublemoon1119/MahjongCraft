package com.doublemoon1119.mahjongcraft.flow.persistence.dto.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass

/**
 * 開放領域介面與 persistence DTO 之間的 type key 註冊表。
 *
 * @param Domain 可由內建或第三方規則模組提供具體實作的領域介面。
 */
class PersistenceDtoRegistry<Domain : Any> {
    /** 保存單一具體領域型別與 persistence DTO 的雙向轉換。 */
    private inner class Entry<D : Domain, T : Any>(
        val serializer: KSerializer<T>,
        val toDto: (D) -> T,
        val toDomain: (T) -> D,
    ) {
        /** 將領域物件轉換成 JSON object payload。 */
        fun encode(domain: D, json: Json): JsonObject = json.encodeToJsonElement(serializer, toDto(domain)) as? JsonObject
            ?: error("Persistence DTO must encode to a JSON object")

        /** 將 JSON object payload 還原成領域物件。 */
        fun decode(payload: JsonObject, json: Json): D = toDomain(json.decodeFromJsonElement(serializer, payload))
    }

    /** 以領域具體類別索引的註冊項目。 */
    private val byDomainClass = mutableMapOf<KClass<out Domain>, Pair<String, Entry<*, *>>>()

    /** 以穩定 type key 索引的註冊項目。 */
    private val byTypeKey = mutableMapOf<String, Entry<*, *>>()

    /**
     * 註冊一組具體領域型別與 persistence DTO 的雙向轉換。
     *
     * @throws IllegalArgumentException 若 [typeKey] 為空白，或領域類別／type key 已被註冊。
     */
    fun <D : Domain, T : Any> register(
        typeKey: String,
        domainClass: KClass<D>,
        serializer: KSerializer<T>,
        toDto: (D) -> T,
        toDomain: (T) -> D,
    ) {
        require(typeKey.isNotBlank()) { "Persistence type key must not be blank" }
        require(domainClass !in byDomainClass) { "Persistence DTO already registered for $domainClass" }
        require(typeKey !in byTypeKey) { "Persistence type key already registered: $typeKey" }

        val entry = Entry(serializer, toDto, toDomain)
        byDomainClass[domainClass] = typeKey to entry
        byTypeKey[typeKey] = entry
    }

    /** 將已註冊的領域物件轉換成帶 type key 的 persistence DTO。 */
    @Suppress("UNCHECKED_CAST")
    fun encode(domain: Domain, json: Json = Json): TypedPersistenceDto {
        val (typeKey, rawEntry) = byDomainClass[domain::class]
            ?: error("No persistence DTO registered for ${domain::class}")
        val entry = rawEntry as Entry<Domain, Any>
        return TypedPersistenceDto(typeKey, entry.encode(domain, json))
    }

    /** 依 type key 將 persistence DTO 還原成領域物件。 */
    @Suppress("UNCHECKED_CAST")
    fun decode(dto: TypedPersistenceDto, json: Json = Json): Domain {
        val entry = byTypeKey[dto.typeKey] as? Entry<Domain, Any>
            ?: error("No persistence DTO registered for type key ${dto.typeKey}")
        return entry.decode(dto.payload, json)
    }
}
