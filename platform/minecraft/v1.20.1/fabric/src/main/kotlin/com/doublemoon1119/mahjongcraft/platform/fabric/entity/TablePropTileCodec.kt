package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TilePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlinx.serialization.json.Json

/** 公開展示牌衍生實體使用的固定保存格式；解碼失敗由重新呈現流程清除並重建。 */
internal object TablePropTileCodec {
    /** 實體保存格式獨立於網路格式及使用者設定。 */
    private val json = Json

    /**
     * 保存公開展示牌的牌種，不包含正式牌張 UUID。
     *
     * @param tile 要保存的公開牌種。
     * @return 固定格式的牌種資料。
     */
    fun encode(tile: Tile): String = json.encodeToString(TilePersistenceDto.serializer(), tile.toPersistenceDto())

    /**
     * 還原衍生實體的公開牌種。
     *
     * @param encoded 已保存的牌種資料。
     * @return 合法牌種；資料無法辨識時為 null。
     */
    fun decode(encoded: String): Tile? = try {
        json.decodeFromString(TilePersistenceDto.serializer(), encoded).toDomain()
    } catch (_: IllegalArgumentException) {
        null
    }
}
