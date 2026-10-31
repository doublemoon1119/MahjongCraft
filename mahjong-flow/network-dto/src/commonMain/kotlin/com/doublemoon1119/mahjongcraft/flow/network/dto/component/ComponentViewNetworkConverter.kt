package com.doublemoon1119.mahjongcraft.flow.network.dto.component

import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet

/** 組件對外值與網路格式之間的唯讀轉換。 */
interface ComponentViewNetworkConverter {
    /**
     * 只編碼本次實際可交付的項目。
     *
     * @param views 已裁切的輸出。
     * @return 可由網路格式序列化的資料。
     */
    fun encode(views: ComponentViewSet): List<ComponentViewDto>

    /**
     * 還原輸出值，不建立權威組件；重複項目會拒絕。
     *
     * @param dtos 網路資料。
     * @return 已裁切的組件輸出。
     */
    fun decode(dtos: List<ComponentViewDto>): ComponentViewSet
}
