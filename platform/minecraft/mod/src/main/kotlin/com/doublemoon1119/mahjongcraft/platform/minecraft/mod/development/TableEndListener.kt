package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.development

import kotlin.uuid.Uuid

/** 一桌的對局結束或牌桌被移除時收到通知，用來清掉只屬於這桌的暫存設定。 */
fun interface TableEndListener {
    /**
     * 這桌的對局已結束，或牌桌已被移除。
     *
     * @param tableId 牌桌識別碼。
     */
    fun onTableEnded(tableId: Uuid)
}
