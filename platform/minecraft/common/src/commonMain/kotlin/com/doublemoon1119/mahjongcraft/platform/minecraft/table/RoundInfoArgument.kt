package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/** 局況顯示行可攜帶的規則中立參數。 */
sealed interface RoundInfoArgument {
    /**
     * 供翻譯文字直接代入的整數。
     *
     * @property value 要代入翻譯文字的整數。
     */
    data class Number(val value: Int) : RoundInfoArgument

    /**
     * 需要由呈現層轉成玩家語言的風位。
     *
     * @property value 要本地化顯示的風位。
     */
    data class WindValue(val value: Wind) : RoundInfoArgument

    /**
     * 需要依牌種名稱與可用的牌面 emoji 顯示的牌。
     *
     * @property tile 要顯示的牌面。
     */
    data class TileValue(val tile: Tile) : RoundInfoArgument

    /**
     * 由局況訊息 registry 對應到翻譯文字的 namespaced ID。
     *
     * @property id 訊息的完整識別碼。
     * @throws IllegalArgumentException [id] 不符合 `namespace:path` 格式時。
     */
    data class Message(val id: String) : RoundInfoArgument {
        init {
            NamespacedId.requireValid(id) { "Round info message ID must be namespaced: $id" }
        }
    }

    /** 僅由解碼器在無法辨識原始參數時產生的佔位值；規則不應自行建立此值。 */
    data object Unrecognized : RoundInfoArgument
}
