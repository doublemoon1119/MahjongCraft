package com.doublemoon1119.mahjongcraft.flow.persistence.format.component

import kotlinx.serialization.Serializable

/**
 * 整數組件的保存資料，值域由正式組件定義驗證。
 *
 * @property value 完整整數值。
 */
@Serializable
data class ComponentIntPersistenceDto(val value: Int)
