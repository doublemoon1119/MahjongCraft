package com.doublemoon1119.mahjongcraft.flow.persistence.format.core

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
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

    /** 目前已登記 persistence mapper 的穩定 type key 快照。 */
    val registrationKeys: Set<String> get() = byTypeKey.keys.toSet()

    /** 登記時宣告可在精簡牌譜中壓縮的 type key。 */
    private val compactTypeKeys = mutableSetOf<String>()

    /** 登記時宣告可在精簡牌譜中壓縮的 type key 快照，見 [register] 的 `compactInReplay`。 */
    val replayCompactTypeKeys: Set<String> get() = compactTypeKeys.toSet()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /**
     * 註冊一組具體領域型別與 persistence DTO 的雙向轉換。
     *
     * @param typeKey 穩定的 namespaced type key。
     * @param domainClass 具體領域類別。
     * @param serializer persistence DTO 的序列化器。
     * @param toDto 領域物件轉成 persistence DTO。
     * @param toDomain persistence DTO 還原成領域物件。
     * @param compactInReplay 為 `true` 時，精簡牌譜會把這份資料內的牌與玩家 UUID 換成局內索引，並壓縮其中的欄位名稱；
     * 只有資料內的 UUID 都是本局的牌或玩家、且讀取牌譜時能處理局內索引的格式才可設為 `true`。預設保留原始內容。
     * @throws IllegalArgumentException 若 [typeKey] 不是 namespaced ID，或領域類別／type key 已被註冊。
     */
    fun <D : Domain, T : Any> register(
        typeKey: String,
        domainClass: KClass<D>,
        serializer: KSerializer<T>,
        toDto: (D) -> T,
        toDomain: (T) -> D,
        compactInReplay: Boolean = false,
    ) {
        check(!frozen) { "Persistence DTO registry is frozen" }
        NamespacedId.requireValid(typeKey) { "Persistence type key must be a namespaced ID: $typeKey" }
        require(domainClass !in byDomainClass) { "Persistence DTO already registered for $domainClass" }
        require(typeKey !in byTypeKey) { "Persistence type key already registered: $typeKey" }

        val entry = Entry(serializer, toDto, toDomain)
        byDomainClass[domainClass] = typeKey to entry
        byTypeKey[typeKey] = entry
        if (compactInReplay) compactTypeKeys += typeKey
    }

    /** 凍結註冊表；凍結後不得新增 persistence mapper。 */
    fun freeze() {
        frozen = true
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
