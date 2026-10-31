package com.doublemoon1119.mahjongcraft.flow.persistence.format.core

/**
 * 一類領域型別與保存格式之間的唯讀轉換。
 *
 * @param Domain 領域型別。
 */
interface PersistenceDtoConverter<Domain : Any> {
    /**
     * 依領域物件的具體類別找出已登記的 mapper 並編碼。
     *
     * @param domain 要保存的領域物件。
     * @return 型別識別碼與編碼後的內容。
     */
    fun encode(domain: Domain): TypedPersistenceDto

    /**
     * 依型別識別碼將存檔 DTO 還原成領域物件。
     *
     * @param dto 型別識別碼與已保存的內容。
     * @return 還原後的領域物件。
     */
    fun decode(dto: TypedPersistenceDto): Domain
}
