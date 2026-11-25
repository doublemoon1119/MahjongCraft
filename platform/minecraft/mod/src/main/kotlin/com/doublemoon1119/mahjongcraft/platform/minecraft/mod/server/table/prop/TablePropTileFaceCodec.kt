package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.prop

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentViewPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TileFaceViewPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import kotlinx.serialization.json.Json

/**
 * 公開展示牌衍生實體保存牌面的固定格式，沿用存檔的牌面格式；解碼失敗由重新呈現流程清除並重建。
 *
 * @property views 已登記的組件公開效果保存轉換。
 */
internal class TablePropTileFaceCodec(private val views: ComponentViewPersistenceRegistry) {
    /**
     * 保存公開展示牌的牌面與組件公開效果，不包含正式牌張 UUID。
     *
     * @param face 要保存的公開牌面。
     * @return 固定格式的牌面資料。
     */
    fun encode(face: TileFaceView): String = Json.encodeToString(TileFaceViewPersistenceDto.serializer(), face.toPersistenceDto(views))

    /**
     * 還原衍生實體的公開牌面。
     *
     * @param encoded 已保存的牌面資料。
     * @return 合法牌面；資料無法辨識或含有未登記的組件效果時為 null。
     */
    fun decode(encoded: String): TileFaceView? = try {
        Json.decodeFromString(TileFaceViewPersistenceDto.serializer(), encoded).toDomain(views)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }
}
