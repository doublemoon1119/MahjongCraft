package com.doublemoon1119.mahjongcraft.flow.network.dto.component

import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewType
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/**
 * 對外組件值的網路轉換，不接受權威組件集合。
 *
 * @param V 已裁切值型別。
 * @param D 網路 DTO 型別。
 * @property viewType 輸出定義，也是轉換的身分。
 * @property dtoClass 具體網路型別。
 * @property serializer 網路序列化器。
 * @property toDto 輸出值轉網路資料。
 * @property toValue 網路資料還原為輸出值，不是權威值。
 */
class ComponentViewNetworkDefinition<V : Any, D : ComponentViewDto>(
    val viewType: ComponentViewType<V>,
    val dtoClass: KClass<D>,
    val serializer: KSerializer<D>,
    val toDto: (V) -> D,
    val toValue: (D) -> V,
)
