package com.doublemoon1119.mahjongcraft.flow.network.dto.registry

import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/**
 * 保存領域與網路 DTO 型別的雙向轉換，以及該 DTO 的序列化器。
 *
 * 轉換函式在 MahjongCraft 實際轉換時才收到 [NetworkDtoConverter]，用來轉換巢狀的可擴充型別；
 * 不需要巢狀轉換時，可以用只接收值的建構子。
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
    val toDto: (Domain, NetworkDtoConverter) -> Dto,
    val toDomain: (Dto, NetworkDtoConverter) -> Domain,
) {
    /**
     * 建立不需要巢狀轉換的定義。
     *
     * @param domainClass 領域具體型別。
     * @param dtoClass DTO 具體型別。
     * @param serializer 此 DTO 型別的序列化器。
     * @param toDto 將領域物件轉成網路 DTO 的函式。
     * @param toDomain 將網路 DTO 還原成領域物件的函式。
     */
    constructor(
        domainClass: KClass<Domain>,
        dtoClass: KClass<Dto>,
        serializer: KSerializer<Dto>,
        toDto: (Domain) -> Dto,
        toDomain: (Dto) -> Domain,
    ) : this(
        domainClass = domainClass,
        dtoClass = dtoClass,
        serializer = serializer,
        toDto = { domain, _ -> toDto(domain) },
        toDomain = { dto, _ -> toDomain(dto) },
    )
}
