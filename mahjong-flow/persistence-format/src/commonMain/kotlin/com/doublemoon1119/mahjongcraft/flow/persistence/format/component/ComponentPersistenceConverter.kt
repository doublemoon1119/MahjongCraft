package com.doublemoon1119.mahjongcraft.flow.persistence.format.component

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet

/** 權威組件與保存格式之間的唯讀轉換。 */
interface ComponentPersistenceConverter {
    /**
     * 將所有組件依已登記定義編碼。
     *
     * @param components 權威不可變組件。
     * @return 保存資料清單。
     */
    fun encode(components: ComponentSet): List<TypedPersistenceDto>

    /**
     * 拒絕重複或未知 ID，還原為使用已登記定義的組件集合。
     *
     * @param values 保存資料。
     * @return 經定義驗證的組件集合。
     */
    fun decode(values: List<TypedPersistenceDto>): ComponentSet
}
