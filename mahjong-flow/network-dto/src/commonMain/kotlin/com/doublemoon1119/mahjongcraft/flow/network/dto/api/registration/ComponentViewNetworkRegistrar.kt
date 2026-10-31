package com.doublemoon1119.mahjongcraft.flow.network.dto.api.registration

import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewNetworkDefinition

/** 擴充登記組件對外值的網路格式時取得的登記介面；編碼、解碼與凍結由 MahjongCraft 進行。 */
interface ComponentViewNetworkRegistrar {
    /** 網路序列化名稱的獨立快照。 */
    val registrationKeys: Set<String>

    /**
     * 登記具型別的雙向轉換，任何一種身分重複都會拒絕。
     *
     * @param V 輸出值型別。
     * @param D 網路型別。
     * @param definition 轉換定義。
     */
    fun <V : Any, D : ComponentViewDto> register(definition: ComponentViewNetworkDefinition<V, D>)
}
