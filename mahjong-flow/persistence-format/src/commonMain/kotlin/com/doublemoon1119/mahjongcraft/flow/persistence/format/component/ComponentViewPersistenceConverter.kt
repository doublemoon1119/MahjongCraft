package com.doublemoon1119.mahjongcraft.flow.persistence.format.component

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet

/** 已裁切組件投影與保存格式之間的唯讀轉換。 */
interface ComponentViewPersistenceConverter {
    /**
     * 將所有投影依已登記定義編碼。
     *
     * @param views 已裁切的不可變投影。
     * @return 保存資料清單。
     */
    fun encode(views: ComponentViewSet): List<TypedPersistenceDto>

    /**
     * 拒絕重複或未知 ID，還原為使用已登記定義的投影集合。
     *
     * @param values 保存資料。
     * @return 經定義驗證的投影集合。
     */
    fun decode(values: List<TypedPersistenceDto>): ComponentViewSet
}
