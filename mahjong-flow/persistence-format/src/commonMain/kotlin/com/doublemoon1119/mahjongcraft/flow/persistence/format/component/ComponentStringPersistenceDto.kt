package com.doublemoon1119.mahjongcraft.flow.persistence.format.component

import kotlinx.serialization.Serializable

/**
 * 組件的字串值保存格式。
 *
 * @property value 明確保存的值，不使用預設值補寫遺漏欄位。
 */
@Serializable
data class ComponentStringPersistenceDto(val value: String)
