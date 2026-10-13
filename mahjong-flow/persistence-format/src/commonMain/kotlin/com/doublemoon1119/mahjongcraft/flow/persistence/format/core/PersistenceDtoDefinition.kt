package com.doublemoon1119.mahjongcraft.flow.persistence.format.core

import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/**
 * 保存持久化種類 ID、領域型別、雙向轉換與序列化器；不改變原存檔格式。
 *
 * @param Domain 領域具體型別。
 * @param T persistence DTO 具體型別。
 * @property typeKey 穩定且 namespaced 的 type key。
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
    val toDto: (Domain) -> T,
    val toDomain: (T) -> Domain,
    val compactInReplay: Boolean = false,
)
