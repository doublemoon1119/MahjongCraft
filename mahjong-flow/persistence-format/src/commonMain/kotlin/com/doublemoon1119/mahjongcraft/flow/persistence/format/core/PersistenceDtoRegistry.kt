package com.doublemoon1119.mahjongcraft.flow.persistence.format.core

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass

/**
 * 開放領域介面與 persistence DTO 之間的 type key 註冊表。
 *
 * @param Domain 可由內建或第三方規則模組提供具體實作的領域介面。
 */
class PersistenceDtoRegistry<Domain : Any> {
    /**
     * 保存單一具體領域型別與存檔 DTO 的雙向轉換。
     *
     * @param D 此項目接受的領域型別。
     * @param T 對應的存檔 DTO 型別。
     * @property serializer DTO 的序列化器。
     * @property toDto 將領域物件轉成 DTO 的函式。
     * @property toDomain 將 DTO 還原成領域物件的函式。
     */
    private inner class Entry<D : Domain, T : Any>(
        val serializer: KSerializer<T>,
        val toDto: (D) -> T,
        val toDomain: (T) -> D,
    ) {
        /**
         * 將領域物件轉成 JSON 物件。
         *
         * @param domain 要保存的領域物件。
         * @return 編碼後的 DTO 內容。
         * @throws IllegalStateException DTO 編碼結果不是 JSON 物件時拋出。
         */
        fun encode(domain: D): JsonObject = persistenceJson.encodeToJsonElement(serializer, toDto(domain)) as? JsonObject
            ?: error("Persistence DTO must encode to a JSON object")

        /**
         * 將 JSON 物件還原成領域物件。
         *
         * @param payload 已保存的 DTO 內容。
         * @return 還原後的領域物件。
         */
        fun decode(payload: JsonObject): D = toDomain(persistenceJson.decodeFromJsonElement(serializer, payload))
    }

    /** 以領域具體類別索引的註冊項目。 */
    private val byDomainClass = mutableMapOf<KClass<out Domain>, Pair<String, Entry<*, *>>>()

    /** 以穩定 type key 索引的註冊項目。 */
    private val byTypeKey = mutableMapOf<String, Entry<*, *>>()

    /** 目前已登記 persistence mapper 的穩定 type key 快照。 */
    val registrationKeys: Set<String> get() = byTypeKey.keys.toSet()

    /** 登記時宣告可在精簡牌譜中壓縮的 type key。 */
    private val compactTypeKeys = mutableSetOf<String>()

    /** 可在精簡牌譜中壓縮的 type key 快照，依 [PersistenceDtoDefinition.compactInReplay] 的宣告建立。 */
    val replayCompactTypeKeys: Set<String> get() = compactTypeKeys.toSet()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /**
     * 註冊一組具體領域型別與 persistence DTO 的雙向轉換。
     *
     * @param D 要登記的具體領域型別。
     * @param T 對應的存檔 DTO 型別。
     * @param definition 穩定 type key、領域型別、serializer 與雙向 mapper 的完整定義。
     * @throws IllegalArgumentException 若 definition 的 type key 不是 namespaced ID，或領域類別／type key 已被註冊。
     * @throws IllegalStateException 註冊表已凍結時拋出。
     */
    fun <D : Domain, T : Any> register(definition: PersistenceDtoDefinition<D, T>) {
        check(!frozen) { "Persistence DTO registry is frozen" }
        NamespacedId.requireValid(definition.typeKey) { "Persistence type key must be a namespaced ID: ${definition.typeKey}" }
        require(definition.domainClass !in byDomainClass) { "Persistence DTO already registered for ${definition.domainClass}" }
        require(definition.typeKey !in byTypeKey) { "Persistence type key already registered: ${definition.typeKey}" }

        val entry = Entry(definition.serializer, definition.toDto, definition.toDomain)
        byDomainClass[definition.domainClass] = definition.typeKey to entry
        byTypeKey[definition.typeKey] = entry
        if (definition.compactInReplay) compactTypeKeys += definition.typeKey
    }

    /** 凍結註冊表；凍結後不得新增 persistence mapper。 */
    fun freeze() {
        frozen = true
    }

    /**
     * 將已註冊的領域物件轉成附有型別識別碼的存檔 DTO。
     *
     * @param domain 要保存的領域物件。
     * @return 型別識別碼與編碼後的內容。
     * @throws IllegalStateException 領域型別尚未註冊，或 DTO 編碼結果不是 JSON 物件時拋出。
     */
    @Suppress("UNCHECKED_CAST")
    fun encode(domain: Domain): TypedPersistenceDto {
        val (typeKey, rawEntry) = byDomainClass[domain::class]
            ?: error("No persistence DTO registered for ${domain::class}")
        val entry = rawEntry as Entry<Domain, Any>
        return TypedPersistenceDto(typeKey, entry.encode(domain))
    }

    /**
     * 依型別識別碼將存檔 DTO 還原成領域物件。
     *
     * @param dto 型別識別碼與已保存的內容。
     * @return 還原後的領域物件。
     * @throws IllegalStateException 型別識別碼尚未註冊時拋出。
     */
    @Suppress("UNCHECKED_CAST")
    fun decode(dto: TypedPersistenceDto): Domain {
        val entry = byTypeKey[dto.typeKey] as? Entry<Domain, Any>
            ?: error("No persistence DTO registered for type key ${dto.typeKey}")
        return entry.decode(dto.payload)
    }
}
