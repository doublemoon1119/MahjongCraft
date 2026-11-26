package com.doublemoon1119.mahjongcraft.flow.persistence.format.component

import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import kotlinx.serialization.KSerializer

/**
 * 一種組件的保存轉換，使用組件本身的完整 ID，不另外登記另一個身分。
 *
 * @param V 組件值型別。
 * @param D 保存 DTO 型別。
 * @property component 組件的完整型別定義。
 * @property serializer 保存 DTO 序列化器。
 * @property toDto 領域值轉保存值，不修改原值。
 * @property toDomain 保存值還原為領域值。
 */
class ComponentPersistenceDefinition<V : Any, D : Any>(
    val component: ComponentType<V>,
    val serializer: KSerializer<D>,
    val toDto: (V) -> D,
    val toDomain: (D) -> V,
)
