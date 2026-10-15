package com.doublemoon1119.mahjongcraft.flow.persistence.format.core

/**
 * 存檔中的型別識別碼沒有任何已登記的轉換器時拋出，例如提供該型別的擴充已被移除。
 *
 * @property typeKey 存檔中找不到轉換器的型別識別碼。
 */
class UnregisteredPersistenceTypeException(val typeKey: String) : IllegalStateException("No persistence DTO registered for type key $typeKey")
