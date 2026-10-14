package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry
import kotlin.uuid.Uuid

/**
 * 桌級工作建立時捕捉的來源身分，不保存至世界或傳送給客戶端。
 *
 * @property tableId 工作對應的桌子。
 * @property sessionId 建立工作時的伺服器啟動身分。
 * @property entry 建立工作時的位置條目；使用物件身分比較，不依賴可能重用的 revision。
 * @property requestId 這份票據的唯一識別碼，避免同桌舊工作套用後來的移除請求。
 */
class FabricTableStateTicket(
    val tableId: Uuid,
    val sessionId: Uuid,
    val entry: TableLocationEntry,
    val requestId: Uuid = Uuid.random(),
)
