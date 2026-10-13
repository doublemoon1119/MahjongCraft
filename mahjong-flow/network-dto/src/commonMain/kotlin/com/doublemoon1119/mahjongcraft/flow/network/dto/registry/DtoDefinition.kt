package com.doublemoon1119.mahjongcraft.flow.network.dto.registry

import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/**
 * 保存領域與網路 DTO 型別的雙向轉換，以及該 DTO 的序列化器。
 *
 * @param Domain 領域具體型別。
 * @param Dto DTO 具體型別。
 * @property domainClass 領域具體型別。
 * @property dtoClass DTO 具體型別。
 * @property serializer 此 DTO 型別的序列化器。
 * @property toDto 將領域物件轉成網路 DTO 的函式。
 * @property toDomain 將網路 DTO 還原成領域物件的函式。
 */
data class DtoDefinition<Domain : Any, Dto : Any>(
    val domainClass: KClass<Domain>,
    val dtoClass: KClass<Dto>,
    val serializer: KSerializer<Dto>,
    val toDto: (Domain) -> Dto,
    val toDomain: (Dto) -> Domain,
)
