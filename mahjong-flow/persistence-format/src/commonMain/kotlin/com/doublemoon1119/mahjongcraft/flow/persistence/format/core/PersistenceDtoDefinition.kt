package com.doublemoon1119.mahjongcraft.flow.persistence.format.core

import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/**
 * 保存持久化種類 ID、領域型別、雙向轉換與序列化器；不改變原存檔格式。
 *
 * 轉換函式在 MahjongCraft 實際轉換時才收到 [PersistenceConverter]，用來轉換巢狀的可擴充型別；
 * 不需要巢狀轉換時，可以用只接收值的建構子。
 *
 * @param Domain 領域具體型別。
 * @param T persistence DTO 具體型別。
 * @property typeKey 穩定且 namespaced 的 type key；擴充遊戲動作必須使用動作本身的 ID。
 * @property domainClass 領域具體型別。
 * @property serializer 此存檔 DTO 型別的序列化器。
 * @property toDto 將領域物件轉成存檔 DTO 的函式。
 * @property toDomain 將存檔 DTO 還原成領域物件的函式。
 * @property compactInReplay 是否允許精簡牌譜將本局牌與玩家 UUID 換成索引，並縮短欄位名稱；
 * 只有資料內的 UUID 都屬於本局、且讀取端能處理這些索引時才可啟用，否則保留原內容。
 */
data class PersistenceDtoDefinition<Domain : Any, T : Any>(
    val typeKey: String,
    val domainClass: KClass<Domain>,
    val serializer: KSerializer<T>,
    val toDto: (Domain, PersistenceConverter) -> T,
    val toDomain: (T, PersistenceConverter) -> Domain,
    val compactInReplay: Boolean = false,
) {
    /**
     * 建立不需要巢狀轉換的定義。
     *
     * @param typeKey 穩定且 namespaced 的 type key。
     * @param domainClass 領域具體型別。
     * @param serializer 此存檔 DTO 型別的序列化器。
     * @param toDto 將領域物件轉成存檔 DTO 的函式。
     * @param toDomain 將存檔 DTO 還原成領域物件的函式。
     * @param compactInReplay 是否允許精簡牌譜壓縮本局牌與玩家 UUID。
     */
    constructor(
        typeKey: String,
        domainClass: KClass<Domain>,
        serializer: KSerializer<T>,
        toDto: (Domain) -> T,
        toDomain: (T) -> Domain,
        compactInReplay: Boolean = false,
    ) : this(
        typeKey = typeKey,
        domainClass = domainClass,
        serializer = serializer,
        toDto = { domain, _ -> toDto(domain) },
        toDomain = { dto, _ -> toDomain(dto) },
        compactInReplay = compactInReplay,
    )
}
