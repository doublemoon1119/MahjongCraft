package com.doublemoon1119.mahjongcraft.flow.network.dto.registry

/**
 * 一類領域型別與網路 DTO 之間的唯讀轉換。
 *
 * @param Domain 領域型別。
 * @param Dto 網路 DTO 型別。
 */
interface DtoConverter<Domain : Any, Dto : Any> {
    /**
     * 將領域物件轉成已登記的網路 DTO。
     *
     * @param domain 要轉換的領域物件。
     * @return 對應的網路 DTO。
     */
    fun toDto(domain: Domain): Dto

    /**
     * 將網路 DTO 還原成已登記的領域物件。
     *
     * @param dto 要還原的網路 DTO。
     * @return 對應的領域物件。
     */
    fun toDomain(dto: Dto): Domain
}
