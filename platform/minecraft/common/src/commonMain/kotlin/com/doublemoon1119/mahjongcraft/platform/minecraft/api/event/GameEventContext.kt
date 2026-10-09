package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 事件交付時提供的版本中立情境。
 *
 * 真人玩家的玩家 UUID 與其 Minecraft 帳號 UUID 相同。桌子位置是在事件產生時保存；找不到位置時為 `null`，
 * 需要操作實體桌子的訂閱者必須先檢查位置是否存在。事件情境不提供 Minecraft 原生物件。
 *
 * @property tableLocation 事件產生時的桌子位置；位置索引查不到時為 `null`。
 * @property sessionId 產生事件的伺服器 session 識別碼；僅供平台內部驗證事件歸屬。
 */
class GameEventContext private constructor(
    val tableLocation: TableLocation?,
    @get:JvmSynthetic internal val sessionId: Uuid,
) {
    /** MahjongCraft 內部建立事件情境的入口。 */
    internal companion object {
        /**
         * 建立事件情境；位置與 session 識別碼會隨事件一併保存。
         *
         * @param tableLocation 事件產生時的桌子位置；找不到時為 `null`。
         * @param sessionId 產生事件的伺服器 session 識別碼。
         */
        @JvmSynthetic
        fun create(
            tableLocation: TableLocation?,
            sessionId: Uuid,
        ): GameEventContext = GameEventContext(tableLocation, sessionId)
    }
}
